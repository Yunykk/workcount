package com.workcount.core.domain

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Serializable
enum class ShiftStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
}

@Serializable
enum class ShiftSource {
    TIMER,
    MANUAL,
}

@Serializable
enum class RoundingMode {
    EXACT,
    QUARTER_HOUR,
    HALF_HOUR,
    ;

    fun roundSeconds(value: Long): Long {
        if (value <= 0L) return 0L
        return when (this) {
            EXACT -> value
            QUARTER_HOUR -> roundNearest(value, 15 * 60)
            HALF_HOUR -> roundNearest(value, 30 * 60)
        }
    }

    private fun roundNearest(value: Long, stepSeconds: Int): Long {
        val step = stepSeconds.toLong()
        return ((value + (step / 2)) / step) * step
    }
}

@Serializable
enum class ForgottenTimerPolicy {
    NONE,
    REMINDER,
    AUTO_STOP,
}

@Serializable
enum class PeriodPreset {
    DAY,
    WEEK,
    MONTH,
    CUSTOM,
}

@Serializable
data class Shift(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val status: ShiftStatus = ShiftStatus.COMPLETED,
    val breakSeconds: Long = 0L,
    val pausedAtEpochMillis: Long? = null,
    val locationText: String = "",
    val note: String = "",
    val hourlyRateSnapshot: Double? = null,
    val source: ShiftSource = ShiftSource.MANUAL,
)

@Serializable
data class PlacePreset(
    val id: String,
    val name: String,
    val defaultHourlyRate: Double? = null,
    val lastUsedAtEpochMillis: Long = 0L,
)

@Serializable
data class PaySettings(
    val currency: String = "EUR",
    val defaultHourlyRate: Double? = null,
    val roundingMode: RoundingMode = RoundingMode.EXACT,
    val forgottenTimerPolicy: ForgottenTimerPolicy = ForgottenTimerPolicy.REMINDER,
    val reminderThresholdHours: Int = 8,
    val reminderRepeatMinutes: Int = 30,
    val privacyLockEnabled: Boolean = false,
)

@Serializable
data class TimeRange(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val label: String,
)

data class ShiftFilter(
    val preset: PeriodPreset = PeriodPreset.DAY,
    val anchorDate: LocalDate = LocalDate.now(),
    val customRange: TimeRange? = null,
    val locationQuery: String = "",
    val status: ShiftStatus? = null,
)

@Serializable
data class BackupBundle(
    val schemaVersion: Int = 1,
    val generatedAtEpochMillis: Long,
    val shifts: List<Shift>,
    val places: List<PlacePreset>,
    val settings: PaySettings,
)

data class SummaryTotals(
    val totalSeconds: Long = 0L,
    val totalEarnings: Double = 0.0,
    val shiftCount: Int = 0,
)

object PayrollPeriods {
    fun forPreset(
        preset: PeriodPreset,
        anchorDate: LocalDate,
        zoneId: ZoneId,
        customRange: TimeRange? = null,
    ): TimeRange = when (preset) {
        PeriodPreset.DAY -> day(anchorDate, zoneId)
        PeriodPreset.WEEK -> week(anchorDate, zoneId)
        PeriodPreset.MONTH -> month(anchorDate, zoneId)
        PeriodPreset.CUSTOM -> customRange ?: day(anchorDate, zoneId)
    }

    fun day(date: LocalDate, zoneId: ZoneId): TimeRange {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return TimeRange(start, end, date.toString())
    }

    fun week(anchor: LocalDate, zoneId: ZoneId): TimeRange {
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val start = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = sunday.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return TimeRange(start, end, "$monday - $sunday")
    }

    fun month(anchor: LocalDate, zoneId: ZoneId): TimeRange {
        val firstDay = anchor.withDayOfMonth(1)
        val lastDay = anchor.withDayOfMonth(anchor.lengthOfMonth())
        val start = firstDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return TimeRange(start, end, "${anchor.year}-${anchor.monthValue.toString().padStart(2, '0')}")
    }

    fun custom(startEpochMillis: Long, endEpochMillis: Long, zoneId: ZoneId): TimeRange {
        val startDate = Instant.ofEpochMilli(startEpochMillis).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(endEpochMillis).atZone(zoneId).toLocalDate()
        return TimeRange(startEpochMillis, endEpochMillis, "$startDate - $endDate")
    }
}
