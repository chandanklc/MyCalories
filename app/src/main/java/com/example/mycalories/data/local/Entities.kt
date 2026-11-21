package com.example.mycalories.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Nutrients stored inline in a table row. All values per 100 g. */
data class NutrientColumns(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    @ColumnInfo(defaultValue = "0") val ironMg: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val calciumMg: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val vitaminB12Ug: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val vitaminDUg: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val vitaminCMg: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val potassiumMg: Double = 0.0,
)

@Entity(tableName = "foods", indices = [Index(value = ["nameKey"], unique = true)])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Lower-case, single-spaced name used for matching. */
    val nameKey: String,
    @Embedded(prefix = "n_") val per100g: NutrientColumns,
    val defaultUnit: String,
    val gramsPerUnit: Double,
    val source: String,
)

@Entity(tableName = "log_entries", indices = [Index("epochDay")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    @Embedded(prefix = "n_") val per100g: NutrientColumns,
    val source: String,
    val needsReview: Boolean,
    val loggedAtMillis: Long,
    @ColumnInfo(defaultValue = "NULL") val note: String? = null,
)

@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [ForeignKey(
        entity = SavedMealEntity::class, parentColumns = ["id"], childColumns = ["savedMealId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("savedMealId")],
)
data class SavedMealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val position: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    @Embedded(prefix = "n_") val per100g: NutrientColumns,
    val source: String,
)

data class SavedMealWithItems(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "savedMealId") val items: List<SavedMealItemEntity>,
)

@Entity(tableName = "water")
data class WaterEntity(
    @PrimaryKey val epochDay: Long,
    val glasses: Int,
)

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)

@Entity(tableName = "portion_prefs", primaryKeys = ["nameKey", "unit"])
data class PortionPrefEntity(
    val nameKey: String,
    val unit: String,
    val gramsPerUnit: Double,
    val timesSeen: Int,
)
