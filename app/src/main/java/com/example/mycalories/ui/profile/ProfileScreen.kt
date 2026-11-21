package com.example.mycalories.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.ui.theme.MacroColors
import com.example.mycalories.R
import com.example.mycalories.ui.components.label
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

@Composable
fun ProfileRoute(viewModel: ProfileViewModel, contentPadding: PaddingValues, onSaved: () -> Unit, onSettings: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    ProfileScreen(
        state, contentPadding,
        onAge = viewModel::onAge, onHeight = viewModel::onHeight, onWeight = viewModel::onWeight,
        onSex = viewModel::onSex, onActivity = viewModel::onActivity, onGoal = viewModel::onGoal, onSave = viewModel::save,
        onSettings = onSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    contentPadding: PaddingValues,
    onAge: (String) -> Unit,
    onHeight: (String) -> Unit,
    onWeight: (String) -> Unit,
    onSex: (Sex) -> Unit,
    onActivity: (ActivityLevel) -> Unit,
    onGoal: (GoalType) -> Unit,
    onSave: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isEditing) R.string.profile_title_edit else R.string.profile_title_new)) },
            actions = {
                if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.profile_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(stringResource(R.string.age), state.age, onAge, state.ageError?.let { stringResource(R.string.error_age) }, stringResource(R.string.years), Modifier.weight(1f).testTag("age"), KeyboardType.Number)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sex), style = MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Sex.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = state.sex == s, onClick = { onSex(s) },
                                shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                            ) { Text(stringResource(if (s == Sex.MALE) R.string.male else R.string.female)) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(stringResource(R.string.height), state.height, onHeight, state.heightError?.let { stringResource(R.string.error_height) }, stringResource(R.string.cm), Modifier.weight(1f).testTag("height"))
                NumberField(stringResource(R.string.weight), state.weight, onWeight, state.weightError?.let { stringResource(R.string.error_weight) }, stringResource(R.string.kg), Modifier.weight(1f).testTag("weight"))
            }
            Text(stringResource(R.string.how_active), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLevel.entries.forEach { a ->
                    FilterChip(selected = state.activity == a, onClick = { onActivity(a) }, label = { Text(a.label()) })
                }
            }
            Text(stringResource(R.string.your_goal), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalType.entries.forEach { g ->
                    FilterChip(selected = state.goal == g, onClick = { onGoal(g) }, label = { Text(g.label()) })
                }
            }
            state.preview?.let { goal ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.daily_target), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.kcal_value, "%,d".format(goal.calories)), style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("goalPreview"))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.g_protein, goal.protein.toString()), color = MacroColors.protein)
                            Text(stringResource(R.string.g_carbs, goal.carbs.toString()), color = MacroColors.carbs)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.g_fat, goal.fat.toString()), color = MacroColors.fat)
                            Text(stringResource(R.string.g_fiber, goal.fiber.toString()), color = MacroColors.fiber)
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSave, enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("saveProfile"),
        ) { Text(stringResource(if (state.isEditing) R.string.save else R.string.continue_)) }
    }
}

@Composable
private fun NumberField(
    label: String, value: String, onChange: (String) -> Unit, error: String?, suffix: String,
    modifier: Modifier, type: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, suffix = { Text(suffix) },
        isError = error != null, supportingText = error?.let { { Text(it) } }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = modifier,
    )
}
