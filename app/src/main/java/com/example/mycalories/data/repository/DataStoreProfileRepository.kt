package com.example.mycalories.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreProfileRepository(private val store: DataStore<Preferences>) : ProfileRepository {

    private object Keys {
        val age = intPreferencesKey("age")
        val sex = stringPreferencesKey("sex")
        val height = doublePreferencesKey("height_cm")
        val weight = doublePreferencesKey("weight_kg")
        val activity = stringPreferencesKey("activity")
        val goal = stringPreferencesKey("goal")
        val autoPick = booleanPreferencesKey("auto_pick_meal")
        val adjustment = intPreferencesKey("calorie_adjustment")
        val target = doublePreferencesKey("target_weight_kg")
        val reviewed = androidx.datastore.preferences.core.longPreferencesKey("goal_reviewed_on")
    }

    override val profile: Flow<UserProfile?> = store.data.map { p ->
        val age = p[Keys.age] ?: return@map null
        UserProfile(
            age = age,
            sex = Sex.entries.firstOrNull { it.name == p[Keys.sex] } ?: Sex.MALE,
            heightCm = p[Keys.height] ?: return@map null,
            weightKg = p[Keys.weight] ?: return@map null,
            activity = ActivityLevel.entries.firstOrNull { it.name == p[Keys.activity] } ?: ActivityLevel.LIGHT,
            goal = GoalType.entries.firstOrNull { it.name == p[Keys.goal] } ?: GoalType.MAINTAIN,
            calorieAdjustment = p[Keys.adjustment] ?: 0,
            targetWeightKg = p[Keys.target],
            goalReviewedOn = p[Keys.reviewed]?.let(java.time.LocalDate::ofEpochDay),
        )
    }

    override val autoPickMeal: Flow<Boolean> = store.data.map { it[Keys.autoPick] ?: false }

    override suspend fun save(profile: UserProfile) {
        store.edit {
            it[Keys.age] = profile.age
            it[Keys.sex] = profile.sex.name
            it[Keys.height] = profile.heightCm
            it[Keys.weight] = profile.weightKg
            it[Keys.activity] = profile.activity.name
            it[Keys.goal] = profile.goal.name
            it[Keys.adjustment] = profile.calorieAdjustment
            profile.targetWeightKg?.let { t -> it[Keys.target] = t } ?: it.remove(Keys.target)
            profile.goalReviewedOn?.let { d -> it[Keys.reviewed] = d.toEpochDay() } ?: it.remove(Keys.reviewed)
        }
    }

    override suspend fun setAutoPickMeal(enabled: Boolean) {
        store.edit { it[Keys.autoPick] = enabled }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}
