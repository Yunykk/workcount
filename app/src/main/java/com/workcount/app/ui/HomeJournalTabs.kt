package com.workcount.app.ui

import android.widget.CalendarView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.workcount.app.WorkCountUiState
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.PeriodPreset
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.RoundingMode
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftCalculators
import com.workcount.core.domain.ShiftStatus
import com.workcount.core.domain.SummaryTotals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeTab(
    modifier: Modifier,
    uiState: WorkCountUiState,
    onStart: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onLocationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onUseGps: () -> Unit,
    onUsePreset: (PlacePreset) -> Unit,
) {
    val activeShift = uiState.activeShift
    val suggestions = uiState.places
        .filter {
            activeShift == null ||
                activeShift.locationText.isBlank() ||
                it.name.contains(activeShift.locationText, ignoreCase = true)
        }
        .take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroHeader(
                title = "Work hours",
                subtitle = if (activeShift == null) {
                    "Start a shift and add details when needed."
                } else {
                    "The active shift is being tracked."
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Today", uiState.homeTodaySummary, uiState.settings.currency, Modifier.fillMaxWidth())
                SummaryCard("Week", uiState.homeWeekSummary, uiState.settings.currency, Modifier.fillMaxWidth())
                SummaryCard("Month", uiState.homeMonthSummary, uiState.settings.currency, Modifier.fillMaxWidth())
            }
        }

        if (activeShift == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Start a shift.",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Button(
                            onClick = onStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start", style = MaterialTheme.typography.titleLarge)
                        }
                        if (uiState.places.isNotEmpty()) {
                            Divider()
                            Text("Frequent places", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.places.take(6).forEach { place ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(place.name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                ActiveShiftCard(
                    activeShift = activeShift,
                    settings = uiState.settings,
                    places = uiState.places,
                    nowEpochMillis = uiState.nowEpochMillis,
                    suggestions = suggestions,
                    onPauseResume = onPauseResume,
                    onStop = onStop,
                    onLocationChange = onLocationChange,
                    onNoteChange = onNoteChange,
                    onRateChange = onRateChange,
                    onUseGps = onUseGps,
                    onUsePreset = onUsePreset,
                )
            }
        }
    }
}

@Composable
private fun ActiveShiftCard(
    activeShift: Shift,
    settings: PaySettings,
    places: List<PlacePreset>,
    nowEpochMillis: Long,
    suggestions: List<PlacePreset>,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onLocationChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onUseGps: () -> Unit,
    onUsePreset: (PlacePreset) -> Unit,
) {
    val durationSeconds = ShiftCalculators.payableDurationSeconds(activeShift, nowEpochMillis)
    val earnings = ShiftCalculators.earnings(
        shift = activeShift,
        settings = settings,
        nowEpochMillis = nowEpochMillis,
        placePreset = places.firstOrNull { it.name == activeShift.locationText },
    )

    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (activeShift.status == ShiftStatus.PAUSED) "Paused" else "Active shift",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(formatDuration(durationSeconds), style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    text = formatMoney(earnings, settings.currency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedButton(onClick = onPauseResume, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = if (activeShift.status == ShiftStatus.PAUSED) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.Pause
                    },
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (activeShift.status == ShiftStatus.PAUSED) "Resume" else "Pause")
            }

            OutlinedTextField(
                value = activeShift.locationText,
                onValueChange = onLocationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Place") },
                trailingIcon = {
                    IconButton(onClick = onUseGps) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null)
                    }
                },
            )

            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { place ->
                        AssistChip(
                            onClick = { onUsePreset(place) },
                            label = { Text(place.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = activeShift.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 2,
            )

            OutlinedTextField(
                value = activeShift.hourlyRateSnapshot?.toString().orEmpty(),
                onValueChange = onRateChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hourly rate") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(settings.currency) },
            )
        }
    }
}

