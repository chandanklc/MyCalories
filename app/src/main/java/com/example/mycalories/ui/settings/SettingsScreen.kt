package com.example.mycalories.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.R
import com.example.mycalories.domain.repository.HealthAvailability
import com.example.mycalories.domain.repository.ThemeMode
import com.example.mycalories.platform.HealthConnectSource
import com.example.mycalories.ui.components.BackScreen
import com.example.mycalories.ui.components.SectionLabel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit, onProfile: () -> Unit, onExport: () -> Unit, onPrivacy: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        viewModel.onHealthPermissionResult(granted.containsAll(HealthConnectSource.PERMISSIONS))
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    SettingsScreen(
        state, onBack,
        onMeal = { viewModel.setMealReminders(it); if (it) askNotifications() },
        onWater = { viewModel.setWaterReminders(it); if (it) askNotifications() },
        onHealth = {
            if (state.settings.healthConnect) viewModel.disconnectHealth()
            else if (state.healthAvailability == HealthAvailability.AVAILABLE) healthPermission.launch(HealthConnectSource.PERMISSIONS)
        },
        onTheme = viewModel::setTheme, onLanguage = viewModel::setLanguage,
        onProfile = onProfile, onExport = onExport, onPrivacy = onPrivacy,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onMeal: (Boolean) -> Unit,
    onWater: (Boolean) -> Unit,
    onHealth: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLanguage: (String) -> Unit,
    onProfile: () -> Unit,
    onExport: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val s = state.settings
    BackScreen(stringResource(R.string.settings_title), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionLabel(stringResource(R.string.reminders))
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.meal_reminders)) },
                    supportingContent = { Text(stringResource(R.string.meal_reminders_sub)) },
                    trailingContent = { Switch(s.mealReminders, onMeal, Modifier.testTag("mealSwitch")) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.water_reminders)) },
                    supportingContent = { Text(stringResource(R.string.water_reminders_sub)) },
                    trailingContent = { Switch(s.waterReminders, onWater, Modifier.testTag("waterSwitch")) },
                )
            }
            SectionLabel(stringResource(R.string.connected), Modifier.padding(top = 8.dp))
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.health_connect)) },
                    supportingContent = {
                        Text(stringResource(when {
                            s.healthConnect -> R.string.hc_connected
                            state.healthAvailability == HealthAvailability.NEEDS_UPDATE -> R.string.hc_update
                            state.healthAvailability == HealthAvailability.NOT_SUPPORTED -> R.string.hc_unavailable
                            else -> R.string.hc_connect
                        }))
                    },
                    trailingContent = {
                        Switch(s.healthConnect, { onHealth() }, enabled = s.healthConnect || state.healthAvailability == HealthAvailability.AVAILABLE)
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_auto_sub)) },
                )
            }
            SectionLabel(stringResource(R.string.appearance), Modifier.padding(top = 8.dp))
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.theme))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
                        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(s.theme == mode, { onTheme(mode) }, SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(stringResource(label)) }
                        }
                    }
                    Text(stringResource(R.string.language))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SegmentedButton(s.language == "en", { onLanguage("en") }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.english)) }
                        SegmentedButton(s.language == "hi", { onLanguage("hi") }, SegmentedButtonDefaults.itemShape(1, 2), Modifier.testTag("hindi")) { Text(stringResource(R.string.hindi)) }
                    }
                }
            }
            OutlinedCard(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
                Link(stringResource(R.string.edit_profile), onProfile)
                HorizontalDivider()
                Link(stringResource(R.string.export_data), onExport)
                HorizontalDivider()
                Link(stringResource(R.string.privacy_data), onPrivacy)
            }
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
