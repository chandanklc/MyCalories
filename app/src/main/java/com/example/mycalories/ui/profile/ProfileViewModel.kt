package com.example.mycalories.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val age: String = "",
    val sex: Sex = Sex.MALE,
    val height: String = "",
    val weight: String = "",
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: GoalType = GoalType.MAINTAIN,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
) {
    private val ageValue get() = age.trim().toIntOrNull()?.takeIf { it in 13..100 }
    private val heightValue get() = parseDecimal(height)?.takeIf { it in 100.0..230.0 }
    private val weightValue get() = parseDecimal(weight)?.takeIf { it in 30.0..300.0 }

    val ageError: String? get() = if (age.isNotBlank() && ageValue == null) "Enter an age from 13 to 100" else null
    val heightError: String? get() = if (height.isNotBlank() && heightValue == null) "Enter height in cm (100–230)" else null
    val weightError: String? get() = if (weight.isNotBlank() && weightValue == null) "Enter weight in kg (30–300)" else null

    val profile: UserProfile?
        get() {
            val a = ageValue ?: return null
            val h = heightValue ?: return null
            val w = weightValue ?: return null
            return UserProfile(a, sex, h, w, activity, goal)
        }

    val preview: DailyGoal? get() = profile?.let(GoalCalculator::calculate)
    val canSave: Boolean get() = profile != null

    companion object {
        fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
        fun format(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}

class ProfileViewModel(private val profiles: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.profile.first()?.let { p ->
                _state.update {
                    it.copy(
                        age = p.age.toString(), sex = p.sex, height = ProfileUiState.format(p.heightCm),
                        weight = ProfileUiState.format(p.weightKg), activity = p.activity, goal = p.goal, isEditing = true,
                    )
                }
            }
        }
    }

    fun onAge(v: String) = _state.update { it.copy(age = v, saved = false) }
    fun onHeight(v: String) = _state.update { it.copy(height = v, saved = false) }
    fun onWeight(v: String) = _state.update { it.copy(weight = v, saved = false) }
    fun onSex(v: Sex) = _state.update { it.copy(sex = v, saved = false) }
    fun onActivity(v: ActivityLevel) = _state.update { it.copy(activity = v, saved = false) }
    fun onGoal(v: GoalType) = _state.update { it.copy(goal = v, saved = false) }

    fun save() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            profiles.save(profile)
            _state.update { it.copy(saved = true, isEditing = true) }
        }
    }
}
