package com.example.mycalories.ui.foods

import androidx.compose.ui.res.stringResource
import com.example.mycalories.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Mic
import com.example.mycalories.ui.components.label
import com.example.mycalories.ui.components.labelRes
import com.example.mycalories.ui.components.unitLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.ui.components.kcal
import com.example.mycalories.ui.components.qty

@Composable
fun FoodsRoute(viewModel: FoodsViewModel, contentPadding: PaddingValues, onNewRecipe: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FoodsScreen(state, contentPadding, viewModel::onQuery, viewModel::logSavedMeal, viewModel::logFood,
        viewModel::deleteSavedMeal, viewModel::messageShown, onNewRecipe)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodsScreen(
    state: FoodsUiState,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onLogSavedMeal: (SavedMeal) -> Unit,
    onLogFood: (Food, MealType) -> Unit,
    onDeleteSavedMeal: (Long) -> Unit,
    onMessageShown: () -> Unit,
    onNewRecipe: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        when (val m = state.message) {
            is FoodsMessage.Added -> {
                snackbar.showSnackbar(context.getString(R.string.added_to, m.name, context.getString(m.meal.labelRes)))
                onMessageShown()
            }
            null -> Unit
        }
    }
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.my_foods)) })
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text(stringResource(R.string.search_foods)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.query.isBlank()) item {
                    OutlinedButton(onClick = onNewRecipe, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Mic, null)
                        Text("  " + stringResource(R.string.new_recipe))
                    }
                }
                if (state.savedMeals.isNotEmpty() && state.query.isBlank()) {
                    item { Header(stringResource(R.string.saved_meals_header)) }
                    items(state.savedMeals, key = { "m${it.id}" }) { meal ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text(meal.name) },
                                supportingContent = { Text(meal.items.joinToString { it.name } + " · " + stringResource(R.string.kcal_value, meal.total.calories.kcal()), maxLines = 2) },
                                trailingContent = {
                                    androidx.compose.foundation.layout.Row {
                                        IconButton(onClick = { onDeleteSavedMeal(meal.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_x, meal.name)) }
                                        FilledIconButton(onClick = { onLogSavedMeal(meal) }) { Icon(Icons.Filled.Add, stringResource(R.string.log_x, meal.name)) }
                                    }
                                },
                            )
                        }
                    }
                }
                item { Header(stringResource(if (state.query.isBlank()) R.string.library_header else R.string.results)) }
                items(state.foods, key = { "f${it.id}" }) { food -> FoodRow(food, onLogFood) }
                if (state.foods.isEmpty()) item {
                    Text(stringResource(R.string.no_foods), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FoodRow(food: Food, onLog: (Food, MealType) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val serving = food.per100g.forGrams(food.gramsPerUnit)
    OutlinedCard(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(food.name) },
            supportingContent = { Text(stringResource(R.string.serving_line, unitLabel(food.defaultUnit), food.gramsPerUnit.qty(), food.source.label())) },
            trailingContent = {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(serving.calories.kcal(), style = MaterialTheme.typography.titleMedium)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.log_x, food.name)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MealType.entries.forEach { m ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.add_to, m.label())) }, onClick = { menu = false; onLog(food, m) })
                            }
                        }
                    }
                }
            },
        )
    }
}
