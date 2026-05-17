package com.workcount.core.domain

import kotlin.math.max

object ShiftCalculators {
    fun totalBreakSeconds(shift: Shift, nowEpochMillis: Long): Long {
        val liveBreak = when {
            shift.status == ShiftStatus.PAUSED && shift.pausedAtEpochMillis != null -> {
                ((nowEpochMillis - shift.pausedAtEpochMillis).coerceAtLeast(0L)) / 1000L
            }

            else -> 0L
        }
        return max(0L, shift.breakSeconds + liveBreak)
    }

    fun rawDurationSeconds(shift: Shift, nowEpochMillis: Long): Long {
        val effectiveEnd = shift.endedAtEpochMillis ?: nowEpochMillis
        return ((effectiveEnd - shift.startedAtEpochMillis).coerceAtLeast(0L)) / 1000L
    }

    fun payableDurationSeconds(shift: Shift, nowEpochMillis: Long): Long {
        return (rawDurationSeconds(shift, nowEpochMillis) - totalBreakSeconds(shift, nowEpochMillis)).coerceAtLeast(0L)
    }

    fun roundedPayableDurationSeconds(
        shift: Shift,
        settings: PaySettings,
        nowEpochMillis: Long,
    ): Long = settings.roundingMode.roundSeconds(payableDurationSeconds(shift, nowEpochMillis))

    fun resolveHourlyRate(
        shift: Shift,
        settings: PaySettings,
        placePreset: PlacePreset? = null,
    ): Double = shift.hourlyRateSnapshot ?: placePreset?.defaultHourlyRate ?: settings.defaultHourlyRate ?: 0.0

    fun earnings(
        shift: Shift,
        settings: PaySettings,
        nowEpochMillis: Long,
        placePreset: PlacePreset? = null,
    ): Double {
        val seconds = roundedPayableDurationSeconds(shift, settings, nowEpochMillis)
        val rate = resolveHourlyRate(shift, settings, placePreset)
        return (seconds / 3600.0) * rate
    }

    fun summarise(
        shifts: List<Shift>,
        settings: PaySettings,
        nowEpochMillis: Long,
        places: Map<String, PlacePreset> = emptyMap(),
    ): SummaryTotals {
        val seconds = shifts.sumOf { roundedPayableDurationSeconds(it, settings, nowEpochMillis) }
        val earnings = shifts.sumOf { shift ->
            earnings(
                shift = shift,
                settings = settings,
                nowEpochMillis = nowEpochMillis,
                placePreset = places[shift.locationText.trim()],
            )
        }
        return SummaryTotals(
            totalSeconds = seconds,
            totalEarnings = earnings,
            shiftCount = shifts.size,
        )
    }
}
