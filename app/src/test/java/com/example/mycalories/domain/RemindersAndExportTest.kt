package com.example.mycalories.domain

import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MealTimesTest {

    @Test
    fun `defaults until there are three days of history`() {
        val usual = MealTimes.usual(mapOf(MealType.LUNCH to listOf(LocalTime.of(14, 0))))
        assertEquals(MealTimes.DEFAULTS, usual)
    }

    @Test
    fun `usual time is the median of past first logs`() {
        val lunch = listOf(LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 30), LocalTime.of(12, 50), LocalTime.of(13, 10))
        assertEquals(LocalTime.of(13, 10), MealTimes.usual(mapOf(MealType.LUNCH to lunch)).getValue(MealType.LUNCH))
    }

    @Test
    fun `a meal is due 45 minutes after its usual time, until three hours after`() {
        val usual = MealTimes.DEFAULTS // lunch 13:30
        val day = LocalDate.of(2026, 11, 18)
        fun due(h: Int, m: Int, logged: Set<MealType> = emptySet(), reminded: Set<MealType> = emptySet()) =
            MealTimes.dueReminders(LocalDateTime.of(day, LocalTime.of(h, m)), usual, logged, reminded)

        assertFalse(MealType.LUNCH in due(14, 0))
        assertTrue(MealType.LUNCH in due(14, 15))
        assertFalse("already logged", MealType.LUNCH in due(14, 15, logged = setOf(MealType.LUNCH)))
        assertFalse("already reminded", MealType.LUNCH in due(14, 15, reminded = setOf(MealType.LUNCH)))
        assertFalse("too late now", MealType.LUNCH in due(16, 45))
    }

    @Test
    fun `water reminders every two hours in the daytime until the goal`() {
        assertTrue(WaterPolicy.shouldRemind(LocalTime.of(11, 0), glasses = 3, lastReminder = LocalTime.of(9, 0)))
        assertFalse("too soon", WaterPolicy.shouldRemind(LocalTime.of(10, 30), 3, LocalTime.of(9, 0)))
        assertFalse("goal met", WaterPolicy.shouldRemind(LocalTime.of(15, 0), 8, null))
        assertFalse("night", WaterPolicy.shouldRemind(LocalTime.of(22, 0), 3, null))
        assertFalse("early", WaterPolicy.shouldRemind(LocalTime.of(7, 0), 0, null))
        assertTrue("first of the day", WaterPolicy.shouldRemind(LocalTime.of(9, 5), 0, null))
    }
}

class CsvExporterTest {

    private val day = LocalDate.of(2026, 11, 18)

    @Test
    fun `one row per food with nutrients and quoting`() {
        val items = listOf(
            LoggedItem(1, day, MealType.LUNCH, "Rice, white", 1.0, "cup", 158.0, Nutrients(calories = 130.0, protein = 2.7), FoodSource.LOCAL, false, 0),
            LoggedItem(2, day, MealType.DINNER, "Mom's \"special\" dal", 1.0, "bowl", 200.0, Nutrients(calories = 90.0), FoodSource.USER_RECIPE, false, 0),
        )
        val lines = CsvExporter.foods(items).trim().lines()
        assertEquals("date,meal,food,quantity,unit,grams,kcal,protein_g,carbs_g,fat_g,fiber_g,sugar_g,sodium_mg,iron_mg,calcium_mg,vitamin_b12_ug,vitamin_d_ug,vitamin_c_mg,potassium_mg,source", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1], lines[1].startsWith("2026-11-18,Lunch,\"Rice, white\",1,cup,158,205.4,4.3,"))
        assertTrue(lines[2], lines[2].contains("\"Mom's \"\"special\"\" dal\""))
    }

    @Test
    fun `daily totals and weights`() {
        val items = listOf(
            LoggedItem(1, day, MealType.LUNCH, "a", 1.0, "g", 100.0, Nutrients(calories = 500.0, protein = 20.0), FoodSource.LOCAL, false, 0),
            LoggedItem(2, day, MealType.DINNER, "b", 1.0, "g", 100.0, Nutrients(calories = 700.0, protein = 30.0), FoodSource.LOCAL, false, 0),
        )
        val csv = CsvExporter.dailyTotals(items, listOf(WeightEntry(day, 78.0))).trim().lines()
        assertEquals("date,kcal,protein_g,carbs_g,fat_g,fiber_g,weight_kg", csv[0])
        assertEquals("2026-11-18,1200,50.0,0.0,0.0,0.0,78.0", csv[1])
    }
}

class BackupTest {

    @Test
    fun `everything survives a round trip`() {
        val n = Nutrients(calories = 90.0, ironMg = 1.5)
        val backup = Backup(
            profile = UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.LIGHT, GoalType.LOSE, calorieAdjustment = -120, targetWeightKg = 72.0),
            foods = listOf(Food(0, "Dal", n, "bowl", 200.0, FoodSource.USER_RECIPE)),
            log = listOf(LoggedItem(0, LocalDate.of(2026, 11, 18), MealType.LUNCH, "Dal", 1.0, "bowl", 200.0, n, FoodSource.USER_RECIPE, true, 5, note = "dal")),
            savedMeals = listOf(SavedMeal(0, "Usual", listOf(SavedMealItem("Dal", 1.0, "bowl", 200.0, n, FoodSource.LOCAL)))),
            weights = listOf(WeightEntry(LocalDate.of(2026, 11, 18), 78.0)),
            water = mapOf(LocalDate.of(2026, 11, 18) to 5),
        )
        val text = BackupSerializer.encode(backup)
        assertTrue(text.contains("\"version\": 1") || text.contains("\"version\":1"))
        assertEquals(backup, BackupSerializer.decode(text))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `not a backup file is rejected`() {
        BackupSerializer.decode("{\"hello\":1}")
    }
}
