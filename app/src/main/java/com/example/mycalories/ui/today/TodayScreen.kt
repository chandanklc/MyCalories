package com.example.mycalories.ui.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.domain.MealSummary
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.ui.components.CalorieRing
import com.example.mycalories.ui.components.MealRingKey
import com.example.mycalories.ui.components.ringSegments
import com.example.mycalories.ui.components.MacroRow
import com.example.mycalories.ui.components.MealIcon
import com.example.mycalories.ui.components.kcal
import com.example.mycalories.ui.components.label
import com.example.mycalories.ui.components.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun TodayRoute(
    viewModel: TodayViewModel,
    contentPadding: PaddingValues,
    onNeedsProfile: () -> Unit,
    onOpenMeal: (LocalDate, MealType) -> Unit,
    onAddToMeal: (LocalDate) -> Unit,
    onAsk: () -> Unit,
    onMicros: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.needsProfile, state.loading) {
        if (!state.loading && state.needsProfile) onNeedsProfile()
    }
    TodayScreen(
        state = state, contentPadding = contentPadding,
        onPrevious = viewModel::previousDay, onNext = viewModel::nextDay, onToday = viewModel::goToToday,
        onAddWater = viewModel::addWater, onRemoveWater = viewModel::removeWater,
        onOpenMeal = { onOpenMeal(state.date, it) }, onAddToMeal = { onAddToMeal(state.date) },
        onCopy = viewModel::copyFromYesterday, onMessageShown = viewModel::messageShown,
        onAsk = onAsk, onMicros = onMicros,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    contentPadding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAddWater: () -> Unit,
    onRemoveWater: () -> Unit,
    onOpenMeal: (MealType) -> Unit,
    onAddToMeal: () -> Unit,
    onCopy: (MealType?) -> Unit = {},
    onMessageShown: () -> Unit = {},
    onAsk: () -> Unit = {},
    onMicros: () -> Unit = {},
) {
    val summary = state.summary
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val text = when (val m = state.message) {
            null -> return@LaunchedEffect
            TodayMessage.CopiedDay -> context.getString(R.string.copied_day)
            is TodayMessage.CopiedMeal -> context.getString(R.string.copied_meal, context.getString(m.meal.labelRes).lowercase())
        }
        snackbar.showSnackbar(text)
        onMessageShown()
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.isToday) stringResource(R.string.today) else state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")))
                        if (state.isToday) Text(
                            state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAsk) { Icon(Icons.Filled.ChatBubbleOutline, stringResource(R.string.ask_about_food)) }
                    IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_day)) }
                    if (!state.isToday) {
                        TextButton(onClick = onToday) { Text(stringResource(R.string.today)) }
                        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day)) }
                    }
                },
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryCard(state, onAddWater, onRemoveWater) }
                item {
                    MacroRow(summary.eaten, summary.goal.protein, summary.goal.carbs, summary.goal.fat, summary.goal.fiber)
                }
                if (state.yesterdayMeals.isNotEmpty()) item { YesterdayChips(state.yesterdayMeals, onCopy) }
                items(summary.meals, key = { it.meal.name }) { meal ->
                    MealCard(meal, onOpen = { onOpenMeal(meal.meal) }, onAdd = onAddToMeal)
                }
                item {
                    AssistChip(
                        onClick = onMicros, label = { Text(stringResource(R.string.vitamins_link)) },
                        leadingIcon = { Icon(Icons.Filled.Science, null) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun SummaryCard(state: TodayUiState, onAddWater: () -> Unit, onRemoveWater: () -> Unit) {
    val summary = state.summary
    OutlinedCard(Modifier.fillMaxWidth()) {
        val byMeal = summary.meals.associate { it.meal to it.total.calories }
        val segments = ringSegments(byMeal, (summary.goal.calories + state.burnedCalories).toDouble())
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(segments, state.remainingCalories, Modifier.testTag("calorieRing"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow(stringResource(R.string.goal), "%,d".format(summary.goal.calories))
                StatRow(stringResource(R.string.eaten), summary.eaten.calories.kcal())
                if (state.burnedCalories > 0) StatRow(stringResource(R.string.burned), "+%,d".format(state.burnedCalories))
                state.steps?.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.steps_line, "%,d".format(it)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(" " + stringResource(R.string.water_glasses, state.waterGlasses), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = onRemoveWater) { Icon(Icons.Filled.Remove, stringResource(R.string.remove_water)) }
                    IconButton(onClick = onAddWater) { Icon(Icons.Filled.Add, stringResource(R.string.add_water)) }
                }
            }
        }
        if (segments.isNotEmpty()) {
            MealRingKey(segments, byMeal, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun YesterdayChips(meals: Map<MealType, Double>, onCopy: (MealType?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.filter { it in meals }.forEach { m ->
                SuggestionChip(
                    onClick = { onCopy(m) },
                    label = { Text(stringResource(R.string.same_meal, m.label().lowercase(), meals.getValue(m).roundToInt())) },
                )
            }
            AssistChip(
                onClick = { onCopy(null) }, label = { Text(stringResource(R.string.copy_whole_day)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, modifier = Modifier.testTag("copyDay"),
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun MealCard(meal: MealSummary, onOpen: () -> Unit, onAdd: () -> Unit) {
    val empty = meal.items.isEmpty()
    val label = meal.meal.label()
    Card(
        onClick = if (empty) onAdd else onOpen,
        modifier = Modifier.fillMaxWidth().testTag("meal_${meal.meal.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (empty) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { MealIcon(meal.meal) },
            headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(if (empty) stringResource(R.string.not_logged_yet) else meal.description, maxLines = 1) },
            trailingContent = {
                if (empty) Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_meal, label))
                else Text(meal.total.calories.roundToInt().toString(), style = MaterialTheme.typography.titleMedium)
            },
        )
    }
}
