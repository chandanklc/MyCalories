package com.example.mycalories.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.mycalories.data.remote.ChatMessage
import com.example.mycalories.domain.DaySummary
import com.example.mycalories.domain.GoalSuggestion
import com.example.mycalories.domain.MicroKey
import com.example.mycalories.domain.MicroRow
import com.example.mycalories.domain.MicroStatus
import com.example.mycalories.domain.ProteinTip
import com.example.mycalories.domain.WeekSummary
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.HealthAvailability
import com.example.mycalories.ui.ask.AskScreen
import com.example.mycalories.ui.ask.AskUiState
import com.example.mycalories.ui.micros.MicrosScreen
import com.example.mycalories.ui.micros.MicrosUiState
import com.example.mycalories.ui.privacy.PrivacyScreen
import com.example.mycalories.ui.privacy.PrivacyUiState
import com.example.mycalories.ui.recipe.RecipeScreen
import com.example.mycalories.ui.recipe.RecipeStage
import com.example.mycalories.ui.recipe.RecipeUiState
import com.example.mycalories.ui.settings.SettingsScreen
import com.example.mycalories.ui.settings.SettingsUiState
import com.example.mycalories.ui.theme.MyCaloriesTheme
import com.example.mycalories.ui.today.TodayScreen
import com.example.mycalories.ui.today.TodayUiState
import com.example.mycalories.ui.weekly.WeeklySummaryScreen
import com.example.mycalories.ui.weekly.WeeklyUiState
import com.example.mycalories.ui.weight.WeightScreen
import com.example.mycalories.ui.weight.WeightUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import androidx.compose.foundation.layout.PaddingValues

