package com.workcount.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workcount.app.location.LocationResolver
import com.workcount.core.domain.BackupRepository
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.PayrollPeriods
import com.workcount.core.domain.PeriodPreset
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.PlaceRepository
import com.workcount.core.domain.SettingsRepository
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftCalculators
import com.workcount.core.domain.ShiftFilter
import com.workcount.core.domain.ShiftRepository
import com.workcount.core.domain.ShiftSource
import com.workcount.core.domain.ShiftStatus
import com.workcount.core.domain.SummaryTotals
import com.workcount.core.domain.TimeRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class AppTab {
    HOME,
    JOURNAL,
    SETTINGS,
}

data class ShiftEditorState(
    val id: String? = null,
    val dateText: String = LocalDate.now().toString(),
    val startTimeText: String = "09:00",
    val endTimeText: String = "17:00",
    val breakMinutesText: String = "0",
    val locationText: String = "",
    val note: String = "",
    val rateText: String = "",
)

data class PlaceEditorState(
    val id: String? = null,
    val name: String = "",
    val rateText: String = "",
)

data class ExportPayload(
    val fileName: String,
    val bytes: ByteArray,
)

data class WorkCountUiState(
    val settings: PaySettings = PaySettings(),
    val activeShift: Shift? = null,
    val places: List<PlacePreset> = emptyList(),
    val allShifts: List<Shift> = emptyList(),
    val homeTodaySummary: SummaryTotals = SummaryTotals(),
    val homeWeekSummary: SummaryTotals = SummaryTotals(),
    val homeMonthSummary: SummaryTotals = SummaryTotals(),
    val journalFilter: ShiftFilter = ShiftFilter(),
    val journalRange: TimeRange = PayrollPeriods.day(LocalDate.now(), ZoneId.systemDefault()),
    val journalShifts: List<Shift> = emptyList(),
    val journalSummary: SummaryTotals = SummaryTotals(),
    val selectedTab: AppTab = AppTab.HOME,
    val onboardingVisible: Boolean = true,
    val shiftEditorVisible: Boolean = false,
    val shiftEditor: ShiftEditorState = ShiftEditorState(),
    val placeEditorVisible: Boolean = false,
    val placeEditor: PlaceEditorState = PlaceEditorState(),
    val nowEpochMillis: Long = System.currentTimeMillis(),
    val message: String? = null,
)

