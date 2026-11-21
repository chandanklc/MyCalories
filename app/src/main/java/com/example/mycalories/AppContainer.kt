package com.example.mycalories

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.local.FoodSeeder
import com.example.mycalories.data.remote.AnthropicTransport
import com.example.mycalories.data.remote.AiAssistant
import com.example.mycalories.data.remote.AiConfig
import com.example.mycalories.data.remote.AiTransport
import com.example.mycalories.data.remote.GeminiTransport
import com.example.mycalories.data.remote.GroqTransport
import com.example.mycalories.data.remote.MissingKeyTransport
import com.example.mycalories.data.remote.AiFoodParser
import com.example.mycalories.data.remote.FoodParser
import com.example.mycalories.data.remote.OpenFoodFactsSource
import com.example.mycalories.data.remote.PackagedFoodSource
import com.example.mycalories.data.remote.UsdaNutritionSource
import com.example.mycalories.data.repository.DataStoreProfileRepository
import com.example.mycalories.data.repository.DataStoreSettingsRepository
import com.example.mycalories.data.repository.RoomDataManager
import com.example.mycalories.data.repository.RoomFoodRepository
import com.example.mycalories.data.repository.RoomLogRepository
import com.example.mycalories.data.repository.RoomPortionRepository
import com.example.mycalories.data.repository.RoomSavedMealRepository
import com.example.mycalories.data.repository.RoomWeightRepository
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.repository.DataManager
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.HealthDataSource
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.PortionRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.ReminderScheduler
import com.example.mycalories.domain.repository.SavedMealRepository
import com.example.mycalories.domain.repository.SettingsRepository
import com.example.mycalories.domain.repository.WeightRepository
import com.example.mycalories.platform.AndroidPdfMaker
import com.example.mycalories.platform.CaloriesWidget
import com.example.mycalories.platform.HealthConnectSource
import com.example.mycalories.platform.WorkManagerReminderScheduler
import com.example.mycalories.voice.AndroidVoiceInput
import com.example.mycalories.voice.VoiceInput
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private val Context.profileStore by preferencesDataStore(name = "profile")
private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Builds the app's shared objects once. Screens get their dependencies from here. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.create(app)
    // Start-up jobs must never crash the app; a failed one is simply skipped.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })

    val clock: Clock = Clock.systemDefaultZone()
    val foods: FoodRepository = RoomFoodRepository(db)
    val log: LogRepository = RoomLogRepository(db)
    val savedMeals: SavedMealRepository = RoomSavedMealRepository(db)
    val weights: WeightRepository = RoomWeightRepository(db)
    val portions: PortionRepository = RoomPortionRepository(db)
    val profiles: ProfileRepository = DataStoreProfileRepository(app.profileStore)
    private val settingsRepo = DataStoreSettingsRepository(app.settingsStore)
    val settings: SettingsRepository = settingsRepo
    val health: HealthDataSource = HealthConnectSource(app)
    val reminders: ReminderScheduler = WorkManagerReminderScheduler(app)
    val data: DataManager = RoomDataManager(db, profiles) { settingsRepo.clear() }
    val pdf = AndroidPdfMaker()

    private val http = HttpClient(OkHttp) {
        // AI replies can take a while, especially on the free tier.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 60_000
        }
    }
    private val ai: AiTransport = when (val c = AiConfig.choose(
        BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL,
        BuildConfig.ANTHROPIC_API_KEY,
        groqKey = BuildConfig.GROQ_API_KEY, groqModel = BuildConfig.GROQ_MODEL, groqVisionModel = BuildConfig.GROQ_VISION_MODEL,
    )) {
        is AiConfig.Groq -> GroqTransport(http, c.apiKey, c.model, AiConfig.FALLBACK_GROQ_MODEL, c.visionModel)
        is AiConfig.Gemini -> GeminiTransport(http, c.apiKey, c.model, AiConfig.FALLBACK_GEMINI_MODEL)
        is AiConfig.ClaudeDirect -> AnthropicTransport.direct(c.apiKey)
        AiConfig.None -> MissingKeyTransport
    }
    val parser: FoodParser = AiFoodParser(ai) { foods.allNames() }
    val assistant = AiAssistant(ai)
    val packagedFoods: PackagedFoodSource = OpenFoodFactsSource(http)
    val resolver = FoodResolver(foods, UsdaNutritionSource(http, BuildConfig.USDA_API_KEY), portions)

    /** Speech in Hindi when the app is in Hindi, Indian English otherwise. */
    fun voiceInput(): VoiceInput = AndroidVoiceInput(app) {
        if (java.util.Locale.getDefault().language == "hi") "hi-IN" else "en-IN"
    }

    init {
        scope.launch {
            FoodSeeder(db.foodDao()) { app.assets.open("foods_seed.json").bufferedReader().use { it.readText() } }.seedIfEmpty()
        }
        scope.launch { reminders.apply(settings.settings.first()) }
        // Keep the home-screen widget in step with today's log, and copy new meals to Health Connect.
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            log.observeDay(LocalDate.now(clock)).drop(1).debounce(500).collect { items ->
                runCatching { CaloriesWidget().updateAll(app) }
                if (settings.settings.first().healthConnect) {
                    val fresh = items.filter { it.loggedAtMillis > clock.millis() - 60_000 }
                    runCatching { health.writeMeals(fresh) }
                }
            }
        }
    }
}

class MyCaloriesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
