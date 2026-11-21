package com.example.mycalories.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.mycalories.AppContainer
import com.example.mycalories.MainActivity
import com.example.mycalories.R
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.ui.ask.AskRoute
import com.example.mycalories.ui.ask.AskViewModel
import com.example.mycalories.ui.export.ExportRoute
import com.example.mycalories.ui.export.ExportViewModel
import com.example.mycalories.ui.foods.FoodsRoute
import com.example.mycalories.ui.foods.FoodsViewModel
import com.example.mycalories.ui.history.HistoryRoute
import com.example.mycalories.ui.history.HistoryViewModel
import com.example.mycalories.ui.logmeal.LogMealRoute
import com.example.mycalories.ui.logmeal.LogMealViewModel
import com.example.mycalories.ui.logmeal.LogStart
import com.example.mycalories.ui.meal.MealDetailRoute
import com.example.mycalories.ui.meal.MealDetailViewModel
import com.example.mycalories.ui.micros.MicrosRoute
import com.example.mycalories.ui.micros.MicrosViewModel
import com.example.mycalories.ui.privacy.PrivacyRoute
import com.example.mycalories.ui.privacy.PrivacyViewModel
import com.example.mycalories.ui.profile.ProfileRoute
import com.example.mycalories.ui.profile.ProfileViewModel
import com.example.mycalories.ui.recipe.RecipeRoute
import com.example.mycalories.ui.recipe.RecipeViewModel
import com.example.mycalories.ui.settings.SettingsRoute
import com.example.mycalories.ui.settings.SettingsViewModel
import com.example.mycalories.ui.today.TodayRoute
import com.example.mycalories.ui.today.TodayViewModel
import com.example.mycalories.ui.weekly.WeeklySummaryRoute
import com.example.mycalories.ui.weekly.WeeklySummaryViewModel
import com.example.mycalories.ui.weight.WeightRoute
import com.example.mycalories.ui.weight.WeightViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.reflect.KClass

@Serializable object TodayRoute
@Serializable object HistoryRoute
@Serializable object FoodsRoute
@Serializable data class ProfileRoute(val firstRun: Boolean = false)
@Serializable data class LogMealRoute(val epochDay: Long, val start: String = LogStart.VOICE.name)
@Serializable data class MealDetailRoute(val epochDay: Long, val meal: String)
@Serializable object RecipeRoute
@Serializable object WeightRoute
@Serializable object WeeklyRoute
@Serializable object AskRoute
@Serializable object MicrosRoute
@Serializable object SettingsRoute
@Serializable object ExportRoute
@Serializable object PrivacyRoute

private data class Tab(val route: Any, val type: KClass<*>, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, TodayRoute::class, R.string.nav_today, Icons.Filled.Today),
    Tab(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.Filled.BarChart),
    Tab(FoodsRoute, FoodsRoute::class, R.string.nav_foods, Icons.Filled.Restaurant),
    Tab(ProfileRoute(), ProfileRoute::class, R.string.nav_profile, Icons.Filled.Person),
)

