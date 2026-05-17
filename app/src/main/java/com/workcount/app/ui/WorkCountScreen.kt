package com.workcount.app.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workcount.app.AppTab
import com.workcount.app.ExportPayload
import com.workcount.app.WorkCountViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkCountScreen(
    viewModel: WorkCountViewModel,
    isUnlocked: Boolean,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<ExportPayload?>(null) }
    var showCustomRangeDialog by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            viewModel.fillActiveLocationFromGps()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Location permission was not granted.") }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
        writeExportBytes(context, uri, pendingExport, snackbarHostState, scope)
    }
    val pdfLauncher = rememberLauncherForActivityResult(CreateDocument("application/pdf")) { uri ->
        writeExportBytes(context, uri, pendingExport, snackbarHostState, scope)
    }
    val backupLauncher = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        writeExportBytes(context, uri, pendingExport, snackbarHostState, scope)
    }
    val restoreLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    snackbarHostState.showSnackbar("Could not read the backup.")
                } else {
                    viewModel.restoreBackup(bytes)
                }
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (!isUnlocked) {
        LockedScreen()
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.HOME,
                    onClick = { viewModel.setTab(AppTab.HOME) },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.JOURNAL,
                    onClick = { viewModel.setTab(AppTab.JOURNAL) },
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                    label = { Text("Journal") },
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.SETTINGS,
                    onClick = { viewModel.setTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedTab == AppTab.JOURNAL) {
                FloatingActionButton(onClick = viewModel::openNewShiftEditor) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
        },
    ) { padding ->
        when (uiState.selectedTab) {
            AppTab.HOME -> HomeTab(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onStart = viewModel::startShift,
                onPauseResume = viewModel::pauseOrResumeActiveShift,
                onStop = viewModel::stopActiveShift,
                onLocationChange = viewModel::updateActiveLocation,
                onNoteChange = viewModel::updateActiveNote,
                onRateChange = viewModel::updateActiveRate,
                onUseGps = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onUsePreset = viewModel::applyPresetToActive,
            )
            AppTab.JOURNAL -> JournalTab(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onPresetSelected = viewModel::setJournalPreset,
                onDateSelected = viewModel::setJournalAnchorDate,
                onLocationFilterChange = viewModel::setLocationFilter,
                onStatusSelected = viewModel::setStatusFilter,
                onOpenCustomRange = { showCustomRangeDialog = true },
                onEditShift = viewModel::openShiftEditor,
                onDeleteShift = viewModel::deleteShift,
            )
            AppTab.SETTINGS -> SettingsTab(
                modifier = Modifier.padding(padding),
                uiState = uiState,
                onSaveSettings = viewModel::saveSettings,
                onExportCsv = {
                    scope.launch {
                        pendingExport = viewModel.buildCsvExport()
                        csvLauncher.launch(pendingExport!!.fileName)
                    }
                },
                onExportPdf = {
                    scope.launch {
                        pendingExport = viewModel.buildPdfExport()
                        pdfLauncher.launch(pendingExport!!.fileName)
                    }
                },
                onBackup = {
                    scope.launch {
                        pendingExport = viewModel.buildBackupExport()
                        backupLauncher.launch(pendingExport!!.fileName)
                    }
                },
                onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                onAddPlace = viewModel::openNewPlaceEditor,
                onEditPlace = viewModel::openPlaceEditor,
                onDeletePlace = viewModel::deletePlace,
            )
        }
    }

    if (uiState.onboardingVisible) {
        OnboardingSheet(
            settings = uiState.settings,
            onSave = viewModel::completeOnboarding,
        )
    }

    if (uiState.shiftEditorVisible) {
        ShiftEditorDialog(
            state = uiState.shiftEditor,
            onStateChange = viewModel::updateShiftEditor,
            onDismiss = viewModel::closeShiftEditor,
            onSave = viewModel::saveShiftEditor,
        )
    }

    if (uiState.placeEditorVisible) {
        PlaceEditorDialog(
            state = uiState.placeEditor,
            onStateChange = viewModel::updatePlaceEditor,
            onDismiss = viewModel::closePlaceEditor,
            onSave = viewModel::savePlaceEditor,
        )
    }

    if (showCustomRangeDialog) {
        CustomRangeDialog(
            onDismiss = { showCustomRangeDialog = false },
            onSave = { start, end ->
                showCustomRangeDialog = false
                viewModel.setCustomRange(start, end)
            },
        )
    }
}

@Composable
private fun LockedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("WorkCount is locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Return to the app and unlock it.")
        }
    }
}

private fun writeExportBytes(
    context: Context,
    uri: Uri?,
    payload: ExportPayload?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    if (uri == null || payload == null) return
    scope.launch {
        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(payload.bytes)
                stream.flush()
            } ?: error("Cannot open output stream")
        }.isSuccess

        snackbarHostState.showSnackbar(
            if (success) "File saved." else "Could not save the file.",
        )
    }
}
