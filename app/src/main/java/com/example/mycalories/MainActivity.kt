package com.example.mycalories

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.ThemeMode
import com.example.mycalories.ui.navigation.AppNavigation
import com.example.mycalories.ui.theme.MyCaloriesTheme

class MainActivity : AppCompatActivity() {

    /** What a notification, the widget or Health Connect asked to open. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MyCaloriesApp).container
        handle(intent)
        setContent {
            // Null until the saved settings have loaded, so the language is only changed once, to the saved one.
            val settingsFlow = remember { container.settings.settings.map<AppSettings, AppSettings?> { it } }
            val loaded by settingsFlow.collectAsStateWithLifecycle(null)
            LaunchedEffect(loaded?.language) { loaded?.let { applyLanguage(it.language) } }
            val settings = loaded ?: AppSettings()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MyCaloriesTheme(darkTheme = dark) {
                AppNavigation(container, openRequest.value) { openRequest.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        openRequest.value = when {
            intent == null -> null
            intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
                intent.action == "android.intent.action.VIEW_PERMISSION_USAGE" -> OPEN_PRIVACY
            else -> intent.getStringExtra(EXTRA_OPEN)
        }
    }

    private fun applyLanguage(tag: String) {
        val wanted = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_VOICE = "voice"
        const val OPEN_ADD_WATER = "water"
        const val OPEN_PRIVACY = "privacy"
    }
}
