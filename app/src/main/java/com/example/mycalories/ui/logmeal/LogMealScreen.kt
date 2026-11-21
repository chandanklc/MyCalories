package com.example.mycalories.ui.logmeal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.platform.BarcodeScanner
import com.example.mycalories.platform.PhotoFiles
import com.example.mycalories.ui.components.MealColors
import com.example.mycalories.ui.components.PortionEditorSheet
import com.example.mycalories.ui.components.SourceChip
import com.example.mycalories.ui.components.grams
import com.example.mycalories.ui.components.kcal
import com.example.mycalories.ui.components.label
import com.example.mycalories.ui.components.qty
import com.example.mycalories.ui.components.shortMacros
import com.example.mycalories.ui.components.text
import com.example.mycalories.ui.components.unitLabel
import com.example.mycalories.ui.theme.MacroColors
import kotlinx.coroutines.launch

/** How the log screen opens: typing (the default), taking a plate photo, or scanning a barcode. */
enum class LogStart { TYPE, PHOTO, BARCODE }

@Composable
fun LogMealRoute(viewModel: LogMealViewModel, onClose: () -> Unit, start: LogStart = LogStart.TYPE, allowPhotos: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val nothingYet = { state.stage == Stage.Search && state.items.isEmpty() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) scope.launch { PhotoFiles.load(context, uri)?.let(viewModel::submitPhoto) }
        else if (nothingYet() && start == LogStart.PHOTO) onClose()
    }
    val takePhoto = {
        val uri = PhotoFiles.newPhotoUri(context)
        photoUri = uri
        camera.launch(uri)
    }
    val scan: () -> Unit = {
        scope.launch {
            val code = BarcodeScanner.scan(context)
            if (code != null) viewModel.submitBarcode(code)
            else if (nothingYet() && start == LogStart.BARCODE) onClose()
        }
    }

    LaunchedEffect(Unit) {
        if (nothingYet()) when (start) {
            LogStart.PHOTO -> takePhoto()
            LogStart.BARCODE -> scan()
            LogStart.TYPE -> Unit
        }
    }
    LaunchedEffect(state.stage) { if (state.stage == Stage.Saved) onClose() }

    LogMealScreen(
        state = state,
        onQuery = viewModel::setQuery, onAddSuggestion = viewModel::addSuggestion, onFindWithAi = viewModel::findWithAi,
        onMeal = viewModel::setMeal, onReview = viewModel::review, onAddMore = viewModel::addMore,
        onUpdateItem = viewModel::updateItem, onRemoveItem = viewModel::removeItem,
        onFavorite = viewModel::setSaveAsFavorite, onFavoriteName = viewModel::setFavoriteName,
        onSave = viewModel::save, onRetry = viewModel::retry, onClose = onClose,
        onPhoto = if (allowPhotos) takePhoto else null, onBarcode = scan, onLearnedShown = viewModel::learnedShown,
        autoFocus = start == LogStart.TYPE,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMealScreen(
    state: LogMealUiState,
    onQuery: (String) -> Unit,
    onAddSuggestion: (FoodSuggestion) -> Unit,
    onFindWithAi: () -> Unit,
    onMeal: (MealType) -> Unit,
    onReview: () -> Unit,
    onAddMore: () -> Unit,
    onUpdateItem: (Int, Double, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onFavorite: (Boolean) -> Unit,
    onFavoriteName: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onPhoto: (() -> Unit)? = null,
    onBarcode: (() -> Unit)? = null,
    onLearnedShown: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val reviewing = state.stage == Stage.Review
    val snackbar = remember { SnackbarHostState() }
    val learnedText = state.learned?.let {
        stringResource(R.string.learned_portion, it.foodName, unitLabel(it.unit), it.gramsPerUnit.grams())
    }
    LaunchedEffect(learnedText) {
        if (learnedText != null) {
            snackbar.showSnackbar(learnedText)
            onLearnedShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = { Text(stringResource(if (reviewing) R.string.check_before_saving else R.string.log_meal_title)) },
                navigationIcon = {
                    IconButton(onClick = if (reviewing) onAddMore else onClose) {
                        Icon(if (reviewing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close, stringResource(R.string.close))
                    }
                },
                actions = { if (reviewing) MealPicker(state.meal, onMeal) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.stage) {
                    Stage.Review -> ReviewContent(state, onUpdateItem, onRemoveItem, onFavorite, onFavoriteName)
                    Stage.Error -> ErrorContent(state.error?.text().orEmpty(), onRetry)
                    Stage.Processing -> ProcessingContent(state.transcript)
                    else -> SearchContent(state, onQuery, onAddSuggestion, onFindWithAi, onMeal, onPhoto, onBarcode, autoFocus)
                }
            }
            when (state.stage) {
                Stage.Review -> Column(
                    Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onAddMore, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_more_typing))
                    }
                    Button(
                        onClick = onSave, enabled = state.items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("saveMeal"),
                    ) { Text(stringResource(R.string.add_to_meal, state.total.calories.kcal(), state.meal.label())) }
                }
                Stage.Search -> if (state.items.isNotEmpty()) Tray(state, onReview)
                else -> Unit
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

@Composable
private fun SearchContent(
    state: LogMealUiState,
    onQuery: (String) -> Unit,
    onAdd: (FoodSuggestion) -> Unit,
    onFindWithAi: () -> Unit,
    onMeal: (MealType) -> Unit,
    onPhoto: (() -> Unit)?,
    onBarcode: (() -> Unit)?,
    autoFocus: Boolean,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    val typed = state.query.isNotBlank()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.what_did_you_eat)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (typed) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_search)) }
                },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (typed) onFindWithAi() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("searchField"),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MealType.entries.forEach { m ->
                    FilterChip(
                        selected = m == state.meal, onClick = { onMeal(m) }, label = { Text(m.label()) },
                        leadingIcon = { Surface(Modifier.size(10.dp), shape = RoundedCornerShape(5.dp), color = MealColors.of(m)) {} },
                        modifier = Modifier.testTag("mealChip_${m.name}"),
                    )
                }
                if (onPhoto != null) FilledTonalIconButton(onClick = onPhoto) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.log_by_photo)) }
                if (onBarcode != null) FilledTonalIconButton(onClick = onBarcode) { Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_barcode)) }
            }
        }
        if (typed) item {
            Card(
                onClick = onFindWithAi,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().testTag("findWithAi"),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.find_calories_for, state.query.trim()), style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.find_calories_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item {
                Text(stringResource(if (typed) R.string.from_your_foods else R.string.your_foods),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.suggestions, key = { it.food.id }) { s -> SuggestionRow(s, onAdd) }
        } else if (typed) item {
            Text(stringResource(R.string.no_food_matches), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!typed) item {
            Text(stringResource(R.string.type_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestionRow(s: FoodSuggestion, onAdd: (FoodSuggestion) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.testTag("suggestion_${s.food.name}").semantics(mergeDescendants = true) {},
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(s.food.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(stringResource(R.string.suggestion_line, s.quantity.qty(), unitLabel(s.food.defaultUnit), s.grams.grams())) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.kcal_value, s.calories.kcal()), style = MaterialTheme.typography.titleSmall)
                    FilledIconButton(onClick = { onAdd(s) }) { Icon(Icons.Filled.Add, stringResource(R.string.add_named_food, s.food.name)) }
                }
            },
        )
    }
}

