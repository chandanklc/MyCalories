package com.example.mycalories.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.local.MIGRATION_1_2
import com.example.mycalories.data.repository.RoomFoodRepository
import com.example.mycalories.data.repository.RoomLogRepository
import com.example.mycalories.data.repository.RoomPortionRepository
import com.example.mycalories.data.repository.RoomWeightRepository
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DatabaseV2Test {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private val today = LocalDate.of(2026, 11, 18)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `migration from version 1 keeps foods and log entries`() = runTest {
        val name = "migrate-test.db"
        ctx.deleteDatabase(name)
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            V1_SCHEMA.forEach(v1::execSQL)
            v1.execSQL("INSERT INTO foods (name, nameKey, defaultUnit, gramsPerUnit, source, n_calories, n_protein, n_carbs, n_fat, n_fiber, n_sugar, n_sodiumMg) VALUES ('Roti','roti','piece',40,'LOCAL',297,9.8,55.8,3.7,9.7,1.6,409)")
            v1.execSQL("INSERT INTO log_entries (epochDay, meal, name, quantity, unit, grams, source, needsReview, loggedAtMillis, n_calories, n_protein, n_carbs, n_fat, n_fiber, n_sugar, n_sodiumMg) VALUES (${today.toEpochDay()},'LUNCH','Roti',2,'piece',80,'LOCAL',0,1,297,9.8,55.8,3.7,9.7,1.6,409)")
            v1.version = 1
        }

        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val roti = RoomFoodRepository(migrated).findByName("roti")!!
            assertEquals(297.0, roti.per100g.calories, 0.0)
            assertEquals(0.0, roti.per100g.ironMg, 0.0)
            val item = RoomLogRepository(migrated).observeDay(today).first().single()
            assertEquals(80.0, item.grams, 0.0)
            assertNull(item.note)
            RoomWeightRepository(migrated).log(today, 78.0)
            assertEquals(78.0, RoomWeightRepository(migrated).observeAll().first().single().kg, 0.0)
        } finally {
            migrated.close()
            ctx.deleteDatabase(name)
        }
    }

    @Test
    fun `micronutrients and notes round trip`() = runTest {
        val n = Nutrients(calories = 100.0, ironMg = 2.5, calciumMg = 120.0, vitaminB12Ug = 0.4, vitaminDUg = 1.1, vitaminCMg = 9.0, potassiumMg = 300.0)
        RoomFoodRepository(db).save(Food(name = "Curd", per100g = n, defaultUnit = "katori", gramsPerUnit = 150.0, source = FoodSource.LOCAL))
        assertEquals(n, RoomFoodRepository(db).findByName("curd")!!.per100g)

        val log = RoomLogRepository(db)
        log.add(listOf(LoggedItem(0, today, MealType.SNACK, "Curd", 1.0, "katori", 150.0, n, FoodSource.LOCAL, false, 0, note = "a katori of curd")))
        val item = log.observeDay(today).first().single()
        assertEquals(n, item.per100g)
        assertEquals("a katori of curd", item.note)
    }

    @Test
    fun `weights are kept one per day, newest last`() = runTest {
        val repo = RoomWeightRepository(db)
        repo.log(today.minusDays(7), 78.6)
        repo.log(today, 78.2)
        repo.log(today, 78.0)
        val all = repo.observeAll().first()
        assertEquals(listOf(78.6, 78.0), all.map { it.kg })
        assertEquals(today, all.last().date)
        repo.delete(today)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test
    fun `portion edits are counted per food and unit`() = runTest {
        val repo = RoomPortionRepository(db)
        assertNull(repo.find("Dal tadka", "bowl"))
        repo.record("Dal tadka", "bowl", 250.0)
        repo.record("dal  TADKA", "bowls", 250.0)
        val pref = repo.find("dal tadka", "Bowl")!!
        assertEquals(250.0, pref.gramsPerUnit, 0.0)
        assertEquals(2, pref.timesSeen)
    }

    @Test
    fun `a different portion restarts the count`() = runTest {
        val repo = RoomPortionRepository(db)
        repo.record("Rice", "cup", 180.0)
        repo.record("Rice", "cup", 200.0)
        val pref = repo.find("rice", "cup")!!
        assertEquals(200.0, pref.gramsPerUnit, 0.0)
        assertEquals(1, pref.timesSeen)
    }

    @Test
    fun `clear all wipes every table`() = runTest {
        RoomLogRepository(db).add(listOf(LoggedItem(0, today, MealType.LUNCH, "x", 1.0, "g", 1.0, Nutrients(), FoodSource.LOCAL, false, 0)))
        RoomWeightRepository(db).log(today, 70.0)
        db.clearAllTables()
        assertEquals(0, RoomLogRepository(db).observeDay(today).first().size)
        assertEquals(0, RoomWeightRepository(db).observeAll().first().size)
    }

    companion object {
        /** The exact tables Room created in version 1. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `defaultUnit` TEXT NOT NULL, `gramsPerUnit` REAL NOT NULL, `source` TEXT NOT NULL, `n_calories` REAL NOT NULL, `n_protein` REAL NOT NULL, `n_carbs` REAL NOT NULL, `n_fat` REAL NOT NULL, `n_fiber` REAL NOT NULL, `n_sugar` REAL NOT NULL, `n_sodiumMg` REAL NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_nameKey` ON `foods` (`nameKey`)",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `meal` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `grams` REAL NOT NULL, `source` TEXT NOT NULL, `needsReview` INTEGER NOT NULL, `loggedAtMillis` INTEGER NOT NULL, `n_calories` REAL NOT NULL, `n_protein` REAL NOT NULL, `n_carbs` REAL NOT NULL, `n_fat` REAL NOT NULL, `n_fiber` REAL NOT NULL, `n_sugar` REAL NOT NULL, `n_sodiumMg` REAL NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_epochDay` ON `log_entries` (`epochDay`)",
            "CREATE TABLE IF NOT EXISTS `saved_meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `saved_meal_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `savedMealId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `grams` REAL NOT NULL, `source` TEXT NOT NULL, `n_calories` REAL NOT NULL, `n_protein` REAL NOT NULL, `n_carbs` REAL NOT NULL, `n_fat` REAL NOT NULL, `n_fiber` REAL NOT NULL, `n_sugar` REAL NOT NULL, `n_sodiumMg` REAL NOT NULL, FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_saved_meal_items_savedMealId` ON `saved_meal_items` (`savedMealId`)",
            "CREATE TABLE IF NOT EXISTS `water` (`epochDay` INTEGER NOT NULL, `glasses` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))",
        )
    }
}
