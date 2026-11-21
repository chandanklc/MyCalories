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
import com.example.mycalories.ui.logmeal.FoodSuggestion
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
    fun `the ring has a colour key for each logged meal only`() {
        val summary = DaySummary.of(today, listOf(
            item("Poha", MealType.BREAKFAST, 100.0, 300.0), item("Dal", MealType.LUNCH, 100.0, 600.0)), goal)
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, summary, loading = false), PaddingValues(), {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("ringKey_BREAKFAST").assertTextContains("300", substring = true)
        compose.onNodeWithTag("ringKey_LUNCH").assertTextContains("600", substring = true)
        compose.onNodeWithTag("ringKey_DINNER").assertDoesNotExist()
        compose.onNodeWithTag("ringKey_SNACK").assertDoesNotExist()
    }

    @Test
    fun `tapping a logged meal opens it and an empty meal adds to it`() {
        var opened: MealType? = null
        var added = 0
        var addedTo: MealType? = null
        val summary = DaySummary.of(today, listOf(item("Dal", MealType.LUNCH, 100.0, 600.0)), goal)
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, summary, loading = false), PaddingValues(), {}, {}, {}, {}, {},
                    onOpenMeal = { opened = it }, onAddToMeal = { addedTo = it; added++ })
            }
        }
        compose.onNodeWithTag("meal_LUNCH").performClick()
        assertEquals(MealType.LUNCH, opened)
        compose.onNodeWithTag("meal_DINNER").performClick()
        assertEquals(1, added)
        assertEquals("the + knows which meal it is for", MealType.DINNER, addedTo)
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

    // ---------- Log meal: type and search ----------

    private val paratha = com.example.mycalories.domain.model.Food(2, "Paneer paratha",
        Nutrients(calories = 290.0), "piece", 90.0, FoodSource.LOCAL)

    private fun logScreen(state: LogMealUiState, calls: MutableList<String> = mutableListOf()) {
        compose.setContent {
            MyCaloriesTheme {
                LogMealScreen(
                    state = state,
                    onQuery = { calls += "query:$it" },
                    onAddSuggestion = { calls += "add:${it.food.name}:${it.quantity}" },
                    onFindWithAi = { calls += "ai" },
                    onMeal = { calls += "meal:${it.name}" },
                    onReview = { calls += "review" }, onAddMore = { calls += "more" },
                    onUpdateItem = { i, q, u -> calls += "update:$i:$q:$u" }, onRemoveItem = { calls += "remove:$it" },
                    onFavorite = { calls += "fav:$it" }, onFavoriteName = { calls += "favName:$it" },
                    onSave = { calls += "save" }, onRetry = { calls += "retry" }, onClose = { calls += "close" },
                    onPhoto = { calls += "photo" }, onBarcode = { calls += "barcode" },
                )
            }
        }
    }

    @Test
    fun `typing shows matching foods with calories for the amount, and + adds one`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(query = "2 pan", meal = MealType.DINNER,
            suggestions = listOf(FoodSuggestion(paratha, 2.0))), calls)
        compose.onNodeWithTag("searchField").assertTextContains("2 pan")
        compose.onNodeWithTag("suggestion_Paneer paratha").assertTextContains("522", substring = true)
        compose.onNodeWithContentDescription("Add Paneer paratha").performClick()
        assertEquals(listOf("add:Paneer paratha:2.0"), calls)
    }

    @Test
    fun `the AI button repeats what was typed and sends it`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(query = "2 roti with dal", meal = MealType.LUNCH), calls)
        compose.onNodeWithTag("findWithAi").assertTextContains("2 roti with dal", substring = true).performClick()
        assertEquals(listOf("ai"), calls)
    }

    @Test
    fun `no AI button before anything is typed`() {
        logScreen(LogMealUiState(meal = MealType.LUNCH, suggestions = listOf(FoodSuggestion(paratha, 1.0))))
        compose.onNodeWithTag("findWithAi").assertDoesNotExist()
        compose.onNodeWithText("Your foods").assertIsDisplayed()
    }

    @Test
    fun `meal chips pick the meal`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(meal = MealType.LUNCH), calls)
        compose.onNodeWithTag("mealChip_DINNER").performClick()
        assertEquals(listOf("meal:DINNER"), calls)
    }

    @Test
    fun `photo and barcode are one tap away`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(meal = MealType.LUNCH), calls)
        compose.onNodeWithContentDescription("Log by photo").performClick()
        compose.onNodeWithContentDescription("Scan a barcode").performClick()
        assertEquals(listOf("photo", "barcode"), calls)
    }

    @Test
    fun `picked foods show in a tray that opens the review`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(meal = MealType.LUNCH, items = listOf(item("Roti", MealType.LUNCH, 300.0, 80.0))), calls)
        compose.onNodeWithTag("tray").assertTextContains("Lunch so far", substring = true)
        compose.onNodeWithTag("tray").assertTextContains("240 kcal", substring = true)
        compose.onNodeWithText("Review").performClick()
        assertEquals(listOf("review"), calls)
    }

    @Test
    fun `no tray and no voice button when nothing is picked`() {
        logScreen(LogMealUiState(meal = MealType.LUNCH))
        compose.onNodeWithTag("tray").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start speaking").assertDoesNotExist()
    }

    @Test
    fun `review shows items total, adds more by typing and saves to the meal`() {
        val calls = mutableListOf<String>()
        val items = listOf(item("Roti", MealType.LUNCH, 300.0, 80.0), item("Salad", MealType.LUNCH, 20.0, 150.0, review = true))
        logScreen(LogMealUiState(stage = Stage.Review, items = items, meal = MealType.LUNCH), calls)

        compose.onNodeWithText("Roti").assertIsDisplayed()
        compose.onNodeWithText("Please check").assertIsDisplayed()
        compose.onNodeWithTag("mealTotal").assertTextContains("270 kcal")
        compose.onNodeWithText("Add more by typing").performClick()
        compose.onNodeWithTag("saveMeal").assertTextContains("Add 270 kcal to Lunch").performClick()
        assertEquals(listOf("more", "save"), calls)
    }

    @Test
    fun `tapping an item opens the portion editor and saves`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Review, items = listOf(item("Roti", MealType.LUNCH, 300.0, 80.0)), meal = MealType.LUNCH), calls)
        compose.onNodeWithTag("reviewItem").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("update:0:1.5:bowl"), calls)
    }

    @Test
    fun `error shows message and try again`() {
        val calls = mutableListOf<String>()
        logScreen(LogMealUiState(stage = Stage.Error, meal = MealType.LUNCH, error = com.example.mycalories.data.remote.AppError.NETWORK), calls)
        compose.onNodeWithTag("error").assertTextContains("Couldn't connect", substring = true)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), calls)
    }

    // ---------- Today: typing bar ----------

    @Test
    fun `today has a typing bar with photo and barcode, and no mic`() {
        var typed = 0; var photo = 0; var barcode = 0
        compose.setContent {
            MyCaloriesTheme {
                TodayScreen(TodayUiState(today, true, DaySummary.of(today, emptyList(), goal), loading = false), PaddingValues(),
                    {}, {}, {}, {}, {}, {}, {}, onType = { typed++ }, onPhoto = { photo++ }, onBarcode = { barcode++ })
            }
        }
        compose.onNodeWithTag("typeBar").assertTextContains("What did you eat?").performClick()
        compose.onNodeWithContentDescription("Log by photo").performClick()
        compose.onNodeWithContentDescription("Scan a barcode").performClick()
        assertEquals(listOf(1, 1, 1), listOf(typed, photo, barcode))
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
