package com.example.mycalories.data.repository

import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.local.toDomain
import com.example.mycalories.domain.Backup
import com.example.mycalories.domain.repository.DataManager
import com.example.mycalories.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Backup, restore and delete-everything for the whole app. */
class RoomDataManager(
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val clearSettings: suspend () -> Unit,
) : DataManager {

    private val foods = RoomFoodRepository(db)
    private val log = RoomLogRepository(db)
    private val saved = RoomSavedMealRepository(db)
    private val weights = RoomWeightRepository(db)

    override suspend fun export(): Backup = Backup(
        profile = profiles.profile.first(),
        foods = db.foodDao().all().map { it.toDomain() },
        log = db.logDao().all().map { it.toDomain() },
        savedMeals = saved.observeAll().first(),
        weights = weights.observeAll().first(),
        water = db.waterDao().all().associate { LocalDate.ofEpochDay(it.epochDay) to it.glasses },
    )

    /** Adds a backup's data to what's here. Foods with the same name are updated. */
    override suspend fun restore(backup: Backup) {
        backup.profile?.let { profiles.save(it) }
        backup.foods.forEach { foods.save(it) }
        log.add(backup.log)
        backup.savedMeals.forEach { saved.save(it) }
        backup.weights.forEach { weights.log(it.date, it.kg) }
        backup.water.forEach { (d, g) -> log.setWater(d, g) }
    }

    override suspend fun deleteEverything() {
        db.clearAllTables()
        profiles.clear()
        clearSettings()
    }
}
