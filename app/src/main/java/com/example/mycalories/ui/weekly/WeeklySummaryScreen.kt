package com.example.mycalories.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.ui.components.BackScreen
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun WeeklySummaryRoute(viewModel: WeeklySummaryViewModel, onBack: () -> Unit, onAsk: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeeklySummaryScreen(state, onBack, onAsk)
}

@Composable
fun WeeklySummaryScreen(state: WeeklyUiState, onBack: () -> Unit, onAsk: () -> Unit) {
    val s = state.summary
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    BackScreen(stringResource(R.string.your_week), onBack, subtitle = s?.let { "${it.from.format(fmt)} – ${it.to.format(fmt)}" }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s == null || !s.hasData) {
                Text(stringResource(R.string.no_week_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(stringResource(R.string.avg_per_day), "%,d".format(s.averageCalories.roundToInt()), Modifier.weight(1f))
                Tile(stringResource(R.string.on_target), stringResource(R.string.x_of_y, s.daysOnTarget, 7), Modifier.weight(1f))
                Tile(stringResource(R.string.weight), s.weightChange?.let { "%+.1f ".format(it) + stringResource(R.string.kg) } ?: "—", Modifier.weight(1f))
            }
            if (s.lowProteinDays >= 3) OutlinedCard(Modifier.fillMaxWidth().testTag("toWorkOn")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.to_work_on), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(stringResource(R.string.protein_low, s.lowProteinDays), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.protein_avg_line, s.averageProtein.roundToInt(), state.goal.protein))
                    s.proteinTips.forEach { Text("• " + stringResource(R.string.tip_line, it.food, it.grams)) }
                }
            }
            val wins = buildList {
                s.fiberChange?.takeIf { it >= 1 }?.let { add(R.string.fiber_up to "%.0f".format(it)) }
                if (s.streak >= 3) add(R.string.streak_line to s.streak)
                if (s.waterGoalDays > 0) add(R.string.water_line to s.waterGoalDays)
            }
            if (wins.isNotEmpty()) OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.went_well), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    wins.forEach { (res, arg) -> Text(stringResource(res, arg)) }
                }
            }
        }
        Button(onClick = onAsk, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(stringResource(R.string.ask_week)) }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
