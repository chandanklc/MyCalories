package com.example.mycalories.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntity::class, LogEntryEntity::class, SavedMealEntity::class, SavedMealItemEntity::class,
        WaterEntity::class, WeightEntity::class, PortionPrefEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun waterDao(): WaterDao
    abstract fun weightDao(): WeightDao
    abstract fun portionPrefDao(): PortionPrefDao

    companion object {
        const val NAME = "mycalories.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()
    }
}

private val MICRO_COLUMNS = listOf("ironMg", "calciumMg", "vitaminB12Ug", "vitaminDUg", "vitaminCMg", "potassiumMg")

/** Version 2: vitamins and minerals, spoken-text notes, weights and learned portions. Keeps all data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("foods", "log_entries", "saved_meal_items").forEach { table ->
            MICRO_COLUMNS.forEach { db.execSQL("ALTER TABLE `$table` ADD COLUMN `n_$it` REAL NOT NULL DEFAULT 0") }
        }
        db.execSQL("ALTER TABLE `log_entries` ADD COLUMN `note` TEXT DEFAULT NULL")
        db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`epochDay` INTEGER NOT NULL, `kg` REAL NOT NULL, PRIMARY KEY(`epochDay`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `portion_prefs` (`nameKey` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                "`gramsPerUnit` REAL NOT NULL, `timesSeen` INTEGER NOT NULL, PRIMARY KEY(`nameKey`, `unit`))",
        )
    }
}
