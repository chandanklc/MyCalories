package com.example.mycalories.ui

import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.ui.profile.ProfileViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun filled(vm: ProfileViewModel) = vm.apply {
        onAge("32"); onHeight("172"); onWeight("78")
        onSex(Sex.MALE); onActivity(ActivityLevel.LIGHT); onGoal(GoalType.LOSE)
    }

    @Test
    fun `empty form has no preview and cannot save`() = runTest {
        val s = ProfileViewModel(FakeProfileRepository()).state.value
        assertNull(s.preview)
        assertFalse(s.canSave)
    }

    @Test
    fun `preview updates as fields are filled`() = runTest {
        val vm = filled(ProfileViewModel(FakeProfileRepository()))
        val expected = GoalCalculator.calculate(UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.LIGHT, GoalType.LOSE))
        assertEquals(expected, vm.state.value.preview)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `out of range values show an error and block saving`() = runTest {
        val vm = filled(ProfileViewModel(FakeProfileRepository()))
        vm.onAge("7")
        assertNotNull(vm.state.value.ageError)
        assertFalse(vm.state.value.canSave)
        vm.onHeight("400")
        assertNotNull(vm.state.value.heightError)
        vm.onWeight("abc")
        assertNotNull(vm.state.value.weightError)
    }

    @Test
    fun `decimal comma is accepted`() = runTest {
        val vm = filled(ProfileViewModel(FakeProfileRepository()))
        vm.onWeight("78,5")
        assertNull(vm.state.value.weightError)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `save stores the profile and marks it saved`() = runTest {
        val repo = FakeProfileRepository()
        val vm = filled(ProfileViewModel(repo))
        vm.save()
        assertEquals(UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.LIGHT, GoalType.LOSE), repo.profileFlow.value)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `save does nothing when the form is invalid`() = runTest {
        val repo = FakeProfileRepository()
        val vm = ProfileViewModel(repo)
        vm.save()
        assertNull(repo.profileFlow.value)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `existing profile fills the form`() = runTest {
        val p = UserProfile(41, Sex.FEMALE, 158.0, 61.5, ActivityLevel.MODERATE, GoalType.MAINTAIN)
        val s = ProfileViewModel(FakeProfileRepository(p)).state.value
        assertEquals("41", s.age)
        assertEquals("158", s.height)
        assertEquals("61.5", s.weight)
        assertEquals(Sex.FEMALE, s.sex)
        assertEquals(ActivityLevel.MODERATE, s.activity)
        assertEquals(GoalType.MAINTAIN, s.goal)
        assertTrue(s.isEditing)
    }
}