class WorkCountViewModel(
    private val shiftRepository: ShiftRepository,
    private val placeRepository: PlaceRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val locationResolver: LocationResolver,
) : ViewModel() {
    private val selectedTab = MutableStateFlow(AppTab.HOME)
    private val journalFilter = MutableStateFlow(ShiftFilter())
    private val shiftEditorVisible = MutableStateFlow(false)
    private val shiftEditor = MutableStateFlow(ShiftEditorState())
    private val placeEditorVisible = MutableStateFlow(false)
    private val placeEditor = MutableStateFlow(PlaceEditorState())
    private val message = MutableStateFlow<String?>(null)
    private val nowTicker = MutableStateFlow(System.currentTimeMillis())

    private val settingsFlow = settingsRepository.observeSettings()
    private val shiftsFlow = shiftRepository.observeShifts()
    private val activeShiftFlow = shiftRepository.observeActiveShift()
    private val placesFlow = placeRepository.observePlaces()

    val uiState: StateFlow<WorkCountUiState> = combine(
        settingsFlow,
        shiftsFlow,
        activeShiftFlow,
        placesFlow,
        selectedTab,
        journalFilter,
        shiftEditorVisible,
        shiftEditor,
        placeEditorVisible,
        placeEditor,
        nowTicker,
        message,
    ) { values ->
        val settings = values[0] as PaySettings
        val allShifts = values[1] as List<Shift>
        val activeShift = values[2] as Shift?
        val places = values[3] as List<PlacePreset>
        val tab = values[4] as AppTab
        val filter = values[5] as ShiftFilter
        val isShiftEditorVisible = values[6] as Boolean
        val shiftEditorState = values[7] as ShiftEditorState
        val isPlaceEditorVisible = values[8] as Boolean
        val placeEditorState = values[9] as PlaceEditorState
        val nowEpochMillis = values[10] as Long
        val messageText = values[11] as String?

        val zoneId = ZoneId.systemDefault()
        val placesByName = places.associateBy { it.name.trim() }
        val todayRange = PayrollPeriods.day(LocalDate.now(zoneId), zoneId)
        val weekRange = PayrollPeriods.week(LocalDate.now(zoneId), zoneId)
        val monthRange = PayrollPeriods.month(LocalDate.now(zoneId), zoneId)
        val resolvedJournalRange = PayrollPeriods.forPreset(
            preset = filter.preset,
            anchorDate = filter.anchorDate,
            zoneId = zoneId,
            customRange = filter.customRange,
        )
        val journalShifts = allShifts.filter { shift ->
            shift.startedAtEpochMillis in resolvedJournalRange.startEpochMillis..resolvedJournalRange.endEpochMillis &&
                (filter.locationQuery.isBlank() || shift.locationText.contains(filter.locationQuery, ignoreCase = true)) &&
                (filter.status == null || shift.status == filter.status)
        }

        WorkCountUiState(
            settings = settings,
            activeShift = activeShift,
            places = places,
            allShifts = allShifts,
            homeTodaySummary = summariseForRange(allShifts, settings, todayRange, nowEpochMillis, placesByName),
            homeWeekSummary = summariseForRange(allShifts, settings, weekRange, nowEpochMillis, placesByName),
            homeMonthSummary = summariseForRange(allShifts, settings, monthRange, nowEpochMillis, placesByName),
            journalFilter = filter,
            journalRange = resolvedJournalRange,
            journalShifts = journalShifts,
            journalSummary = ShiftCalculators.summarise(journalShifts, settings, nowEpochMillis, placesByName),
            selectedTab = tab,
            onboardingVisible = settings.defaultHourlyRate == null,
            shiftEditorVisible = isShiftEditorVisible,
            shiftEditor = shiftEditorState,
            placeEditorVisible = isPlaceEditorVisible,
            placeEditor = placeEditorState,
            nowEpochMillis = nowEpochMillis,
            message = messageText,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkCountUiState(),
    )

    init {
        viewModelScope.launch {
            while (true) {
                nowTicker.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun setTab(tab: AppTab) {
        selectedTab.value = tab
    }

    fun consumeMessage() {
        message.value = null
    }

    fun completeOnboarding(rateText: String, currency: String) {
        val rate = parseRate(rateText)
        if (rate == null || rate <= 0.0) {
            message.value = "Enter a valid hourly rate."
            return
        }

        viewModelScope.launch {
            settingsRepository.setSettings(
                uiState.value.settings.copy(
                    currency = currency.ifBlank { "EUR" },
                    defaultHourlyRate = rate,
                ),
            )
        }
    }

    fun saveSettings(
        currency: String,
        defaultRateText: String,
        roundingModeName: String,
        forgottenPolicyName: String,
        reminderThresholdHoursText: String,
        reminderRepeatMinutesText: String,
        privacyLockEnabled: Boolean,
    ) {
        val defaultRate = parseOptionalRate(defaultRateText)
        val threshold = reminderThresholdHoursText.toIntOrNull()?.coerceAtLeast(1) ?: 8
        val repeat = reminderRepeatMinutesText.toIntOrNull()?.coerceAtLeast(5) ?: 30

        viewModelScope.launch {
            settingsRepository.setSettings(
                uiState.value.settings.copy(
                    currency = currency.ifBlank { "EUR" },
                    defaultHourlyRate = defaultRate,
                    roundingMode = com.workcount.core.domain.RoundingMode.valueOf(roundingModeName),
                    forgottenTimerPolicy = com.workcount.core.domain.ForgottenTimerPolicy.valueOf(forgottenPolicyName),
                    reminderThresholdHours = threshold,
                    reminderRepeatMinutes = repeat,
                    privacyLockEnabled = privacyLockEnabled,
                ),
            )
            message.value = "Settings saved."
        }
    }

    fun startShift() {
        val settings = uiState.value.settings
        if (settings.defaultHourlyRate == null) {
            message.value = "Set a default rate first."
            return
        }
        viewModelScope.launch {
            shiftRepository.startShift(
                nowEpochMillis = System.currentTimeMillis(),
                defaultHourlyRate = settings.defaultHourlyRate,
            )
        }
    }

    fun pauseOrResumeActiveShift() {
        viewModelScope.launch {
            when (uiState.value.activeShift?.status) {
                ShiftStatus.ACTIVE -> shiftRepository.pauseActiveShift(System.currentTimeMillis())
                ShiftStatus.PAUSED -> shiftRepository.resumeActiveShift(System.currentTimeMillis())
                else -> Unit
            }
        }
    }

    fun stopActiveShift() {
        viewModelScope.launch {
            val stopped = shiftRepository.stopActiveShift(System.currentTimeMillis())
            if (!stopped?.locationText.isNullOrBlank()) {
                placeRepository.registerUsage(
                    name = stopped!!.locationText,
                    defaultHourlyRate = stopped.hourlyRateSnapshot,
                )
            }
        }
    }

    fun updateActiveLocation(location: String) {
        val current = uiState.value.activeShift ?: return
        viewModelScope.launch {
            shiftRepository.upsertShift(current.copy(locationText = location))
        }
    }

    fun updateActiveNote(note: String) {
        val current = uiState.value.activeShift ?: return
        viewModelScope.launch {
            shiftRepository.upsertShift(current.copy(note = note))
        }
    }

    fun updateActiveRate(rateText: String) {
        val current = uiState.value.activeShift ?: return
        val rate = parseOptionalRate(rateText)
        viewModelScope.launch {
            shiftRepository.upsertShift(current.copy(hourlyRateSnapshot = rate))
        }
    }

    fun applyPresetToActive(placePreset: PlacePreset) {
        val current = uiState.value.activeShift ?: return
        viewModelScope.launch {
            shiftRepository.upsertShift(
                current.copy(
                    locationText = placePreset.name,
                    hourlyRateSnapshot = current.hourlyRateSnapshot ?: placePreset.defaultHourlyRate ?: current.hourlyRateSnapshot,
                ),
            )
        }
    }

    fun fillActiveLocationFromGps() {
        val current = uiState.value.activeShift ?: return
        viewModelScope.launch {
            val address = locationResolver.getCurrentAddress()
            if (address.isNullOrBlank()) {
                message.value = "Could not get the current address."
            } else {
                shiftRepository.upsertShift(current.copy(locationText = address))
            }
        }
    }

    fun setJournalPreset(preset: PeriodPreset) {
        journalFilter.value = journalFilter.value.copy(preset = preset)
    }

    fun setJournalAnchorDate(date: LocalDate) {
        journalFilter.value = journalFilter.value.copy(anchorDate = date)
    }

    fun setCustomRange(startDateText: String, endDateText: String) {
        val zoneId = ZoneId.systemDefault()
        val start = runCatching { LocalDate.parse(startDateText) }.getOrNull()
        val end = runCatching { LocalDate.parse(endDateText) }.getOrNull()
        if (start == null || end == null || end.isBefore(start)) {
            message.value = "Check the date range."
            return
        }
        journalFilter.value = journalFilter.value.copy(
            preset = PeriodPreset.CUSTOM,
            customRange = PayrollPeriods.custom(
                start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
                zoneId,
            ),
        )
    }

    fun setLocationFilter(query: String) {
        journalFilter.value = journalFilter.value.copy(locationQuery = query)
    }

    fun setStatusFilter(status: ShiftStatus?) {
        journalFilter.value = journalFilter.value.copy(status = status)
    }

    fun openNewShiftEditor() {
        val now = LocalDateTime.now()
        shiftEditor.value = ShiftEditorState(
            dateText = now.toLocalDate().toString(),
            startTimeText = now.toLocalTime().withSecond(0).withNano(0).toString(),
            endTimeText = now.toLocalTime().withSecond(0).withNano(0).toString(),
            breakMinutesText = "0",
        )
        shiftEditorVisible.value = true
    }

    fun openShiftEditor(shiftId: String) {
        viewModelScope.launch {
            val shift = shiftRepository.getShift(shiftId) ?: return@launch
            val zoneId = ZoneId.systemDefault()
            val startDateTime = Instant.ofEpochMilli(shift.startedAtEpochMillis).atZone(zoneId).toLocalDateTime()
            val endDateTime = Instant.ofEpochMilli(shift.endedAtEpochMillis ?: shift.startedAtEpochMillis).atZone(zoneId).toLocalDateTime()
            shiftEditor.value = ShiftEditorState(
                id = shift.id,
                dateText = startDateTime.toLocalDate().toString(),
                startTimeText = startDateTime.toLocalTime().toString().take(5),
                endTimeText = endDateTime.toLocalTime().toString().take(5),
                breakMinutesText = (shift.breakSeconds / 60L).toString(),
                locationText = shift.locationText,
                note = shift.note,
                rateText = shift.hourlyRateSnapshot?.toString().orEmpty(),
            )
            shiftEditorVisible.value = true
        }
    }

    fun closeShiftEditor() {
        shiftEditorVisible.value = false
    }

    fun updateShiftEditor(state: ShiftEditorState) {
        shiftEditor.value = state
    }

    fun saveShiftEditor() {
        val editor = shiftEditor.value
        val zoneId = ZoneId.systemDefault()
        val date = runCatching { LocalDate.parse(editor.dateText) }.getOrNull()
        val startTime = runCatching { LocalTime.parse(editor.startTimeText) }.getOrNull()
        val endTime = runCatching { LocalTime.parse(editor.endTimeText) }.getOrNull()
        val breakMinutes = editor.breakMinutesText.toLongOrNull()
        if (date == null || startTime == null || endTime == null || breakMinutes == null) {
            message.value = "Check the date, time, and break."
            return
        }

        val start = date.atTime(startTime)
        val rawEnd = date.atTime(endTime)
        val end = if (rawEnd.isAfter(start)) rawEnd else rawEnd.plusDays(1)
        val rate = parseOptionalRate(editor.rateText)

        viewModelScope.launch {
            val shift = Shift(
                id = editor.id ?: UUID.randomUUID().toString(),
                startedAtEpochMillis = start.atZone(zoneId).toInstant().toEpochMilli(),
                endedAtEpochMillis = end.atZone(zoneId).toInstant().toEpochMilli(),
                status = ShiftStatus.COMPLETED,
                breakSeconds = (breakMinutes * 60L).coerceAtLeast(0L),
                pausedAtEpochMillis = null,
                locationText = editor.locationText.trim(),
                note = editor.note.trim(),
                hourlyRateSnapshot = rate,
                source = ShiftSource.MANUAL,
            )
            shiftRepository.upsertShift(shift)
            if (shift.locationText.isNotBlank()) {
                placeRepository.registerUsage(
                    name = shift.locationText,
                    defaultHourlyRate = shift.hourlyRateSnapshot,
                )
            }
            shiftEditorVisible.value = false
        }
    }

    fun deleteShift(shiftId: String) {
        viewModelScope.launch {
            shiftRepository.deleteShift(shiftId)
        }
    }

    fun openNewPlaceEditor() {
        placeEditor.value = PlaceEditorState()
        placeEditorVisible.value = true
    }

    fun openPlaceEditor(placePreset: PlacePreset) {
        placeEditor.value = PlaceEditorState(
            id = placePreset.id,
            name = placePreset.name,
            rateText = placePreset.defaultHourlyRate?.toString().orEmpty(),
        )
        placeEditorVisible.value = true
    }

    fun closePlaceEditor() {
        placeEditorVisible.value = false
    }

    fun updatePlaceEditor(state: PlaceEditorState) {
        placeEditor.value = state
    }

    fun savePlaceEditor() {
        val editor = placeEditor.value
        val name = editor.name.trim()
        if (name.isBlank()) {
            message.value = "Enter a place name."
            return
        }

        viewModelScope.launch {
            placeRepository.upsertPlace(
                PlacePreset(
                    id = editor.id ?: UUID.randomUUID().toString(),
                    name = name,
                    defaultHourlyRate = parseOptionalRate(editor.rateText),
                    lastUsedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            placeEditorVisible.value = false
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            placeRepository.deletePlace(placeId)
        }
    }

    suspend fun buildCsvExport(): ExportPayload {
        val state = uiState.value
        return ExportPayload(
            fileName = "workcount-${state.journalRange.label}.csv",
            bytes = backupRepository.exportCsv(
                shifts = state.journalShifts,
                settings = state.settings,
                places = state.places.associateBy { it.name.trim() },
                range = state.journalRange,
                nowEpochMillis = state.nowEpochMillis,
            ),
        )
    }

    suspend fun buildPdfExport(): ExportPayload {
        val state = uiState.value
        return ExportPayload(
            fileName = "workcount-${state.journalRange.label}.pdf",
            bytes = backupRepository.exportPdf(
                shifts = state.journalShifts,
                settings = state.settings,
                places = state.places.associateBy { it.name.trim() },
                range = state.journalRange,
                nowEpochMillis = state.nowEpochMillis,
            ),
        )
    }

    suspend fun buildBackupExport(): ExportPayload {
        val bundle = backupRepository.createBackup()
        return ExportPayload(
            fileName = "workcount-backup-${bundle.generatedAtEpochMillis}.json",
            bytes = backupRepository.encodeBackup(bundle),
        )
    }

    fun restoreBackup(bytes: ByteArray) {
        viewModelScope.launch {
            val bundle = backupRepository.decodeBackup(bytes)
            backupRepository.restoreBackup(bundle)
            message.value = "Backup restored."
        }
    }

    private fun summariseForRange(
        shifts: List<Shift>,
        settings: PaySettings,
        range: TimeRange,
        nowEpochMillis: Long,
        places: Map<String, PlacePreset>,
    ): SummaryTotals {
        val inRange = shifts.filter { it.startedAtEpochMillis in range.startEpochMillis..range.endEpochMillis }
        return ShiftCalculators.summarise(inRange, settings, nowEpochMillis, places)
    }

    private fun parseRate(text: String): Double? {
        return text.replace(',', '.').toDoubleOrNull()
    }

    private fun parseOptionalRate(text: String): Double? {
        return if (text.isBlank()) null else parseRate(text)
    }
}
