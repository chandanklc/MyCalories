package com.example.mycalories.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.grams
import com.example.mycalories.ui.components.kcal
import com.example.mycalories.ui.components.qty
import com.example.mycalories.ui.components.text
import com.example.mycalories.ui.components.unitLabel
import com.example.mycalories.ui.theme.MacroColors

@Composable
fun RecipeRoute(viewModel: RecipeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.stage) { if (state.stage == RecipeStage.Saved) onBack() }
    RecipeScreen(state, onBack, viewModel::submitText, viewModel::setName,
        viewModel::setServings, viewModel::removeIngredient, viewModel::save)
}

@Composable
fun RecipeScreen(
    state: RecipeUiState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onName: (String) -> Unit,
    onServings: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    BackScreen(stringResource(R.string.new_recipe_title), onBack) {
        Column(Modifier.weight(1f).imePadding()) {
            when (state.stage) {
                RecipeStage.Ready -> RecipeDetails(state, onName, onServings, onRemove)
                else -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.recipe_hint), style = MaterialTheme.typography.bodyLarge)
                    Text(state.transcript.ifBlank { stringResource(R.string.recipe_example) },
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("recipeTranscript"))
                    if (state.stage == RecipeStage.Processing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(R.string.working_out))
                    }
                    if (state.stage == RecipeStage.Error) Text(state.error?.text().orEmpty(), color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it }, modifier = Modifier.fillMaxWidth().testTag("recipeField"),
                        placeholder = { Text(stringResource(R.string.type_recipe)) }, minLines = 3,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.Center) {
            if (state.stage == RecipeStage.Ready) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("saveRecipe")) { Text(stringResource(R.string.save_recipe)) }
            } else if (state.stage != RecipeStage.Processing) {
                Button(
                    onClick = { if (typed.isNotBlank()) onSubmit(typed) }, enabled = typed.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sendRecipe"),
                ) { Text(stringResource(R.string.work_out_recipe)) }
            }
        }
    }
}

@Composable
private fun RecipeDetails(state: RecipeUiState, onName: (String) -> Unit, onServings: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val r = state.result
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(state.name, onName, label = { Text(stringResource(R.string.recipe_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
        itemsIndexed(state.ingredients) { i, item ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text("${item.quantity.qty()} ${unitLabel(item.unit)} · ${item.grams.grams()} g") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.nutrients.calories.kcal())
                            IconButton(onClick = { onRemove(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                        }
                    },
                )
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.makes), Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { onServings(state.servings - 1) }) { Icon(Icons.Filled.Remove, stringResource(R.string.fewer_servings)) }
                        Text(stringResource(R.string.servings_value, state.servings, unitLabel(state.servingUnit)),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp).testTag("servings"))
                        FilledTonalIconButton(onClick = { onServings(state.servings + 1) }) { Icon(Icons.Filled.Add, stringResource(R.string.more_servings)) }
                    }
                    if (r != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(stringResource(R.string.per_serving, unitLabel(state.servingUnit)), Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.kcal_value, r.perServing.calories.kcal()), style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.testTag("perServing"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.g_protein, r.perServing.protein.grams()), color = MacroColors.protein)
                            Text(stringResource(R.string.g_carbs, r.perServing.carbs.grams()), color = MacroColors.carbs)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.g_fat, r.perServing.fat.grams()), color = MacroColors.fat)
                            Text(stringResource(R.string.g_fiber, r.perServing.fiber.grams()), color = MacroColors.fiber)
                        }
                        Text(stringResource(R.string.whole_recipe) + ": " + stringResource(R.string.kcal_value, r.total.calories.kcal()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

