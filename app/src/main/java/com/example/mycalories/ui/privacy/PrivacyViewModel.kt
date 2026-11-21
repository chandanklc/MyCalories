package com.example.mycalories.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.domain.BackupSerializer
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.DataManager
import com.example.mycalories.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrivacyUiState(
    val settings: AppSettings = AppSettings(),
    val confirmingDelete: Boolean = false,
    val deleted: Boolean = false,
    val restored: Boolean = false,
    val error: AppError? = null,
)

class PrivacyViewModel(private val data: DataManager, private val settings: SettingsRepository) : ViewModel() {

    private val local = MutableStateFlow(PrivacyUiState())

    val state: StateFlow<PrivacyUiState> = combine(settings.settings, local) { s, l -> l.copy(settings = s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrivacyUiState())

    fun setAllowPhotos(on: Boolean) { viewModelScope.launch { settings.update { it.copy(allowPhotos = on) } } }
    fun setKeepTranscript(on: Boolean) { viewModelScope.launch { settings.update { it.copy(keepTranscript = on) } } }

    fun requestDelete() { local.value = local.value.copy(confirmingDelete = true) }
    fun cancelDelete() { local.value = local.value.copy(confirmingDelete = false) }

    fun confirmDelete() {
        viewModelScope.launch {
            data.deleteEverything()
            local.value = local.value.copy(confirmingDelete = false, deleted = true)
        }
    }

    /** The whole diary as a backup file the user can save to Google Drive or anywhere. */
    suspend fun backupText(): String = BackupSerializer.encode(data.export())

    fun restore(text: String) {
        viewModelScope.launch {
            val backup = runCatching { BackupSerializer.decode(text) }.getOrNull()
            local.value = if (backup == null) {
                local.value.copy(error = AppError.INVALID_BACKUP, restored = false)
            } else {
                data.restore(backup)
                local.value.copy(restored = true, error = null)
            }
        }
    }

    fun messageShown() { local.value = local.value.copy(restored = false, error = null, deleted = false) }
}
