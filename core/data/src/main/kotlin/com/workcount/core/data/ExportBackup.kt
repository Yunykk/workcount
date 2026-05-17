package com.workcount.core.data

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.workcount.core.domain.BackupBundle
import com.workcount.core.domain.BackupRepository
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.PlacePreset
import com.workcount.core.domain.PlaceRepository
import com.workcount.core.domain.SettingsRepository
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftCalculators
import com.workcount.core.domain.ShiftRepository
import com.workcount.core.domain.TimeRange
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class LocalBackupRepository(
    private val shiftRepository: ShiftRepository,
    private val placeRepository: PlaceRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) : BackupRepository {
    override suspend fun createBackup(): BackupBundle {
        return BackupBundle(
            schemaVersion = 1,
            generatedAtEpochMillis = System.currentTimeMillis(),
            shifts = shiftRepository.observeShifts().first(),
            places = placeRepository.observePlaces().first(),
            settings = settingsRepository.observeSettings().first(),
        )
    }

    override suspend fun restoreBackup(bundle: BackupBundle) {
        shiftRepository.replaceAll(bundle.shifts)
        placeRepository.replaceAll(bundle.places)
        settingsRepository.setSettings(bundle.settings)
    }

    override suspend fun encodeBackup(bundle: BackupBundle): ByteArray {
        return json.encodeToString(bundle).encodeToByteArray()
    }

    override suspend fun decodeBackup(bytes: ByteArray): BackupBundle {
        return json.decodeFromString(bytes.decodeToString())
    }

    override suspend fun exportCsv(
        shifts: List<Shift>,
        settings: PaySettings,
        places: Map<String, PlacePreset>,
        range: TimeRange,
        nowEpochMillis: Long,
    ): ByteArray {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val zoneId = ZoneId.systemDefault()
        val builder = StringBuilder()
        builder.appendLine("Period,${csvEscape(range.label)}")
        builder.appendLine("Start,End,Place,Note,Status,Break minutes,Paid hours,Rate,Earnings")
        shifts.forEach { shift ->
            val place = places[shift.locationText.trim()]
            val start = formatter.format(Instant.ofEpochMilli(shift.startedAtEpochMillis).atZone(zoneId))
            val end = shift.endedAtEpochMillis?.let { formatter.format(Instant.ofEpochMilli(it).atZone(zoneId)) } ?: "-"
            val paidHours = ShiftCalculators.roundedPayableDurationSeconds(shift, settings, nowEpochMillis) / 3600.0
            val rate = ShiftCalculators.resolveHourlyRate(shift, settings, place)
            val earnings = ShiftCalculators.earnings(shift, settings, nowEpochMillis, place)
            builder.appendLine(
                listOf(
                    start,
                    end,
                    csvEscape(shift.locationText),
                    csvEscape(shift.note),
                    shift.status.name,
                    (ShiftCalculators.totalBreakSeconds(shift, nowEpochMillis) / 60L).toString(),
                    formatNumber(paidHours),
                    formatNumber(rate),
                    formatNumber(earnings),
                ).joinToString(","),
            )
        }
        return builder.toString().encodeToByteArray()
    }

    override suspend fun exportPdf(
        shifts: List<Shift>,
        settings: PaySettings,
        places: Map<String, PlacePreset>,
        range: TimeRange,
        nowEpochMillis: Long,
    ): ByteArray {
        val document = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply { textSize = 11f }
        val zoneId = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val summary = ShiftCalculators.summarise(shifts, settings, nowEpochMillis, places)

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas = page.canvas
        var y = 48f

        fun finishPage() {
            document.finishPage(page)
        }

        fun newPage() {
            finishPage()
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvas = page.canvas
            y = 40f
        }

        canvas.drawText("WorkCount", 40f, y, titlePaint)
        y += 24f
        canvas.drawText("Period: ${range.label}", 40f, y, textPaint)
        y += 18f
        canvas.drawText("Shifts: ${summary.shiftCount}", 40f, y, textPaint)
        y += 16f
        canvas.drawText(
            "Hours: ${formatNumber(summary.totalSeconds / 3600.0)}  Earnings: ${formatNumber(summary.totalEarnings)} ${settings.currency}",
            40f,
            y,
            textPaint,
        )
        y += 26f

        shifts.forEachIndexed { index, shift ->
            if (y > 780f) newPage()
            val place = places[shift.locationText.trim()]
            val hours = ShiftCalculators.roundedPayableDurationSeconds(shift, settings, nowEpochMillis) / 3600.0
            val earnings = ShiftCalculators.earnings(shift, settings, nowEpochMillis, place)
            val start = formatter.format(Instant.ofEpochMilli(shift.startedAtEpochMillis).atZone(zoneId))
            val end = shift.endedAtEpochMillis?.let { formatter.format(Instant.ofEpochMilli(it).atZone(zoneId)) } ?: "active"
            canvas.drawText("${index + 1}. $start - $end", 40f, y, textPaint)
            y += 16f
            canvas.drawText(
                "Place: ${shift.locationText.ifBlank { "-" }} | Hours: ${formatNumber(hours)} | Earnings: ${formatNumber(earnings)} ${settings.currency}",
                56f,
                y,
                textPaint,
            )
            y += 16f
            if (shift.note.isNotBlank()) {
                canvas.drawText("Note: ${shift.note.take(80)}", 56f, y, textPaint)
                y += 16f
            }
            y += 6f
        }

        finishPage()
        return ByteArrayOutputStream().use { stream ->
            document.writeTo(stream)
            document.close()
            stream.toByteArray()
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun formatNumber(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }
}
