package com.example.mycalories

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.ThemeMode
import com.example.mycalories.ui.intro.IntroGate
import com.example.mycalories.ui.intro.IntroOverlay
import com.example.mycalories.ui.navigation.AppNavigation
import com.example.mycalories.ui.theme.MyCaloriesTheme

class MainActivity : AppCompatActivity() {

    /** What a notification, the widget or Health Connect asked to open. */
    private val openRequest = mutableStateOf<String?>(null)

    /** The splash stays until saved settings (theme, language) are read, so the first frame is already right. */
    @Volatile private var settingsReady = false

    /** Plays the katori animation each time you come back to the app (the system splash covers fresh starts). */
    private val introGate = IntroGate()
    private val showIntro = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val shownAt = android.os.SystemClock.uptimeMillis()
        // Never longer than 1.5 s, even if settings somehow fail to load.
        splash.setKeepOnScreenCondition { !settingsReady && android.os.SystemClock.uptimeMillis() - shownAt < 1500 }
        splash.setOnExitAnimationListener { view ->
            // Fade and gently grow the splash away instead of cutting to the app.
            view.view.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(260L)
                .withEndAction { view.remove() }.start()
        }
        enableEdgeToEdge()
        val container = (application as MyCaloriesApp).container
        handle(intent)
        setContent {
            // Null until the saved settings have loaded, so the language is only changed once, to the saved one.
            val settingsFlow = remember { container.settings.settings.map<AppSettings, AppSettings?> { it } }
            val loaded by settingsFlow.collectAsStateWithLifecycle(null)
            LaunchedEffect(loaded?.language) {
                loaded?.let {
                    applyLanguage(it.language)
                    settingsReady = true
                }
            }
            val settings = loaded ?: AppSettings()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            Box {
                MyCaloriesTheme(darkTheme = dark) {
                    AppNavigation(container, openRequest.value) { openRequest.value = null }
                }
                if (showIntro.value) IntroOverlay(onFinished = { showIntro.value = false })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (introGate.onStart()) showIntro.value = true
    }

    override fun onStop() {
        introGate.onStop(isChangingConfigurations)
        super.onStop()
    }

    // Every screen the app opens itself (camera, scanner, share, Health Connect) goes through one of these,
    // so coming back from it doesn't replay the intro.
    @Deprecated("Called by the Activity Result API")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        introGate.onOpeningOtherScreen()
        @Suppress("DEPRECATION") super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Called by the Activity Result API")
    override fun startIntentSenderForResult(
        intent: IntentSender, requestCode: Int, fillInIntent: Intent?, flagsMask: Int, flagsValues: Int,
        extraFlags: Int, options: Bundle?,
    ) {
        introGate.onOpeningOtherScreen()
        @Suppress("DEPRECATION")
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
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
        const val OPEN_LOG = "log"
        const val OPEN_ADD_WATER = "water"
        const val OPEN_PRIVACY = "privacy"
    }
}
