package com.example.mycalories.ui.history

import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.ui.components.kcal
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HistoryRoute(viewModel: HistoryViewModel, contentPadding: PaddingValues, onWeight: () -> Unit = {}, onWeekly: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(state, contentPadding, onWeight, onWeekly)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HistoryUiState, contentPadding: PaddingValues, onWeight: () -> Unit = {}, onWeekly: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text(stringResource(R.string.history_title)) })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.daily_average_7), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.kcal_value, state.averageCalories.kcal()), style = MaterialTheme.typography.headlineMedium)
                    WeekChart(state)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.logging_streak), stringResource(R.string.days_count, state.streak), null, Modifier.weight(1f))
                StatCard(stringResource(R.string.weight), state.weightKg?.let { "%.1f ".format(it) + stringResource(R.string.kg) } ?: "—", null,
                    Modifier.weight(1f).testTag("weightCard"), onClick = onWeight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.avg_protein), stringResource(R.string.grams_value, state.averageProtein.roundToInt().toString()),
                    gap(state.averageProtein, state.goal.protein), Modifier.weight(1f))
                StatCard(stringResource(R.string.avg_fiber), stringResource(R.string.grams_value, state.averageFiber.roundToInt().toString()),
                    gap(state.averageFiber, state.goal.fiber), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onWeekly, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.weekly_summary_link)) }
                OutlinedButton(onClick = onWeight, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.weight_link)) }
            }
        }
    }
}

@Composable
private fun gap(avg: Double, goal: Int): String? {
    if (avg <= 0) return null
    val diff = goal - avg.roundToInt()
    return if (diff > 0) stringResource(R.string.short_of_goal, diff) else stringResource(R.string.goal_met)
}

@Composable
private fun StatCard(label: String, value: String, note: String?, modifier: Modifier, onClick: (() -> Unit)? = null) {
    OutlinedCard(onClick = onClick ?: {}, enabled = onClick != null, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekChart(state: HistoryUiState) {
    val within = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val today = MaterialTheme.colorScheme.primaryContainer
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val top = max(state.goalCalories.toDouble() * 1.25, state.days.maxOfOrNull { it.calories } ?: 0.0)
    val description = stringResource(R.string.calories_per_day,
        state.days.joinToString { "${it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.calories.roundToInt()}" })

    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val n = state.days.size.coerceAtLeast(1)
            val gapPx = 10.dp.toPx()
            val barW = (size.width - gapPx * (n - 1)) / n
            state.days.forEachIndexed { i, d ->
                val h = if (top > 0) (d.calories / top * size.height).toFloat() else 0f
                val color = when {
                    i == n - 1 -> today
                    d.calories > state.goalCalories -> over
                    else -> within
                }
                drawRoundRect(color, Offset(i * (barW + gapPx), size.height - h), Size(barW, h), CornerRadius(6.dp.toPx()))
            }
            val goalY = (size.height - state.goalCalories / top * size.height).toFloat()
            drawLine(line, Offset(0f, goalY), Offset(size.width, goalY), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.days.forEachIndexed { i, d ->
                Text(
                    if (i == state.days.lastIndex) stringResource(R.string.today) else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                )
            }
        }
        Text(stringResource(R.string.dashed_goal, "%,d".format(state.goalCalories)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
