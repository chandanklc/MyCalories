package com.example.mycalories.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.CsvExporter
import com.example.mycalories.domain.ExportReport
import com.example.mycalories.domain.PdfMaker
import com.example.mycalories.domain.ReportBuilder
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.WeightRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

enum class ExportRange(val days: Long?) { DAYS_7(7), DAYS_30(30), ALL(null) }
enum class ExportFormat { PDF, CSV }

data class ExportFile(val fileName: String, val mimeType: String, val bytes: ByteArray)

data class ExportUiState(
    val range: ExportRange = ExportRange.DAYS_30,
    val format: ExportFormat = ExportFormat.PDF,
    val includeFoods: Boolean = true,
    val includeTotals: Boolean = true,
    val preview: ExportReport? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExportViewModel(
    private val log: LogRepository,
    private val weights: WeightRepository,
    private val pdf: PdfMaker,
    private val clock: Clock,
) : ViewModel() {

    private val today get() = LocalDate.now(clock)
    private val options = MutableStateFlow(ExportUiState())

    private fun from(range: ExportRange): LocalDate = range.days?.let { today.minusDays(it - 1) } ?: LocalDate.of(2000, 1, 1)

    val state: StateFlow<ExportUiState> = options.flatMapLatest { o ->
        combine(log.observeRange(from(o.range), today), weights.observeAll()) { items, w ->
            o.copy(preview = ReportBuilder.build(items, w, items.minOfOrNull { it.date } ?: from(o.range), today))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExportUiState())

    fun setRange(r: ExportRange) { options.value = options.value.copy(range = r) }
    fun setFormat(f: ExportFormat) { options.value = options.value.copy(format = f) }
    fun setIncludeFoods(on: Boolean) { options.value = options.value.copy(includeFoods = on) }
    fun setIncludeTotals(on: Boolean) { options.value = options.value.copy(includeTotals = on) }

    /** Makes the file to save or share. */
    suspend fun build(): ExportFile {
        val o = options.value
        val start = from(o.range)
        val items = log.observeRange(start, today).first()
        val w = weights.observeAll().first()
        val stamp = today.toString()
        return when (o.format) {
            ExportFormat.PDF -> ExportFile("mycalories-$stamp.pdf", "application/pdf",
                pdf.make(ReportBuilder.build(items, w, items.minOfOrNull { it.date } ?: start, today)))
            ExportFormat.CSV -> {
                val text = buildString {
                    if (o.includeFoods) append(CsvExporter.foods(items))
                    if (o.includeFoods && o.includeTotals) appendLine()
                    if (o.includeTotals) append(CsvExporter.dailyTotals(items, w.filter { !it.date.isBefore(start) }))
                }
                ExportFile("mycalories-$stamp.csv", "text/csv", text.toByteArray())
            }
        }
    }
}