@Composable
internal fun JournalTab(
    modifier: Modifier,
    uiState: WorkCountUiState,
    onPresetSelected: (PeriodPreset) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onLocationFilterChange: (String) -> Unit,
    onStatusSelected: (ShiftStatus?) -> Unit,
    onOpenCustomRange: () -> Unit,
    onEditShift: (String) -> Unit,
    onDeleteShift: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroHeader(
                title = "Journal",
                subtitle = "Browse, filter, and edit shifts.",
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
                    Text("Anchor date", style = MaterialTheme.typography.titleMedium)
                    AndroidView(
                        factory = { context ->
                            CalendarView(context).apply {
                                setOnDateChangeListener { _, year, month, dayOfMonth ->
                                    onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PeriodPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = uiState.journalFilter.preset == preset,
                                onClick = {
                                    if (preset == PeriodPreset.CUSTOM) {
                                        onOpenCustomRange()
                                    } else {
                                        onPresetSelected(preset)
                                    }
                                },
                                label = { Text(presetLabel(preset)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.journalFilter.locationQuery,
                        onValueChange = onLocationFilterChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Place filter") },
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.journalFilter.status == null,
                            onClick = { onStatusSelected(null) },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = uiState.journalFilter.status == ShiftStatus.ACTIVE,
                            onClick = { onStatusSelected(ShiftStatus.ACTIVE) },
                            label = { Text("Active") },
                        )
                        FilterChip(
                            selected = uiState.journalFilter.status == ShiftStatus.PAUSED,
                            onClick = { onStatusSelected(ShiftStatus.PAUSED) },
                            label = { Text("Paused") },
                        )
                        FilterChip(
                            selected = uiState.journalFilter.status == ShiftStatus.COMPLETED,
                            onClick = { onStatusSelected(ShiftStatus.COMPLETED) },
                            label = { Text("Done") },
                        )
                    }
                    SummaryCard(
                        title = "Period: ${uiState.journalRange.label}",
                        summary = uiState.journalSummary,
                        currency = uiState.settings.currency,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        items(uiState.journalShifts, key = { it.id }) { shift ->
            ShiftRow(
                shift = shift,
                settings = uiState.settings,
                ratePlace = uiState.places.firstOrNull { it.name == shift.locationText },
                nowEpochMillis = uiState.nowEpochMillis,
                onEdit = { onEditShift(shift.id) },
                onDelete = { onDeleteShift(shift.id) },
            )
        }
    }
}

@Composable
private fun ShiftRow(
    shift: Shift,
    settings: PaySettings,
    ratePlace: PlacePreset?,
    nowEpochMillis: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    val zoneId = ZoneId.systemDefault()
    val start = formatter.format(Instant.ofEpochMilli(shift.startedAtEpochMillis).atZone(zoneId))
    val end = shift.endedAtEpochMillis
        ?.let { formatter.format(Instant.ofEpochMilli(it).atZone(zoneId)) }
        ?: "active"
    val hours = ShiftCalculators.payableDurationSeconds(shift, nowEpochMillis) / 3600.0
    val earnings = ShiftCalculators.earnings(
        shift = shift,
        settings = settings,
        nowEpochMillis = nowEpochMillis,
        placePreset = ratePlace,
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(start, style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(earnings, settings.currency), color = MaterialTheme.colorScheme.primary)
            }
            Text("To: $end")
            Text("Place: ${shift.locationText.ifBlank { "-" }}")
            if (shift.note.isNotBlank()) {
                Text("Note: ${shift.note}")
            }
            Text("Hours: ${String.format(Locale.US, "%.2f", hours)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
internal fun HeroHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SummaryCard(
    title: String,
    summary: SummaryTotals,
    currency: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(formatDuration(summary.totalSeconds), style = MaterialTheme.typography.titleLarge)
            Text(formatMoney(summary.totalEarnings, currency), color = MaterialTheme.colorScheme.primary)
            Text("${summary.shiftCount} shifts", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun presetLabel(preset: PeriodPreset): String = when (preset) {
    PeriodPreset.DAY -> "Day"
    PeriodPreset.WEEK -> "Week"
    PeriodPreset.MONTH -> "Month"
    PeriodPreset.CUSTOM -> "Custom"
}

internal fun roundingLabel(mode: RoundingMode): String = when (mode) {
    RoundingMode.EXACT -> "Exact"
    RoundingMode.QUARTER_HOUR -> "15 min"
    RoundingMode.HALF_HOUR -> "30 min"
}

internal fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "%02d:%02d".format(hours, minutes)
}

internal fun formatMoney(value: Double, currency: String): String {
    return String.format(Locale.US, "%.2f %s", value, currency)
}
