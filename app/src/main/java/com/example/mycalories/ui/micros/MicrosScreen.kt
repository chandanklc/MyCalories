package com.example.mycalories.ui.micros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.domain.MicroRow
import com.example.mycalories.domain.MicroStatus
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.helpText
import com.example.mycalories.ui.components.label
import java.util.Locale

@Composable
fun MicrosRoute(viewModel: MicrosViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MicrosScreen(state, onBack, viewModel::showWeek)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicrosScreen(state: MicrosUiState, onBack: () -> Unit, onWeek: (Boolean) -> Unit) {
    BackScreen(stringResource(R.string.micros_title), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!state.week, { onWeek(false) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.today)) }
                SegmentedButton(state.week, { onWeek(true) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.week_avg_tab)) }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.rows.forEach { MicroLine(it) }
                }
            }
            if (state.lowThisWeek.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val names = state.lowThisWeek.map { it.label() }.joinToString(", ")
                        Text(stringResource(R.string.low_week_title, names), style = MaterialTheme.typography.titleSmall)
                        state.lowThisWeek.forEach { k -> k.helpText()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                    }
                }
            }
            Text(stringResource(R.string.micros_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MicroLine(row: MicroRow) {
    val bad = row.status == MicroStatus.LOW || row.status == MicroStatus.OVER
    val color = when (row.status) {
        MicroStatus.OVER -> MaterialTheme.colorScheme.error
        MicroStatus.LOW -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(row.key.label(), Modifier.weight(1f))
            if (bad) Text(stringResource(if (row.status == MicroStatus.OVER) R.string.over else R.string.low) + "  ",
                color = color, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.micro_value, fmt(row.value), fmt(row.target), row.unit))
        }
        LinearProgressIndicator(progress = { row.progress }, modifier = Modifier.fillMaxWidth(), color = color, drawStopIndicator = {})
    }
}

private fun fmt(v: Double) = if (v >= 100) String.format(Locale.getDefault(), "%,.0f", v) else String.format(Locale.getDefault(), "%.1f", v).removeSuffix(".0")
