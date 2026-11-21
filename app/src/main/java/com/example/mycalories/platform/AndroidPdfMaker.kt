package com.example.mycalories.platform

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.mycalories.domain.ExportReport
import com.example.mycalories.domain.PdfMaker
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The lines of the PDF food diary: a title, a summary, then one row per day. Kept apart from drawing so it can be tested. */
object PdfLines {
    enum class Style { TITLE, BOLD, NORMAL }
    data class Line(val text: String, val style: Style)

    fun build(report: ExportReport, title: String, locale: Locale = Locale.getDefault()): List<Line> {
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        return buildList {
            add(Line("$title · ${report.from.format(fmt)} – ${report.to.format(fmt)}", Style.TITLE))
            add(Line("Average ${report.averageCalories.roundToInt()} kcal and ${report.averageProtein.roundToInt()} g protein a day over ${report.days.size} logged days.", Style.NORMAL))
            if (report.weightStart != null && report.weightEnd != null) {
                add(Line(String.format(Locale.US, "Weight %.1f → %.1f kg", report.weightStart, report.weightEnd), Style.NORMAL))
            }
            add(Line("${report.foodCount} foods logged.", Style.NORMAL))
            add(Line("Date              kcal     Protein g   Carbs g   Fat g   Fiber g", Style.BOLD))
            report.days.forEach { d ->
                add(Line(String.format(Locale.US, "%-16s %6d %10.0f %9.0f %7.0f %9.0f",
                    d.date.format(fmt), d.calories.roundToInt(), d.protein, d.carbs, d.fat, d.fiber), Style.NORMAL))
            }
        }
    }
}

/** Draws [PdfLines] onto A4 pages with Android's PdfDocument. */
class AndroidPdfMaker(private val title: String = "Food diary") : PdfMaker {

    override fun make(report: ExportReport): ByteArray {
        val doc = PdfDocument()
        val width = 595
        val height = 842
        val margin = 48f
        val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val big = Paint(bold).apply { textSize = 18f }

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
        var y = margin
        PdfLines.build(report, title).forEach { line ->
            if (y > height - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
                y = margin
            }
            val paint = when (line.style) {
                PdfLines.Style.TITLE -> big
                PdfLines.Style.BOLD -> bold
                PdfLines.Style.NORMAL -> normal
            }
            page.canvas.drawText(line.text, margin, y, paint)
            y += if (line.style == PdfLines.Style.TITLE) 28f else 18f
        }
        doc.finishPage(page)
        return ByteArrayOutputStream().use { out -> doc.writeTo(out); doc.close(); out.toByteArray() }
    }
}
