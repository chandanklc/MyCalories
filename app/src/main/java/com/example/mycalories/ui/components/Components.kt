package com.example.mycalories.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.ui.theme.MacroColors
import kotlin.math.roundToInt

fun Double.kcal(): String = "%,d".format(roundToInt())
fun Double.grams(): String = if (this < 10) "%.1f".format(this).removeSuffix(".0") else roundToInt().toString()
fun Double.qty(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this).removeSuffix(".0")

val MealType.icon: ImageVector
    get() = when (this) {
        MealType.BREAKFAST -> Icons.Filled.WbSunny
        MealType.LUNCH -> Icons.Filled.LunchDining
        MealType.SNACK -> Icons.Filled.Cookie
        MealType.DINNER -> Icons.Filled.Bedtime
    }

/** A fixed colour per meal, used for its part of the calorie ring, its key and its icon. */
object MealColors {
    val breakfast = Color(0xFFE59A12) // morning sun
    val lunch = Color(0xFF1E7F55)     // brand green
    val snack = Color(0xFFD4507A)     // berry
    val dinner = Color(0xFF5560D6)    // evening indigo

    fun of(meal: MealType): Color = when (meal) {
        MealType.BREAKFAST -> breakfast
        MealType.LUNCH -> lunch
        MealType.SNACK -> snack
        MealType.DINNER -> dinner
    }
}

/** One meal's part of the ring; [start] and [sweep] are fractions of the full circle. */
data class RingSegment(val meal: MealType, val start: Float, val sweep: Float)

/**
 * Splits the ring by meal, in meal order. Each meal takes its share of the day's budget; once the
 * budget is passed (or none is set) the meals fill the whole ring in proportion to each other.
 */
fun ringSegments(caloriesByMeal: Map<MealType, Double>, budget: Double): List<RingSegment> {
    val eaten = MealType.entries.mapNotNull { m -> caloriesByMeal[m]?.takeIf { it > 0 }?.let { m to it } }
    if (eaten.isEmpty()) return emptyList()
    val total = eaten.sumOf { it.second }
    val whole = if (budget > 0 && total <= budget) budget else total
    var start = 0f
    return eaten.map { (meal, kcal) ->
        val sweep = (kcal / whole).toFloat()
        RingSegment(meal, start, sweep).also { start += sweep }
    }
}

/** Ring of the day's calories, coloured by meal, with calories left (or over) in the middle. */
@Composable
fun CalorieRing(segments: List<RingSegment>, remaining: Int, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val label = stringResource(if (remaining >= 0) R.string.kcal_left else R.string.kcal_over)
    // Neutral grey for the part not eaten yet, so it never looks like a meal colour.
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val over = MaterialTheme.colorScheme.error
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "${kotlin.math.abs(remaining)} $label" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            // A small gap between meals so each colour reads as its own part.
            val gap = if (segments.size > 1) 2.5f else 0f
            segments.forEach { s ->
                val sweep = s.sweep * 360f
                if (sweep > gap) drawArc(
                    MealColors.of(s.meal), -90f + s.start * 360f + gap / 2, sweep - gap, false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
            if (remaining < 0) {
                // Thin red line inside the ring when the day's budget is passed.
                val thin = 3.dp.toPx()
                val o = stroke + thin
                drawArc(over, 0f, 360f, false, Offset(o, o), Size(this.size.width - 2 * o, this.size.height - 2 * o),
                    style = Stroke(thin))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(kotlin.math.abs(remaining)), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (remaining < 0) over else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Colour key under the ring: a dot, the meal and its calories, for each meal logged. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MealRingKey(segments: List<RingSegment>, caloriesByMeal: Map<MealType, Double>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEach { s ->
            Row(
                Modifier.testTag("ringKey_${s.meal.name}").semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(10.dp).background(MealColors.of(s.meal), CircleShape))
                Text("${s.meal.label()} ${(caloriesByMeal[s.meal] ?: 0.0).kcal()}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One nutrient tile: name, amount / goal, and a bar. */
@Composable
fun MacroTile(label: String, value: Double, goal: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.macro_of_goal, value.roundToInt(), goal), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = color, trackColor = color.copy(alpha = 0.15f), drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun MacroRow(n: Nutrients, protein: Int, carbs: Int, fat: Int, fiber: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroTile(stringResource(R.string.protein), n.protein, protein, MacroColors.protein, Modifier.weight(1f))
        MacroTile(stringResource(R.string.carbs), n.carbs, carbs, MacroColors.carbs, Modifier.weight(1f))
        MacroTile(stringResource(R.string.fat), n.fat, fat, MacroColors.fat, Modifier.weight(1f))
        MacroTile(stringResource(R.string.fiber), n.fiber, fiber, MacroColors.fiber, Modifier.weight(1f))
    }
}

/** "P 6 · C 36 · F 7" line under a food. */
@Composable
fun Nutrients.shortMacros(): String = stringResource(R.string.short_macros, protein.grams(), carbs.grams(), fat.grams())

@Composable
fun SourceChip(source: FoodSource, needsReview: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(if (needsReview) stringResource(R.string.please_check) else source.label(), style = MaterialTheme.typography.labelSmall) },
        colors = if (needsReview) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        else AssistChipDefaults.assistChipColors(),
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun MealIcon(meal: MealType, modifier: Modifier = Modifier) {
    val color = MealColors.of(meal)
    Surface(modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.16f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(meal.icon, contentDescription = null, tint = color)
        }
    }
}
