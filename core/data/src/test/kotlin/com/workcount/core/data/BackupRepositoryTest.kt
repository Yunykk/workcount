package com.workcount.core.data

import com.workcount.core.domain.BackupBundle
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.PlaceRepository
import com.workcount.core.domain.SettingsRepository
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class BackupRepositoryTest {
    @Test
    fun `backup roundtrip preserves schema version`() = runTest {
        val shiftRepository = FakeShiftRepository()
        val placeRepository = FakePlaceRepository()
        val settingsRepository = FakeSettingsRepository()
        val repository = LocalBackupRepository(shiftRepository, placeRepository, settingsRepository)
        val backup = BackupBundle(
            schemaVersion = 3,
            generatedAtEpochMillis = 10L,
            shifts = emptyList(),
            places = emptyList(),
            settings = PaySettings(),
        )

        val bytes = repository.encodeBackup(backup)
        val decoded = repository.decodeBackup(bytes)

        assertEquals(3, decoded.schemaVersion)
    }
}

private class FakeShiftRepository : ShiftRepository {
    private val shifts = MutableStateFlow<List<Shift>>(emptyList())
    private val active = MutableStateFlow<Shift?>(null)

    override fun observeShifts(): Flow<List<Shift>> = shifts
    override fun observeActiveShift(): Flow<Shift?> = active
    override suspend fun getShift(id: String): Shift? = shifts.value.find { it.id == id }
    override suspend fun startShift(nowEpochMillis: Long, defaultHourlyRate: Double?, locationText: String, note: String): Shift {
        throw UnsupportedOperationException()
    }
    override suspend fun pauseActiveShift(nowEpochMillis: Long): Shift? = null
    override suspend fun resumeActiveShift(nowEpochMillis: Long): Shift? = null
    override suspend fun stopActiveShift(nowEpochMillis: Long): Shift? = null
    override suspend fun upsertShift(shift: Shift) {
        shifts.value = shifts.value + shift
    }
    override suspend fun deleteShift(id: String) {
        shifts.value = shifts.value.filterNot { it.id == id }
    }
    override suspend fun replaceAll(shifts: List<Shift>) {
        this.shifts.value = shifts
    }
}

private class FakePlaceRepository : PlaceRepository {
    private val places = MutableStateFlow<List<PlacePreset>>(emptyList())

    override fun observePlaces(): Flow<List<PlacePreset>> = places
    override suspend fun getPlace(id: String): PlacePreset? = places.value.find { it.id == id }
    override suspend fun findByName(name: String): PlacePreset? = places.value.find { it.name == name }
    override suspend fun upsertPlace(placePreset: PlacePreset) {
        places.value = places.value.filterNot { it.id == placePreset.id } + placePreset
    }
    override suspend fun deletePlace(id: String) {
        places.value = places.value.filterNot { it.id == id }
    }
    override suspend fun registerUsage(name: String, defaultHourlyRate: Double?, usedAtEpochMillis: Long) = Unit
    override suspend fun replaceAll(places: List<PlacePreset>) {
        this.places.value = places
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val settings = MutableStateFlow(PaySettings())

    override fun observeSettings(): Flow<PaySettings> = settings
    override suspend fun setSettings(settings: PaySettings) {
        this.settings.value = settings
    }
}
