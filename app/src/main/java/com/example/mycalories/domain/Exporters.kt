package com.example.mycalories.domain

import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.model.sum
import java.time.LocalDate
import java.util.Locale

/** Plain-text CSV that opens in Excel or Google Sheets. */
object CsvExporter {

    private fun q(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    private fun d1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun num(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else d1(v)

    fun foods(items: List<LoggedItem>): String = buildString {
        appendLine("date,meal,food,quantity,unit,grams,kcal,protein_g,carbs_g,fat_g,fiber_g,sugar_g,sodium_mg,iron_mg,calcium_mg,vitamin_b12_ug,vitamin_d_ug,vitamin_c_mg,potassium_mg,source")
        items.sortedWith(compareBy({ it.date }, { it.meal.ordinal }, { it.loggedAtMillis })).forEach { i ->
            val n = i.nutrients
            appendLine(
                listOf(
                    i.date.toString(), i.meal.label, q(i.name), num(i.quantity), q(i.unit), num(i.grams),
                    d1(n.calories), d1(n.protein), d1(n.carbs), d1(n.fat), d1(n.fiber), d1(n.sugar), d1(n.sodiumMg),
                    d1(n.ironMg), d1(n.calciumMg), d1(n.vitaminB12Ug), d1(n.vitaminDUg), d1(n.vitaminCMg), d1(n.potassiumMg),
                    i.source.name,
                ).joinToString(","),
            )
        }
    }

    fun dailyTotals(items: List<LoggedItem>, weights: List<WeightEntry>): String = buildString {
        appendLine("date,kcal,protein_g,carbs_g,fat_g,fiber_g,weight_kg")
        val byDay = items.groupBy { it.date }
        val weightByDay = weights.associate { it.date to it.kg }
        (byDay.keys + weightByDay.keys).toSortedSet().forEach { day ->
            val n = byDay[day].orEmpty().map { it.nutrients }.sum()
            appendLine(
                listOf(day.toString(), Math.round(n.calories).toString(), d1(n.protein), d1(n.carbs), d1(n.fat), d1(n.fiber),
                    weightByDay[day]?.let(::d1).orEmpty()).joinToString(","),
            )
        }
    }
}

data class ReportDay(val date: LocalDate, val calories: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double)

/** What goes into the PDF report for a dietitian. */
data class ExportReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<ReportDay>,
    val averageCalories: Double,
    val averageProtein: Double,
    val weightStart: Double?,
    val weightEnd: Double?,
    val foodCount: Int,
)

object ReportBuilder {
    fun build(items: List<LoggedItem>, weights: List<WeightEntry>, from: LocalDate, to: LocalDate): ExportReport {
        val inRange = items.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val days = inRange.groupBy { it.date }.toSortedMap().map { (d, l) ->
            val n = l.map { it.nutrients }.sum()
            ReportDay(d, n.calories, n.protein, n.carbs, n.fat, n.fiber)
        }
        val w = weights.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
        return ExportReport(
            from, to, days,
            averageCalories = if (days.isEmpty()) 0.0 else days.map { it.calories }.average(),
            averageProtein = if (days.isEmpty()) 0.0 else days.map { it.protein }.average(),
            weightStart = w.firstOrNull()?.kg, weightEnd = w.lastOrNull()?.kg, foodCount = inRange.size,
        )
    }
}

/** Draws an [ExportReport] as PDF bytes. Android's PdfDocument in the app; a fake in tests. */
fun interface PdfMaker {
    fun make(report: ExportReport): ByteArray
}
