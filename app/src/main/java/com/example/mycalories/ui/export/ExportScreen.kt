package com.example.mycalories.ui.export

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ExportRoute(viewModel: ExportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<ExportFile?>(null) }
    val savedText = stringResource(R.string.backup_saved)

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pending ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) } }
            snackbar.showSnackbar(savedText)
        }
    }
    ExportScreen(
        state, onBack, snackbar,
        onRange = viewModel::setRange, onFormat = viewModel::setFormat,
        onFoods = viewModel::setIncludeFoods, onTotals = viewModel::setIncludeTotals,
        onSave = { scope.launch { viewModel.build().also { pending = it; saveAs.launch(it.fileName) } } },
        onShare = { scope.launch { share(context, viewModel.build()) } },
    )
}

private suspend fun share(context: Context, file: ExportFile) {
    val out = withContext(Dispatchers.IO) {
        File(context.cacheDir, "exports").apply { mkdirs() }.resolve(file.fileName).apply { writeBytes(file.bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
    val send = Intent(Intent.ACTION_SEND).setType(file.mimeType).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    onRange: (ExportRange) -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onFoods: (Boolean) -> Unit,
    onTotals: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    BackScreen(stringResource(R.string.export_title), onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.dates))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportRange.DAYS_7 to R.string.last_7, ExportRange.DAYS_30 to R.string.last_30, ExportRange.ALL to R.string.all_time).forEach { (r, label) ->
                    FilterChip(state.range == r, { onRange(r) }, label = { Text(stringResource(label)) })
                }
            }
            SectionLabel(stringResource(R.string.format))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(state.format == ExportFormat.PDF, { onFormat(ExportFormat.PDF) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.pdf_report)) }
                SegmentedButton(state.format == ExportFormat.CSV, { onFormat(ExportFormat.CSV) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.csv_sheet)) }
            }
            if (state.format == ExportFormat.CSV) {
                SectionLabel(stringResource(R.string.include))
                CheckRow(stringResource(R.string.meals_and_foods), state.includeFoods, onFoods)
                CheckRow(stringResource(R.string.daily_totals), state.includeTotals, onTotals)
            }
            state.preview?.let { p ->
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.preview_line, "%,d".format(p.averageCalories.roundToInt()), p.averageProtein.roundToInt().toString(), p.days.size))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.save_to_phone)) }
            Button(onClick = onShare, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, Modifier.padding(end = 12.dp))
        Text(label)
    }
}
