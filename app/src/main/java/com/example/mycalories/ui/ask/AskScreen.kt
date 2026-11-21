package com.example.mycalories.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.data.remote.ChatMessage
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.text

@Composable
fun AskRoute(viewModel: AskViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AskScreen(state, onBack, viewModel::send)
}

@Composable
fun AskScreen(state: AskUiState, onBack: () -> Unit, onSend: (String) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.sending) { if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size) }
    BackScreen(stringResource(R.string.ask_title), onBack, subtitle = stringResource(R.string.ask_subtitle)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.messages) { m -> Bubble(m) }
            item {
                when {
                    state.sending -> Text(stringResource(R.string.thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error != null -> Text(state.error.text(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        val suggestions = listOf(R.string.suggestion_dinner, R.string.suggestion_over, R.string.suggestion_snack).map { stringResource(it) }
        if (state.messages.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { q -> SuggestionChip(onClick = { onSend(q) }, label = { Text(q) }) }
        }
        var text by rememberSaveable { mutableStateOf("") }
        val send = { if (text.isNotBlank()) { onSend(text); text = "" } }
        OutlinedTextField(
            value = text, onValueChange = { text = it }, placeholder = { Text(stringResource(R.string.ask_placeholder)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().imePadding().testTag("askField"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
            trailingIcon = { IconButton(onClick = send, enabled = !state.sending) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) } },
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val mine = m.role == ChatMessage.Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text,
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
