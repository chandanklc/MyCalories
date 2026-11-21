package com.example.mycalories.ui.logmeal

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.platform.BarcodeScanner
import com.example.mycalories.platform.PhotoFiles
import com.example.mycalories.ui.components.MealIcon
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

/** How the log screen starts: listening, taking a plate photo, or scanning a barcode. */
enum class LogStart { VOICE, PHOTO, BARCODE, TYPE }

@Composable
fun LogMealRoute(viewModel: LogMealViewModel, onClose: () -> Unit, start: LogStart = LogStart.VOICE, allowPhotos: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typing by rememberSaveable { mutableStateOf(start == LogStart.TYPE) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening() else typing = true
    }
    val listen = {
        typing = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startListening()
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) scope.launch { PhotoFiles.load(context, uri)?.let(viewModel::submitPhoto) }
        else if (state.stage == Stage.Idle && state.items.isEmpty()) onClose()
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
            else if (state.stage == Stage.Idle && state.items.isEmpty()) onClose()
        }
    }

    LaunchedEffect(Unit) {
        if (state.stage == Stage.Idle) when (start) {
            LogStart.VOICE -> listen()
            LogStart.PHOTO -> takePhoto()
            LogStart.BARCODE -> scan()
            LogStart.TYPE -> Unit
        }
    }
    LaunchedEffect(state.stage) { if (state.stage == Stage.Saved) onClose() }

    LogMealScreen(
        state = state, typing = typing,
        onListen = listen, onStop = viewModel::stopListening, onType = { viewModel.cancelListening(); typing = true },
        onSubmitText = { typing = false; viewModel.submitText(it) },
        onChooseMeal = viewModel::chooseMeal, onChangeMeal = viewModel::changeMeal,
        onUpdateItem = viewModel::updateItem, onRemoveItem = viewModel::removeItem,
        onFavorite = viewModel::setSaveAsFavorite, onFavoriteName = viewModel::setFavoriteName,
        onSave = viewModel::save, onRetry = { viewModel.reset(); listen() }, onClose = onClose,
        onPhoto = if (allowPhotos) takePhoto else null, onBarcode = scan, onLearnedShown = viewModel::learnedShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMealScreen(
    state: LogMealUiState,
    typing: Boolean,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onType: () -> Unit,
    onSubmitText: (String) -> Unit,
    onChooseMeal: (MealType, Boolean) -> Unit,
    onChangeMeal: (MealType) -> Unit,
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
) {
    val reviewing = state.stage == Stage.Review || (state.stage == Stage.Listening || state.stage == Stage.Processing) && state.items.isNotEmpty()
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
                    IconButton(onClick = onClose) {
                        Icon(if (reviewing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close, stringResource(R.string.close))
                    }
                },
                actions = { if (state.stage == Stage.Review) MealPicker(state.meal ?: state.suggestedMeal, onChangeMeal) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.stage) {
                    Stage.Review -> ReviewContent(state, onUpdateItem, onRemoveItem, onFavorite, onFavoriteName)
                    Stage.Error -> ErrorContent(state.error?.text().orEmpty(), onRetry, onType)
                    Stage.Processing -> ProcessingContent(state.transcript)
                    Stage.ChooseMeal -> ProcessingContent(state.transcript, working = false)
                    else -> ListeningContent(state.stage == Stage.Listening, state.transcript)
                }
            }
            BottomBar(state, typing, onListen, onStop, onType, onSubmitText, onSave, onPhoto, onBarcode)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    if (state.stage == Stage.ChooseMeal) ChooseMealSheet(state, onChooseMeal)
}

@Composable
private fun ListeningContent(listening: Boolean, transcript: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (listening) R.string.listening else R.string.tap_mic_hint),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
        Text(
            transcript.ifBlank { stringResource(R.string.transcript_example) },
            style = MaterialTheme.typography.headlineSmall,
            color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("transcript"),
        )
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.tip_amount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProcessingContent(transcript: String, working: Boolean = true) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (transcript.isNotBlank()) Text("\"$transcript\"", style = MaterialTheme.typography.headlineSmall)
        if (working) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.working_out), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onType: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("error"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onType) { Text(stringResource(R.string.type_instead)) }
            Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
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
                    placeholder = { Text(stringResource(R.string.favourite_placeholder, (state.meal ?: state.suggestedMeal).label().lowercase())) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseMealSheet(state: LogMealUiState, onChoose: (MealType, Boolean) -> Unit) {
    var remember by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { onChoose(state.suggestedMeal, false) }) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.which_meal), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.which_meal_hint, state.suggestedMeal.label().lowercase()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            MealType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { meal ->
                        val suggested = meal == state.suggestedMeal
                        Card(
                            onClick = { onChoose(meal, remember) },
                            modifier = Modifier.weight(1f).height(84.dp).testTag("choose_${meal.name}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (suggested) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MealIcon(meal)
                                Column {
                                    Text(meal.label(), style = MaterialTheme.typography.titleMedium)
                                    if (suggested) Text(stringResource(R.string.suggested), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            CheckboxRow(stringResource(R.string.always_pick), remember) { remember = it }
        }
    }
}

@Composable
private fun BottomBar(
    state: LogMealUiState,
    typing: Boolean,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onType: () -> Unit,
    onSubmitText: (String) -> Unit,
    onSave: () -> Unit,
    onPhoto: (() -> Unit)?,
    onBarcode: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (typing) {
            var text by rememberSaveable { mutableStateOf("") }
            val send = { if (text.isNotBlank()) { onSubmitText(text); text = "" } }
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.type_what_you_ate)) },
                modifier = Modifier.fillMaxWidth().testTag("typeField"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = { IconButton(onClick = send) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) } },
            )
        }
        when (state.stage) {
            Stage.Review -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onType, Modifier.weight(1f)) { Text(stringResource(R.string.type_more)) }
                    OutlinedButton(onClick = onListen, Modifier.weight(1f)) { Text(stringResource(R.string.say_more)) }
                }
                Button(
                    onClick = onSave, enabled = state.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("saveMeal"),
                ) { Text(stringResource(R.string.add_to_meal, state.total.calories.kcal(), (state.meal ?: state.suggestedMeal).label())) }
            }
            Stage.Idle, Stage.Listening -> Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onType) { Icon(Icons.Filled.Keyboard, stringResource(R.string.type_instead)) }
                if (onPhoto != null) FilledTonalIconButton(onClick = onPhoto) { Icon(Icons.Filled.CameraAlt, stringResource(R.string.log_by_photo)) }
                LargeFloatingActionButton(
                    onClick = if (state.stage == Stage.Listening) onStop else onListen,
                    modifier = Modifier.testTag("micButton"),
                ) {
                    if (state.stage == Stage.Listening) Icon(Icons.Filled.Check, stringResource(R.string.done_speaking), Modifier.size(36.dp))
                    else Icon(Icons.Filled.Mic, stringResource(R.string.start_speaking), Modifier.size(36.dp))
                }
                if (onBarcode != null) FilledTonalIconButton(onClick = onBarcode) { Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_barcode)) }
                Spacer(Modifier.size(8.dp))
            }
            else -> Unit
        }
    }
}
