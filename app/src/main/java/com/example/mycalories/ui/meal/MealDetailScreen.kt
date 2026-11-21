package com.example.mycalories.ui.meal

import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AssistChip
import com.example.mycalories.ui.components.label
import com.example.mycalories.ui.components.labelRes
import com.example.mycalories.ui.components.unitLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.ui.components.PortionEditorSheet
import com.example.mycalories.ui.components.grams
import com.example.mycalories.ui.components.kcal
import com.example.mycalories.ui.components.qty
import com.example.mycalories.ui.components.shortMacros
import com.example.mycalories.ui.theme.MacroColors

@Composable
fun MealDetailRoute(viewModel: MealDetailViewModel, onBack: () -> Unit, onAddByVoice: () -> Unit, onMicros: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MealDetailScreen(
        state, onBack, onAddByVoice,
        onDelete = viewModel::delete, onUndo = viewModel::undoDelete, onUpdate = viewModel::update,
        onMoveAll = viewModel::moveAll, onRepeatTomorrow = viewModel::repeatTomorrow, onMessageShown = viewModel::messageShown,
        onMicros = onMicros,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    state: MealDetailUiState,
    onBack: () -> Unit,
    onAddByVoice: () -> Unit,
    onDelete: (Long) -> Unit,
    onUndo: () -> Unit,
    onUpdate: (LoggedItem, Double, String) -> Unit,
    onMoveAll: (MealType) -> Unit,
    onRepeatTomorrow: () -> Unit,
    onMessageShown: () -> Unit,
    onMicros: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<LoggedItem?>(null) }
    var menu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val msg = when (val m = state.message) {
            null -> return@LaunchedEffect
            is MealMessage.Removed -> context.getString(R.string.removed, m.name)
            is MealMessage.Moved -> context.getString(R.string.moved, context.getString(m.meal.labelRes))
            MealMessage.CopiedTomorrow -> context.getString(R.string.copied_tomorrow)
            is MealMessage.LearnedPortion -> context.getString(R.string.learned_portion, m.name, m.unit, m.gramsPerUnit.grams())
        }
        val result = snackbar.showSnackbar(msg, actionLabel = if (state.canUndo) context.getString(R.string.undo) else null, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.meal.label()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = onAddByVoice) { Icon(Icons.Filled.Add, stringResource(R.string.add_food)) }
                    IconButton(onClick = onRepeatTomorrow) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.repeat_tomorrow)) }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MealType.entries.filter { it != state.meal }.forEach { m ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.move_to, m.label())) }, onClick = { menu = false; onMoveAll(m) })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val total = state.total
        val share = total.macroCalorieShare()
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.kcal_value, total.calories.kcal()), style = MaterialTheme.typography.headlineMedium)
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (share.carbs > 0) Surface(Modifier.weight(share.carbs.toFloat()).fillMaxSize(), color = MacroColors.carbs) {}
                            if (share.protein > 0) Surface(Modifier.weight(share.protein.toFloat()).fillMaxSize(), color = MacroColors.protein) {}
                            if (share.fat > 0) Surface(Modifier.weight(share.fat.toFloat()).fillMaxSize(), color = MacroColors.fat) {}
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.g_carbs, total.carbs.grams()), color = MacroColors.carbs)
                            Text(stringResource(R.string.g_protein, total.protein.grams()), color = MacroColors.protein)
                            Text(stringResource(R.string.g_fat, total.fat.grams()), color = MacroColors.fat)
                            Text(stringResource(R.string.g_fiber, total.fiber.grams()), color = MacroColors.fiber)
                        }
                        Text(
                            stringResource(R.string.also_line, total.sugar.grams(), total.sodiumMg.kcal()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AssistChip(onClick = onMicros, label = { Text(stringResource(R.string.vitamins_link)) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.items_tap_to_edit, state.items.size), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.items, key = { it.id }) { item ->
                OutlinedCard(onClick = { editing = item }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.quantity.qty()} ${unitLabel(item.unit)} · ${item.nutrients.shortMacros()}") },
                        trailingContent = { Text(item.nutrients.calories.kcal(), style = MaterialTheme.typography.titleMedium) },
                    )
                }
            }
            if (state.items.isEmpty()) item {
                Text(stringResource(R.string.nothing_logged_meal, state.meal.label().lowercase()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    editing?.let { item ->
        PortionEditorSheet(
            item = item, onDismiss = { editing = null },
            onSave = { q, u -> onUpdate(item, q, u); editing = null },
            onDelete = { onDelete(item.id); editing = null },
        )
    }
}