@Composable
private fun Tray(state: LogMealUiState, onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().testTag("tray").semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.meal_so_far, state.meal.label()), style = MaterialTheme.typography.labelMedium)
                Text(
                    pluralStringResource(R.plurals.items_kcal, state.items.size, state.items.size, state.total.calories.kcal()),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onReview) { Text(stringResource(R.string.review)) }
        }
    }
}

@Composable
private fun ProcessingContent(transcript: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (transcript.isNotBlank()) Text("\"$transcript\"", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.working_out), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("error"))
        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
private fun ReviewContent(
    state: LogMealUiState,
    onUpdateItem: (Int, Double, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onFavorite: (Boolean) -> Unit,
    onFavoriteName: (String) -> Unit,
) {
    var editing by remember { mutableIntStateOf(-1) }
    val total = state.total
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.this_meal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.kcal_value, total.calories.kcal()), style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("mealTotal"))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.g_protein, total.protein.grams()), color = MacroColors.protein, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.g_carbs, total.carbs.grams()), color = MacroColors.carbs, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.g_fat, total.fat.grams()), color = MacroColors.fat, style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.g_fiber, total.fiber.grams()), color = MacroColors.fiber, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        itemsIndexed(state.items) { index, item ->
            Card(
                onClick = { editing = index },
                colors = CardDefaults.cardColors(
                    containerColor = if (item.needsReview) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth().testTag("reviewItem"),
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(item.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.item_line, item.quantity.qty(), unitLabel(item.unit), item.grams.grams(), item.nutrients.shortMacros()))
                            SourceChip(item.source, item.needsReview)
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.nutrients.calories.kcal(), style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Filled.Edit, stringResource(R.string.edit_item, item.name), Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
        }
        item {
            CheckboxRow(stringResource(R.string.save_favourite), state.saveAsFavorite, onFavorite)
            if (state.saveAsFavorite) {
                OutlinedTextField(
                    value = state.favoriteName, onValueChange = onFavoriteName,
                    label = { Text(stringResource(R.string.favourite_name)) },
                    placeholder = { Text(stringResource(R.string.favourite_placeholder, state.meal.label().lowercase())) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    state.items.getOrNull(editing)?.let { item ->
        PortionEditorSheet(
            item = item,
            onDismiss = { editing = -1 },
            onSave = { q, u -> onUpdateItem(editing, q, u); editing = -1 },
            onDelete = { onRemoveItem(editing); editing = -1 },
        )
    }
}

/** Checkbox whose whole row, label included, is one 48dp touch target. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Text(label)
    }
}

@Composable
private fun MealPicker(selected: MealType, onPick: (MealType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, modifier = Modifier.padding(end = 8.dp)) {
            Text(selected.label())
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.change_meal))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MealType.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label()) }, onClick = { onPick(m); open = false },
                    leadingIcon = { if (m == selected) Icon(Icons.Filled.Check, null) })
            }
        }
    }
}

