package com.workcount.core.data

import androidx.room.withTransaction
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.PlaceRepository
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftRepository
import com.workcount.core.domain.ShiftSource
import com.workcount.core.domain.ShiftStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomShiftRepository(
    private val database: WorkCountDatabase,
) : ShiftRepository {
    private val dao = database.shiftDao()

    override fun observeShifts(): Flow<List<Shift>> = dao.observeAll().asDomainList()

    override fun observeActiveShift(): Flow<Shift?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun getShift(id: String): Shift? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun startShift(
        nowEpochMillis: Long,
        defaultHourlyRate: Double?,
        locationText: String,
        note: String,
    ): Shift = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.getActive()?.toDomain()?.let { return@withTransaction it }

            val shift = Shift(
                id = UUID.randomUUID().toString(),
                startedAtEpochMillis = nowEpochMillis,
                endedAtEpochMillis = null,
                status = ShiftStatus.ACTIVE,
                breakSeconds = 0L,
                pausedAtEpochMillis = null,
                locationText = locationText,
                note = note,
                hourlyRateSnapshot = defaultHourlyRate,
                source = ShiftSource.TIMER,
            )
            dao.upsert(shift.toEntity())
            shift
        }
    }

    override suspend fun pauseActiveShift(nowEpochMillis: Long): Shift? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val active = dao.getActive()?.toDomain() ?: return@withTransaction null
            if (active.status != ShiftStatus.ACTIVE) return@withTransaction active

            val paused = active.copy(
                status = ShiftStatus.PAUSED,
                pausedAtEpochMillis = nowEpochMillis,
            )
            dao.upsert(paused.toEntity())
            paused
        }
    }

    override suspend fun resumeActiveShift(nowEpochMillis: Long): Shift? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val active = dao.getActive()?.toDomain() ?: return@withTransaction null
            val pausedAtEpochMillis = active.pausedAtEpochMillis
            if (active.status != ShiftStatus.PAUSED || pausedAtEpochMillis == null) return@withTransaction active

            val resumed = active.copy(
                status = ShiftStatus.ACTIVE,
                breakSeconds = active.breakSeconds + ((nowEpochMillis - pausedAtEpochMillis).coerceAtLeast(0L) / 1000L),
                pausedAtEpochMillis = null,
            )
            dao.upsert(resumed.toEntity())
            resumed
        }
    }

    override suspend fun stopActiveShift(nowEpochMillis: Long): Shift? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val active = dao.getActive()?.toDomain() ?: return@withTransaction null
            val pausedAtEpochMillis = active.pausedAtEpochMillis
            val finalBreak = if (active.status == ShiftStatus.PAUSED && pausedAtEpochMillis != null) {
                active.breakSeconds + ((nowEpochMillis - pausedAtEpochMillis).coerceAtLeast(0L) / 1000L)
            } else {
                active.breakSeconds
            }

            val stopped = active.copy(
                status = ShiftStatus.COMPLETED,
                endedAtEpochMillis = nowEpochMillis,
                breakSeconds = finalBreak,
                pausedAtEpochMillis = null,
            )
            dao.upsert(stopped.toEntity())
            stopped
        }
    }

    override suspend fun upsertShift(shift: Shift) = withContext(Dispatchers.IO) {
        dao.upsert(shift.toEntity())
    }

    override suspend fun deleteShift(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }

    override suspend fun replaceAll(shifts: List<Shift>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clear()
            dao.upsertAll(shifts.map(Shift::toEntity))
        }
    }
}

class RoomPlaceRepository(
    private val database: WorkCountDatabase,
) : PlaceRepository {
    private val dao = database.placePresetDao()

    override fun observePlaces(): Flow<List<PlacePreset>> = dao.observeAll().asDomainPlaces()

    override suspend fun getPlace(id: String): PlacePreset? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun findByName(name: String): PlacePreset? = withContext(Dispatchers.IO) {
        dao.findByName(name.trim())?.toDomain()
    }

    override suspend fun upsertPlace(placePreset: PlacePreset) = withContext(Dispatchers.IO) {
        dao.upsert(placePreset.toEntity())
    }

    override suspend fun deletePlace(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }

    override suspend fun registerUsage(
        name: String,
        defaultHourlyRate: Double?,
        usedAtEpochMillis: Long,
    ) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext

        val existing = dao.findByName(trimmed)
        val entity = if (existing != null) {
            existing.copy(
                defaultHourlyRate = defaultHourlyRate ?: existing.defaultHourlyRate,
                lastUsedAtEpochMillis = usedAtEpochMillis,
            )
        } else {
            PlacePresetEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                defaultHourlyRate = defaultHourlyRate,
                lastUsedAtEpochMillis = usedAtEpochMillis,
            )
        }
        dao.upsert(entity)
    }

    override suspend fun replaceAll(places: List<PlacePreset>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clear()
            dao.upsertAll(places.map(PlacePreset::toEntity))
        }
    }
}
