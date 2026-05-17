package com.workcount.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workcount.app.PlaceEditorState
import com.workcount.app.ShiftEditorState
import com.workcount.app.WorkCountUiState
import com.workcount.core.domain.ForgottenTimerPolicy
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.RoundingMode
import java.time.LocalDate

@Composable
internal fun SettingsTab(
    modifier: Modifier,
    uiState: WorkCountUiState,
    onSaveSettings: (String, String, String, String, String, String, Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onAddPlace: () -> Unit,
    onEditPlace: (PlacePreset) -> Unit,
    onDeletePlace: (String) -> Unit,
) {
    var currency by remember(uiState.settings) { mutableStateOf(uiState.settings.currency) }
    var defaultRate by remember(uiState.settings) { mutableStateOf(uiState.settings.defaultHourlyRate?.toString().orEmpty()) }
    var roundingMode by remember(uiState.settings) { mutableStateOf(uiState.settings.roundingMode.name) }
    var forgottenPolicy by remember(uiState.settings) { mutableStateOf(uiState.settings.forgottenTimerPolicy.name) }
    var thresholdHours by remember(uiState.settings) { mutableStateOf(uiState.settings.reminderThresholdHours.toString()) }
    var repeatMinutes by remember(uiState.settings) { mutableStateOf(uiState.settings.reminderRepeatMinutes.toString()) }
    var privacyLockEnabled by remember(uiState.settings) { mutableStateOf(uiState.settings.privacyLockEnabled) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroHeader(
                title = "Settings",
                subtitle = "Rates, rounding, reminders, privacy, and exports.",
            )
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Currency") },
                    )
                    OutlinedTextField(
                        value = defaultRate,
                        onValueChange = { defaultRate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Default rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Text("Rounding", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = roundingMode == mode.name,
                                onClick = { roundingMode = mode.name },
                                label = { Text(roundingLabel(mode)) },
                            )
                        }
                    }
                    Text("Forgotten timer", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ForgottenTimerPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = forgottenPolicy == policy.name,
                                onClick = { forgottenPolicy = policy.name },
                                label = { Text(policyLabel(policy)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = thresholdHours,
                        onValueChange = { thresholdHours = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Remind after, hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = repeatMinutes,
                        onValueChange = { repeatMinutes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repeat reminder, minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Biometric / PIN lock")
                        Switch(
                            checked = privacyLockEnabled,
                            onCheckedChange = { privacyLockEnabled = it },
                        )
                    }
                    Button(
                        onClick = {
                            onSaveSettings(
                                currency,
                                defaultRate,
                                roundingMode,
                                forgottenPolicy,
                                thresholdHours,
                                repeatMinutes,
                                privacyLockEnabled,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save settings")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Files and backups", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export CSV")
                    }
                    Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export PDF")
                    }
                    OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                        Text("Create backup")
                    }
                    OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore backup")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Work places", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onAddPlace) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    }
                    if (uiState.places.isEmpty()) {
                        Text("No saved places yet.")
                    } else {
                        uiState.places.forEach { place ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(place.name)
                                Text(
                                    text = place.defaultHourlyRate
                                        ?.let { formatMoney(it, uiState.settings.currency) }
                                        ?: "No rate set",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onEditPlace(place) }) {
                                        Text("Edit")
                                    }
                                    IconButton(onClick = { onDeletePlace(place.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = null)
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingSheet(
    settings: PaySettings,
    onSave: (String, String) -> Unit,
) {
    var rate by remember(settings) { mutableStateOf(settings.defaultHourlyRate?.toString().orEmpty()) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }

    ModalBottomSheet(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Initial setup", style = MaterialTheme.typography.headlineSmall)
            Text("Set a default rate and currency.")
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Currency") },
            )
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default hourly rate") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(onClick = { onSave(rate, currency) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save and start")
            }
        }
    }
}

@Composable
internal fun ShiftEditorDialog(
    state: ShiftEditorState,
    onStateChange: (ShiftEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.id == null) "New shift" else "Edit shift")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.dateText,
                    onValueChange = { onStateChange(state.copy(dateText = it)) },
                    label = { Text("Date YYYY-MM-DD") },
                )
                OutlinedTextField(
                    value = state.startTimeText,
                    onValueChange = { onStateChange(state.copy(startTimeText = it)) },
                    label = { Text("Start HH:MM") },
                )
                OutlinedTextField(
                    value = state.endTimeText,
                    onValueChange = { onStateChange(state.copy(endTimeText = it)) },
                    label = { Text("End HH:MM") },
                )
                OutlinedTextField(
                    value = state.breakMinutesText,
                    onValueChange = { onStateChange(state.copy(breakMinutesText = it)) },
                    label = { Text("Break, minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.locationText,
                    onValueChange = { onStateChange(state.copy(locationText = it)) },
                    label = { Text("Place") },
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onStateChange(state.copy(note = it)) },
                    label = { Text("Note") },
                )
                OutlinedTextField(
                    value = state.rateText,
                    onValueChange = { onStateChange(state.copy(rateText = it)) },
                    label = { Text("Rate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun PlaceEditorDialog(
    state: PlaceEditorState,
    onStateChange: (PlaceEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.id == null) "New place" else "Edit place")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onStateChange(state.copy(name = it)) },
                    label = { Text("Place name") },
                )
                OutlinedTextField(
                    value = state.rateText,
                    onValueChange = { onStateChange(state.copy(rateText = it)) },
                    label = { Text("Default rate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(7).toString()) }
    var endDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom period") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start YYYY-MM-DD") },
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("End YYYY-MM-DD") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(startDate, endDate) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun policyLabel(policy: ForgottenTimerPolicy): String = when (policy) {
    ForgottenTimerPolicy.NONE -> "Off"
    ForgottenTimerPolicy.REMINDER -> "Remind"
    ForgottenTimerPolicy.AUTO_STOP -> "Auto-stop"
}
