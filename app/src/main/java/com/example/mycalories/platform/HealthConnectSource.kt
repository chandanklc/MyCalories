package com.example.mycalories.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.DayActivity
import com.example.mycalories.domain.repository.HealthAvailability
import com.example.mycalories.domain.repository.HealthDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads steps, active calories and weight from Health Connect, and writes logged meals to it. */
class HealthConnectSource(private val context: Context) : HealthDataSource {

    private val zone get() = ZoneId.systemDefault()
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.NOT_SUPPORTED
    }

    override suspend fun hasPermissions(): Boolean =
        availability() == HealthAvailability.AVAILABLE &&
            client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    override suspend fun activity(date: LocalDate): DayActivity? {
        if (!hasPermissions()) return null
        val range = TimeRangeFilter.between(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
        val result = client.aggregate(
            AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, StepsRecord.COUNT_TOTAL), range),
        )
        return DayActivity(
            activeCalories = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
        )
    }

    override suspend fun latestWeight(): WeightEntry? {
        if (!hasPermissions()) return null
        val records = client.readRecords(
            ReadRecordsRequest(
                WeightRecord::class,
                TimeRangeFilter.after(Instant.now().minusSeconds(60L * 60 * 24 * 90)),
                ascendingOrder = false, pageSize = 1,
            ),
        ).records
        return records.firstOrNull()?.let { WeightEntry(it.time.atZone(zone).toLocalDate(), it.weight.inKilograms) }
    }

    override suspend fun writeMeals(items: List<LoggedItem>) {
        if (items.isEmpty() || !hasPermissions()) return
        client.insertRecords(items.map { item ->
            val start = Instant.ofEpochMilli(item.loggedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())
            val n = item.nutrients
            NutritionRecord(
                startTime = start, startZoneOffset = zone.rules.getOffset(start),
                endTime = start.plusSeconds(60), endZoneOffset = zone.rules.getOffset(start),
                name = item.name,
                mealType = when (item.meal) {
                    MealType.BREAKFAST -> 1
                    MealType.LUNCH -> 2
                    MealType.DINNER -> 3
                    MealType.SNACK -> 4
                },
                energy = Energy.kilocalories(n.calories),
                protein = Mass.grams(n.protein),
                totalCarbohydrate = Mass.grams(n.carbs),
                totalFat = Mass.grams(n.fat),
                dietaryFiber = Mass.grams(n.fiber),
                sugar = Mass.grams(n.sugar),
                sodium = Mass.milligrams(n.sodiumMg),
                iron = Mass.milligrams(n.ironMg),
                calcium = Mass.milligrams(n.calciumMg),
                potassium = Mass.milligrams(n.potassiumMg),
                vitaminC = Mass.milligrams(n.vitaminCMg),
                vitaminB12 = Mass.micrograms(n.vitaminB12Ug),
                vitaminD = Mass.micrograms(n.vitaminDUg),
                metadata = Metadata.manualEntry(),
            )
        })
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
        )
    }
}
