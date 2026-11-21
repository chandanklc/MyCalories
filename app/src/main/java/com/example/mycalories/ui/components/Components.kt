package com.example.mycalories.ui.components

import androidx.compose.foundation.Canvas
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

/** Ring showing how much of the day's calories are used, with calories left in the middle. */
@Composable
fun CalorieRing(progress: Float, remaining: Int, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val label = stringResource(if (remaining >= 0) R.string.kcal_left else R.string.kcal_over)
    val track = MaterialTheme.colorScheme.primaryContainer
    val bar = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
            drawArc(bar, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%,d".format(kotlin.math.abs(remaining)), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(meal.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