private val DAY = LocalDate.of(2026, 11, 18)
private val GOAL = DailyGoal(2000, 120, 250, 67, 28)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NewScreensUiTest {

    @get:Rule val compose = createComposeRule()

    private fun item(name: String, kcal: Double, grams: Double) =
        LoggedItem(1, DAY, MealType.LUNCH, name, 1.0, "g", grams, Nutrients(calories = kcal, protein = 10.0), FoodSource.LOCAL, false, 0)

    @Test
    fun `today shows burned calories and copies yesterday`() {
        val copied = mutableListOf<MealType?>()
        val state = TodayUiState(DAY, true, DaySummary.of(DAY, emptyList(), GOAL), loading = false,
            burnedCalories = 320, steps = 6450, yesterdayMeals = mapOf(MealType.BREAKFAST to 384.0))
        compose.setContent {
            MyCaloriesTheme { TodayScreen(state, PaddingValues(), {}, {}, {}, {}, {}, {}, {}, onCopy = { copied += it }) }
        }
        compose.onNodeWithContentDescription("2320 kcal left").assertIsDisplayed()
        compose.onNodeWithText("+320").assertIsDisplayed()
        compose.onNodeWithText("6,450 steps", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Same breakfast · 384").performClick()
        compose.onNodeWithTag("copyDay").performClick()
        assertEquals(listOf(MealType.BREAKFAST, null), copied)
    }

    @Test
    fun `recipe shows per serving numbers and saves`() {
        var saved = 0
        var servings = 0
        val state = RecipeUiState(RecipeStage.Ready, name = "Mom's dal", servings = 4, servingUnit = "bowl",
            ingredients = listOf(item("Toor dal", 343.0, 200.0), item("Ghee", 900.0, 26.0)))
        compose.setContent {
            MyCaloriesTheme { RecipeScreen(state, {}, {}, {}, {}, {}, { servings = it }, {}, { saved++ }) }
        }
        compose.onNodeWithTag("perServing").assertTextContains("230 kcal")
        compose.onNodeWithTag("servings").assertTextContains("4 bowl")
        compose.onNodeWithContentDescription("More servings").performClick()
        assertEquals(5, servings)
        compose.onNodeWithTag("saveRecipe").performClick()
        assertEquals(1, saved)
    }

    @Test
    fun `weight suggestion can be accepted or kept`() {
        val calls = mutableListOf<String>()
        val state = WeightUiState(
            entries = listOf(WeightEntry(DAY.minusDays(14), 78.6), WeightEntry(DAY, 78.0)), current = 78.0, targetKg = 72.0,
            weeklyRate = -0.3, suggestion = GoalSuggestion(1840, 1720),
        )
        compose.setContent {
            MyCaloriesTheme { WeightScreen(state, {}, { calls += "log:$it" }, {}, { calls += "accept" }, { calls += "keep" }) }
        }
        compose.onNodeWithText("78.0 kg").assertIsDisplayed()
        compose.onNodeWithText("Adjust your daily goal?").assertIsDisplayed()
        compose.onNodeWithText("Update goal").performClick()
        compose.onNodeWithText("Keep 1840").performClick()
        assertEquals(listOf("accept", "keep"), calls)
        // The "Log weight" dialog is checked on the emulator: a text field inside a dialog never settles under Robolectric.
    }

    @Test
    fun `weekly summary shows what to work on`() {
        val summary = WeekSummary(DAY.minusDays(6), DAY, 2072.0, 86.0, 7, 5, 5, 4.0, 6, -0.6, 12,
            listOf(ProteinTip("2 boiled eggs", 13)))
        var asked = 0
        compose.setContent { MyCaloriesTheme { WeeklySummaryScreen(WeeklyUiState(summary, GOAL), {}, { asked++ }) } }
        compose.onNodeWithText("Protein was low on 5 of 7 days").assertIsDisplayed()
        compose.onNodeWithText("• 2 boiled eggs (+13 g)").assertIsDisplayed()
        compose.onNodeWithText("Logged 12 days in a row").assertIsDisplayed()
        compose.onNodeWithText("Ask a question about my week").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun `ask shows the conversation and sends a suggestion`() {
        val sent = mutableListOf<String>()
        compose.setContent { MyCaloriesTheme { AskScreen(AskUiState(), {}) { sent += it } } }
        compose.onNodeWithText("Why am I over today?").performClick()
        compose.onNodeWithTag("askField").performTextInput("snack ideas?")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(listOf("Why am I over today?", "snack ideas?"), sent)
    }

    @Test
    fun `ask shows answers`() {
        val state = AskUiState(listOf(ChatMessage.user("Dinner?"), ChatMessage.assistant("Try paneer bhurji.")))
        compose.setContent { MyCaloriesTheme { AskScreen(state, {}) {} } }
        compose.onNodeWithText("Try paneer bhurji.").assertIsDisplayed()
    }

    @Test
    fun `vitamins show low and over badges and tips`() {
        val state = MicrosUiState(rows = listOf(
            MicroRow(MicroKey.IRON, 9.0, 19.0, "mg", MicroStatus.LOW),
            MicroRow(MicroKey.SODIUM, 2950.0, 2000.0, "mg", MicroStatus.OVER),
        ), lowThisWeek = setOf(MicroKey.B12))
        compose.setContent { MyCaloriesTheme { MicrosScreen(state, {}) {} } }
        compose.onNodeWithText("Low  ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Over  ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Low all week: Vitamin B12").assertIsDisplayed()
    }

    @Test
    fun `settings switch language and reminders`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MyCaloriesTheme {
                SettingsScreen(SettingsUiState(AppSettings(), HealthAvailability.AVAILABLE), {},
                    onMeal = { calls += "meal:$it" }, onWater = { calls += "water:$it" }, onHealth = { calls += "health" },
                    onTheme = { calls += "theme:$it" }, onLanguage = { calls += "lang:$it" }, {}, {}, {})
            }
        }
        compose.onNodeWithTag("waterSwitch").performClick()
        compose.onNodeWithTag("hindi").performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(listOf("water:true", "lang:hi", "theme:DARK"), calls)
    }

    @Test
    fun `delete all asks before deleting`() {
        val calls = mutableListOf<String>()
        var confirming = false
        compose.setContent {
            MyCaloriesTheme {
                PrivacyScreen(PrivacyUiState(confirmingDelete = confirming), {}, SnackbarHostState(), {}, {}, {}, {},
                    onDelete = { calls += "ask"; confirming = true }, onConfirmDelete = { calls += "delete" }, onCancelDelete = {})
            }
        }
        compose.onNodeWithTag("deleteAll").performClick()
        assertTrue(calls == listOf("ask"))
    }

    @Test
    fun `delete dialog confirms`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            MyCaloriesTheme {
                PrivacyScreen(PrivacyUiState(confirmingDelete = true), {}, SnackbarHostState(), {}, {}, {}, {},
                    onDelete = {}, onConfirmDelete = { calls += "delete" }, onCancelDelete = {})
            }
        }
        compose.onNodeWithText("Delete everything?").assertIsDisplayed()
        compose.onNodeWithTag("confirmDelete").performClick()
        assertEquals(listOf("delete"), calls)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "hi-w411dp-h891dp")
class HindiUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `today screen in hindi`() {
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(DAY, true, DaySummary.of(DAY, emptyList(), GOAL), loading = false), PaddingValues(),
                    {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("आज").assertIsDisplayed()
        compose.onNodeWithText("नाश्ता").assertIsDisplayed()
        compose.onNodeWithText("रात का खाना").assertIsDisplayed()
        compose.onNodeWithContentDescription("2000 kcal बाकी").assertIsDisplayed()
    }
}
