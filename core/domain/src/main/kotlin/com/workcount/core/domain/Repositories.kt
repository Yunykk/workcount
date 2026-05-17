package com.workcount.core.domain

import kotlinx.coroutines.flow.Flow

interface ShiftRepository {
    fun observeShifts(): Flow<List<Shift>>

    fun observeActiveShift(): Flow<Shift?>

    suspend fun getShift(id: String): Shift?

    suspend fun startShift(
        nowEpochMillis: Long,
        defaultHourlyRate: Double?,
        locationText: String = "",
        note: String = "",
    ): Shift

    suspend fun pauseActiveShift(nowEpochMillis: Long): Shift?

    suspend fun resumeActiveShift(nowEpochMillis: Long): Shift?

    suspend fun stopActiveShift(nowEpochMillis: Long): Shift?

    suspend fun upsertShift(shift: Shift)

    suspend fun deleteShift(id: String)

    suspend fun replaceAll(shifts: List<Shift>)
}

interface PlaceRepository {
    fun observePlaces(): Flow<List<PlacePreset>>

    suspend fun getPlace(id: String): PlacePreset?

    suspend fun findByName(name: String): PlacePreset?

    suspend fun upsertPlace(placePreset: PlacePreset)

    suspend fun deletePlace(id: String)

    suspend fun registerUsage(
        name: String,
        defaultHourlyRate: Double? = null,
        usedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend fun replaceAll(places: List<PlacePreset>)
}

interface SettingsRepository {
    fun observeSettings(): Flow<PaySettings>

    suspend fun setSettings(settings: PaySettings)
}

interface BackupRepository {
    suspend fun createBackup(): BackupBundle

    suspend fun restoreBackup(bundle: BackupBundle)

    suspend fun encodeBackup(bundle: BackupBundle): ByteArray

    suspend fun decodeBackup(bytes: ByteArray): BackupBundle

    suspend fun exportCsv(
        shifts: List<Shift>,
        settings: PaySettings,
        places: Map<String, PlacePreset>,
        range: TimeRange,
        nowEpochMillis: Long,
    ): ByteArray

    suspend fun exportPdf(
        shifts: List<Shift>,
        settings: PaySettings,
        places: Map<String, PlacePreset>,
        range: TimeRange,
        nowEpochMillis: Long,
    ): ByteArray
}
