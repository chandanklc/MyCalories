package com.example.mycalories.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.ui.components.BackScreen
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun WeightRoute(viewModel: WeightViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeightScreen(state, onBack, viewModel::log, viewModel::setTarget, viewModel::acceptSuggestion, viewModel::dismissSuggestion)
}

@Composable
fun WeightScreen(
    state: WeightUiState,
    onBack: () -> Unit,
    onLog: (String) -> Unit,
    onTarget: (String) -> Unit,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dialog by remember { mutableStateOf<String?>(null) } // "log" or "target"
    BackScreen(stringResource(R.string.weight_title), onBack, actions = {
        Button(onClick = { dialog = "log" }, modifier = Modifier.padding(end = 8.dp).testTag("logWeight")) { Text(stringResource(R.string.log_weight)) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.weight_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.current?.let { "%.1f ".format(it) + stringResource(R.string.kg) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(stringResource(R.string.target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { dialog = "target" }) {
                                Text(state.targetKg?.let { "%.1f ".format(it) + stringResource(R.string.kg) } ?: stringResource(R.string.set_target))
                            }
                        }
                    }
                    if (state.entries.size >= 2) WeightChart(state) else Text(stringResource(R.string.no_weights), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.weeklyRate?.let { Text(stringResource(R.string.per_week, "%+.2f".format(it)), style = MaterialTheme.typography.titleSmall) }
                    if (state.projectedDate != null && state.targetKg != null) {
                        Text(stringResource(R.string.reach_target, "%.1f".format(state.targetKg),
                            state.projectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.suggestion?.let { s ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.testTag("suggestion")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.adjust_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.adjust_text, "%.2f".format(abs(state.weeklyRate ?: 0.0)), s.newCalories))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.keep_goal, s.currentCalories)) }
                            Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.update_goal)) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.weights_from_scale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    dialog?.let { kind ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (kind == "log") R.string.log_weight else R.string.set_target)) },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.weight_kg_hint)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("weightField"))
            },
            confirmButton = { TextButton(onClick = { if (kind == "log") onLog(text) else onTarget(text); dialog = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WeightChart(state: WeightUiState) {
    val line = MaterialTheme.colorScheme.primary
    val target = MaterialTheme.colorScheme.onSurfaceVariant
    val entries = state.entries.takeLast(30)
    val desc = stringResource(R.string.weight_chart_desc, "%.1f".format(entries.last().kg))
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = desc }) {
        val values = entries.map { it.kg } + listOfNotNull(state.targetKg)
        val lo = values.min() - 0.5
        val hi = values.max() + 0.5
        val first = entries.first().date
        val span = ChronoUnit.DAYS.between(first, entries.last().date).coerceAtLeast(1).toFloat()
        fun x(d: java.time.LocalDate) = ChronoUnit.DAYS.between(first, d) / span * size.width
        fun y(v: Double) = ((hi - v) / (hi - lo) * size.height).toFloat()
        val path = Path()
        entries.forEachIndexed { i, e -> if (i == 0) path.moveTo(x(e.date), y(e.kg)) else path.lineTo(x(e.date), y(e.kg)) }
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(line, 5.dp.toPx(), Offset(x(entries.last().date), y(entries.last().kg)))
        state.targetKg?.let {
            drawLine(target, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}
