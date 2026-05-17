package com.workcount.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftSource
import com.workcount.core.domain.ShiftStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val status: String,
    val breakSeconds: Long,
    val pausedAtEpochMillis: Long?,
    val locationText: String,
    val note: String,
    val hourlyRateSnapshot: Double?,
    val source: String,
)

@Entity(
    tableName = "place_presets",
    indices = [Index(value = ["name"], unique = true)],
)
data class PlacePresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultHourlyRate: Double?,
    val lastUsedAtEpochMillis: Long,
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE status != 'COMPLETED' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeActive(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE status != 'COMPLETED' ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getActive(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ShiftEntity>)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM shifts")
    suspend fun clear()
}

@Dao
interface PlacePresetDao {
    @Query("SELECT * FROM place_presets ORDER BY lastUsedAtEpochMillis DESC, name ASC")
    fun observeAll(): Flow<List<PlacePresetEntity>>

    @Query("SELECT * FROM place_presets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlacePresetEntity?

    @Query("SELECT * FROM place_presets WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PlacePresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlacePresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PlacePresetEntity>)

    @Query("DELETE FROM place_presets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM place_presets")
    suspend fun clear()
}

@Database(
    entities = [ShiftEntity::class, PlacePresetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WorkCountDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao

    abstract fun placePresetDao(): PlacePresetDao

    companion object {
        fun create(context: Context): WorkCountDatabase {
            return Room.databaseBuilder(
                context,
                WorkCountDatabase::class.java,
                "workcount.db",
            ).fallbackToDestructiveMigration().build()
        }
    }
}

internal fun ShiftEntity.toDomain(): Shift {
    return Shift(
        id = id,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        status = ShiftStatus.valueOf(status),
        breakSeconds = breakSeconds,
        pausedAtEpochMillis = pausedAtEpochMillis,
        locationText = locationText,
        note = note,
        hourlyRateSnapshot = hourlyRateSnapshot,
        source = ShiftSource.valueOf(source),
    )
}

internal fun Shift.toEntity(): ShiftEntity {
    return ShiftEntity(
        id = id,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        status = status.name,
        breakSeconds = breakSeconds,
        pausedAtEpochMillis = pausedAtEpochMillis,
        locationText = locationText,
        note = note,
        hourlyRateSnapshot = hourlyRateSnapshot,
        source = source.name,
    )
}

internal fun PlacePresetEntity.toDomain(): PlacePreset {
    return PlacePreset(
        id = id,
        name = name,
        defaultHourlyRate = defaultHourlyRate,
        lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    )
}

internal fun PlacePreset.toEntity(): PlacePresetEntity {
    return PlacePresetEntity(
        id = id,
        name = name,
        defaultHourlyRate = defaultHourlyRate,
        lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    )
}

internal fun Flow<List<ShiftEntity>>.asDomainList(): Flow<List<Shift>> = map { entities ->
    entities.map(ShiftEntity::toDomain)
}

internal fun Flow<List<PlacePresetEntity>>.asDomainPlaces(): Flow<List<PlacePreset>> = map { entities ->
    entities.map(PlacePresetEntity::toDomain)
}
