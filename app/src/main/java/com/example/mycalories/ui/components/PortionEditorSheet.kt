package com.example.mycalories.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.model.LoggedItem

/** Bottom sheet to change how much of a food was eaten. Shows nutrients live. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortionEditorSheet(
    item: LoggedItem,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var quantity by remember(item) { mutableDoubleStateOf(item.quantity) }
    var unit by remember(item) { mutableStateOf(item.unit) }
    val preview = PortionCalculator.withPortion(item, quantity, unit)
    val step = if (PortionCalculator.normalize(unit) == "g") 10.0 else 0.5
    val units = (listOf(item.unit) + PortionCalculator.pickerUnits).distinctBy { PortionCalculator.normalize(it) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.how_much), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { quantity = (quantity - step).coerceAtLeast(0.0) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.less))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(quantity.qty(), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("quantity"))
                    Text(stringResource(R.string.unit_approx_grams, unitLabel(unit), preview.grams.grams()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { quantity += step }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.more))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u ->
                    FilterChip(
                        selected = PortionCalculator.normalize(u) == PortionCalculator.normalize(unit),
                        onClick = {
                            if (PortionCalculator.normalize(u) == "g" && PortionCalculator.normalize(unit) != "g") quantity = preview.grams
                            else if (PortionCalculator.normalize(unit) == "g" && PortionCalculator.normalize(u) != "g") quantity = 1.0
                            unit = u
                        },
                        label = { Text(unitLabel(u).replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.for_this_amount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.kcal_value, preview.nutrients.calories.kcal()), style = MaterialTheme.typography.titleLarge)
            }
            Text(preview.nutrients.shortMacros() + " · " + stringResource(R.string.g_fiber, preview.nutrients.fiber.grams()),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.source_line, item.source.label()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onSave(quantity, unit) }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
