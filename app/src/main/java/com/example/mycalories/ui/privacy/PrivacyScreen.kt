package com.example.mycalories.ui.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrivacyRoute(viewModel: PrivacyViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.backup_saved)
    val restored = stringResource(R.string.restored_done)
    val errorText = state.error?.text()

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = viewModel.backupText()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            snackbar.showSnackbar(saved)
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            viewModel.restore(text.orEmpty())
        }
    }
    LaunchedEffect(state.restored, errorText) {
        when {
            state.restored -> { snackbar.showSnackbar(restored); viewModel.messageShown() }
            errorText != null -> { snackbar.showSnackbar(errorText); viewModel.messageShown() }
        }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    PrivacyScreen(
        state, onBack, snackbar,
        onPhotos = viewModel::setAllowPhotos, onTranscript = viewModel::setKeepTranscript,
        onSaveBackup = { saveBackup.launch("mycalories-backup.json") },
        onRestore = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onDelete = viewModel::requestDelete, onConfirmDelete = viewModel::confirmDelete, onCancelDelete = viewModel::cancelDelete,
    )
}

@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    onPhotos: (Boolean) -> Unit,
    onTranscript: (Boolean) -> Unit,
    onSaveBackup: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    BackScreen(stringResource(R.string.privacy_title), onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(stringResource(R.string.stays_on_phone), stringResource(R.string.stays_text))
            InfoCard(stringResource(R.string.sent_title), stringResource(R.string.sent_text), stringResource(R.string.server_note))
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text(stringResource(R.string.allow_photos)) },
                    trailingContent = { Switch(state.settings.allowPhotos, onPhotos) })
                HorizontalDivider()
                ListItem(headlineContent = { Text(stringResource(R.string.keep_transcript)) },
                    supportingContent = { Text(stringResource(R.string.keep_transcript_sub)) },
                    trailingContent = { Switch(state.settings.keepTranscript, onTranscript) })
            }
            OutlinedButton(onClick = onSaveBackup, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.download_backup)) }
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.restore_backup)) }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("deleteAll")) {
                Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.delete_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SnackbarHost(snackbar)
    }
    if (state.confirmingDelete) AlertDialog(
        onDismissRequest = onCancelDelete,
        title = { Text(stringResource(R.string.confirm_delete_title)) },
        text = { Text(stringResource(R.string.confirm_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("confirmDelete")) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InfoCard(title: String, text: String, note: String? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