@Composable
fun AppNavigation(container: AppContainer, openRequest: String? = null, onOpenHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val onTab = tabs.any { t -> destination?.hasRoute(t.type) == true } &&
        !(destination?.hasRoute(ProfileRoute::class) == true && entry?.toRoute<ProfileRoute>()?.firstRun == true)
    fun today() = LocalDate.now(container.clock).toEpochDay()

    // Notifications, the widget and Health Connect can ask to open a screen.
    LaunchedEffect(openRequest) {
        when (openRequest) {
            MainActivity.OPEN_VOICE -> nav.navigate(LogMealRoute(today()))
            MainActivity.OPEN_PRIVACY -> nav.navigate(PrivacyRoute)
            MainActivity.OPEN_ADD_WATER -> scope.launch {
                val d = LocalDate.now(container.clock)
                container.log.setWater(d, container.log.observeWater(d).first() + 1)
            }
            else -> return@LaunchedEffect
        }
        onOpenHandled()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (onTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(t.type) == true,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (destination?.hasRoute(TodayRoute::class) == true) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { nav.navigate(LogMealRoute(today(), LogStart.BARCODE.name)) }) {
                        Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_barcode))
                    }
                    if (settings.allowPhotos) SmallFloatingActionButton(onClick = { nav.navigate(LogMealRoute(today(), LogStart.PHOTO.name)) }) {
                        Icon(Icons.Filled.CameraAlt, stringResource(R.string.log_by_photo))
                    }
                    LargeFloatingActionButton(
                        onClick = { nav.navigate(LogMealRoute(today())) },
                        modifier = Modifier.testTag("fabMic"),
                    ) { Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.speak_what_you_ate)) }
                }
            }
        },
    ) { scaffoldPadding ->
        // Each screen's TopAppBar handles the status bar itself, so only pass the bottom bar's space down.
        val padding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding())
        NavHost(nav, startDestination = TodayRoute) {
            composable<TodayRoute> {
                val vm: TodayViewModel = viewModel(factory = viewModelFactory {
                    initializer { TodayViewModel(container.log, container.profiles, container.clock, container.health, container.settings) }
                })
                TodayRoute(
                    vm, padding,
                    onNeedsProfile = { nav.navigate(ProfileRoute(firstRun = true)) { launchSingleTop = true } },
                    onOpenMeal = { date, meal -> nav.navigate(MealDetailRoute(date.toEpochDay(), meal.name)) },
                    onAddToMeal = { date -> nav.navigate(LogMealRoute(date.toEpochDay())) },
                    onAsk = { nav.navigate(AskRoute) },
                    onMicros = { nav.navigate(MicrosRoute) },
                )
            }
            composable<HistoryRoute> {
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory {
                    initializer { HistoryViewModel(container.log, container.profiles, container.clock) }
                })
                HistoryRoute(vm, padding, onWeight = { nav.navigate(WeightRoute) }, onWeekly = { nav.navigate(WeeklyRoute) })
            }
            composable<FoodsRoute> {
                val vm: FoodsViewModel = viewModel(factory = viewModelFactory {
                    initializer { FoodsViewModel(container.foods, container.savedMeals, container.log, container.clock) }
                })
                FoodsRoute(vm, padding, onNewRecipe = { nav.navigate(RecipeRoute) })
            }
            composable<ProfileRoute> { back ->
                val route = back.toRoute<ProfileRoute>()
                val vm: ProfileViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProfileViewModel(container.profiles) }
                })
                ProfileRoute(
                    vm, if (route.firstRun) PaddingValues() else padding,
                    onSaved = { if (route.firstRun) nav.popBackStack() },
                    onSettings = if (route.firstRun) null else ({ nav.navigate(SettingsRoute) }),
                )
            }
            composable<LogMealRoute> { back ->
                val route = back.toRoute<LogMealRoute>()
                val vm: LogMealViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        LogMealViewModel(
                            container.voiceInput(), container.parser, container.resolver, container.log,
                            container.profiles, container.savedMeals, container.clock, LocalDate.ofEpochDay(route.epochDay),
                            container.packagedFoods, container.settings, container.portions,
                        )
                    }
                })
                LogMealRoute(vm, onClose = { nav.popBackStack() }, start = LogStart.valueOf(route.start), allowPhotos = settings.allowPhotos)
            }
            composable<MealDetailRoute> { back ->
                val route = back.toRoute<MealDetailRoute>()
                val vm: MealDetailViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        MealDetailViewModel(container.log, LocalDate.ofEpochDay(route.epochDay),
                            MealType.fromString(route.meal) ?: MealType.LUNCH, container.clock, container.portions)
                    }
                })
                MealDetailRoute(vm, onBack = { nav.popBackStack() },
                    onAddByVoice = { nav.navigate(LogMealRoute(route.epochDay)) }, onMicros = { nav.navigate(MicrosRoute) })
            }
            composable<RecipeRoute> {
                val vm: RecipeViewModel = viewModel(factory = viewModelFactory {
                    initializer { RecipeViewModel(container.voiceInput(), container.parser, container.resolver, container.foods, container.clock) }
                })
                RecipeRoute(vm, onBack = { nav.popBackStack() })
            }
            composable<WeightRoute> {
                val vm: WeightViewModel = viewModel(factory = viewModelFactory {
                    initializer { WeightViewModel(container.weights, container.profiles, container.clock, container.health, container.settings) }
                })
                WeightRoute(vm, onBack = { nav.popBackStack() })
            }
            composable<WeeklyRoute> {
                val vm: WeeklySummaryViewModel = viewModel(factory = viewModelFactory {
                    initializer { WeeklySummaryViewModel(container.log, container.weights, container.profiles, container.clock) }
                })
                WeeklySummaryRoute(vm, onBack = { nav.popBackStack() }, onAsk = { nav.navigate(AskRoute) })
            }
            composable<AskRoute> {
                val vm: AskViewModel = viewModel(factory = viewModelFactory {
                    initializer { AskViewModel(container.assistant, container.log, container.profiles, container.clock) }
                })
                AskRoute(vm, onBack = { nav.popBackStack() })
            }
            composable<MicrosRoute> {
                val vm: MicrosViewModel = viewModel(factory = viewModelFactory {
                    initializer { MicrosViewModel(container.log, container.profiles, container.clock) }
                })
                MicrosRoute(vm, onBack = { nav.popBackStack() })
            }
            composable<SettingsRoute> {
                val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settings, container.health, container.reminders) }
                })
                SettingsRoute(vm, onBack = { nav.popBackStack() }, onProfile = { nav.popBackStack() },
                    onExport = { nav.navigate(ExportRoute) }, onPrivacy = { nav.navigate(PrivacyRoute) })
            }
            composable<ExportRoute> {
                val vm: ExportViewModel = viewModel(factory = viewModelFactory {
                    initializer { ExportViewModel(container.log, container.weights, container.pdf, container.clock) }
                })
                ExportRoute(vm, onBack = { nav.popBackStack() })
            }
            composable<PrivacyRoute> {
                val vm: PrivacyViewModel = viewModel(factory = viewModelFactory {
                    initializer { PrivacyViewModel(container.data, container.settings) }
                })
                PrivacyRoute(vm, onBack = { nav.popBackStack() }, onDeleted = {
                    nav.navigate(TodayRoute) { popUpTo(0) }
                })
            }
        }
    }
}
