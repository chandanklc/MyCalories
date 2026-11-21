package com.example.mycalories.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.mycalories.domain.DaySummary
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.ui.logmeal.LogMealScreen
import com.example.mycalories.ui.logmeal.LogMealUiState
import com.example.mycalories.ui.logmeal.Stage
import com.example.mycalories.ui.profile.ProfileScreen
import com.example.mycalories.ui.profile.ProfileUiState
import com.example.mycalories.ui.theme.MyCaloriesTheme
import com.example.mycalories.ui.today.TodayScreen
import com.example.mycalories.ui.today.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ScreensUiTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val goal = DailyGoal(2000, 100, 250, 67, 28)

    private fun item(name: String, meal: MealType, kcalPer100: Double, grams: Double, review: Boolean = false) =
        LoggedItem(1, today, meal, name, 1.0, "bowl", grams, Nutrients(calories = kcalPer100, protein = 5.0),
            FoodSource.LOCAL, review, 0)

    // ---------- Today ----------

    @Test
    fun `today shows calories left and every meal`() {
        val summary = DaySummary.of(today, listOf(item("Dal", MealType.LUNCH, 100.0, 600.0)), goal)
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, summary, 3, loading = false), PaddingValues(), {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("1400 kcal left").assertIsDisplayed()
        MealType.entries.forEach { compose.onNodeWithTag("meal_${it.name}").assertExists() }
        compose.onNodeWithText("3 glasses", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping a logged meal opens it and an empty meal adds to it`() {
        var opened: MealType? = null
        var added = 0
        val summary = DaySummary.of(today, listOf(item("Dal", MealType.LUNCH, 100.0, 600.0)), goal)
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, summary, loading = false), PaddingValues(), {}, {}, {}, {}, {},
                    onOpenMeal = { opened = it }, onAddToMeal = { added++ })
            }
        }
        compose.onNodeWithTag("meal_LUNCH").performClick()
        assertEquals(MealType.LUNCH, opened)
        compose.onNodeWithTag("meal_DINNER").performClick()
        assertEquals(1, added)
    }

    @Test
    fun `water buttons call back`() {
        var added = 0
        var removed = 0
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, DaySummary.of(today, emptyList(), goal), loading = false), PaddingValues(),
                    {}, {}, {}, onAddWater = { added++ }, onRemoveWater = { removed++ }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Add a glass of water").performClick()
        compose.onNodeWithContentDescription("Remove a glass of water").performClick()
        assertEquals(1, added)
        assertEquals(1, removed)
    }

    @Test
    fun `past day shows a today button`() {
        var today = 0
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(this.today.minusDays(2), false, DaySummary.of(this.today, emptyList(), goal), loading = false),
                    PaddingValues(), {}, {}, onToday = { today++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Today").performClick()
        assertEquals(1, today)
    }

    // ---------- Log meal ----------

    private fun logScreen(state: LogMealUiState, typing: Boolean = false, calls: MutableList<String> = mutableListOf()) {
        compose.setContent {
            MyCaloriesTheme {
                LogMealScreen(
                    state = state, typing = typing,
                    onListen = { calls += "listen" }, onStop = { calls += "stop" }, onType = { calls += "type" },
                    onSubmitText = { calls += "submit:$it" },
                    onChooseMeal = { m, r -> calls += "choose:${m.name}:$r" }, onChangeMeal = { calls += "change:${it.name}" },
                    onUpdateItem = { i, q, u -> calls += "update:$i:$q:$u" }, onRemoveItem = { calls += "remove:$it" },
                    onFavorite = { calls += "fav:$it" }, onFavoriteName = { calls += "favName:$it" },
                    onSave = { calls += "save" }, onRetry = { calls += "retry" }, onClose = { calls += "close" },
                )
            }
        }
    }

    @Test
    fun `listening shows the live transcript and done button`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Listening, transcript = "two roti"), calls = calls)
        compose.onNodeWithTag("transcript").assertTextContains("two roti")
        compose.onNodeWithContentDescription("Done speaking").performClick()
        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun `review shows items total and saves to the meal`() {
        val calls = mutableListOf<String>()
        val items = listOf(item("Roti", MealType.LUNCH, 300.0, 80.0), item("Salad", MealType.LUNCH, 20.0, 150.0, review = true))
        logScreen(LogMealUiState(stage = Stage.Review, items = items, meal = MealType.LUNCH), calls = calls)

        compose.onNodeWithText("Roti").assertIsDisplayed()
        compose.onNodeWithText("Please check").assertIsDisplayed()
        compose.onNodeWithTag("mealTotal").assertTextContains("270 kcal")
        compose.onNodeWithTag("saveMeal").assertTextContains("Add 270 kcal to Lunch").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun `tapping an item opens the portion editor and saves`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Review, items = listOf(item("Roti", MealType.LUNCH, 300.0, 80.0)), meal = MealType.LUNCH), calls = calls)
        compose.onNodeWithTag("reviewItem").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("update:0:1.5:bowl"), calls)
    }

    @Test
    fun `choose meal sheet offers every meal and passes remember`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.ChooseMeal, transcript = "dal", suggestedMeal = MealType.LUNCH), calls = calls)
        compose.onNodeWithText("Which meal is this?").assertIsDisplayed()
        compose.onNodeWithText("Suggested").assertIsDisplayed()
        compose.onNodeWithText("Always pick by time of day, don't ask").performClick()
        compose.onNodeWithTag("choose_DINNER").performClick()
        assertTrue(calls.contains("choose:DINNER:true"))
    }

    @Test
    fun `error shows message with retry and type instead`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Error, error = com.example.mycalories.data.remote.AppError.NETWORK), calls = calls)
        compose.onNodeWithTag("error").assertTextContains("Couldn't connect", substring = true)
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Type instead").performClick()
        assertEquals(listOf("retry", "type"), calls)
    }

    @Test
    fun `typed text is submitted`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Idle), typing = true, calls = calls)
        compose.onNodeWithTag("typeField").performTextInput("one bowl poha")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("submit:one bowl poha"), calls)
    }

    // ---------- Profile ----------

    @Test
    fun `profile save is disabled until valid then shows the target`() {
        compose.setContent {
            MyCaloriesTheme { ProfileScreen(ProfileUiState(), PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("saveProfile").assertIsNotEnabled()
    }

    @Test
    fun `valid profile shows the goal preview and saves`() {
        var saved = 0
        compose.setContent {
            MyCaloriesTheme {
                ProfileScreen(ProfileUiState(age = "32", height = "172", weight = "78"), PaddingValues(),
                    {}, {}, {}, {}, {}, {}, onSave = { saved++ })
            }
        }
        compose.onNodeWithTag("goalPreview").assertTextContains("kcal", substring = true)
        compose.onNodeWithTag("saveProfile").assertIsEnabled().performClick()
        assertEquals(1, saved)
    }

    @Test
    fun `invalid age shows an error`() {
        compose.setContent {
            MyCaloriesTheme { ProfileScreen(ProfileUiState(age = "5"), PaddingValues(), {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Enter an age from 13 to 100").assertIsDisplayed()
    }
}
