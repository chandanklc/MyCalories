package com.example.mycalories.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.mycalories.data.repository.DataStoreProfileRepository
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreProfileRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val repo by lazy {
        DataStoreProfileRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("p.preferences_pb") })
    }

    @After fun tearDown() = scope.cancel()

    @Test
    fun `no profile until one is saved`() = runTest {
        assertNull(repo.profile.first())
    }

    @Test
    fun `saved profile comes back`() = runTest {
        val p = UserProfile(29, Sex.FEMALE, 160.5, 58.2, ActivityLevel.MODERATE, GoalType.LOSE, calorieAdjustment = -120)
        repo.save(p)
        assertEquals(p, repo.profile.first())
    }

    @Test
    fun `auto pick meal defaults off and can be turned on`() = runTest {
        assertFalse(repo.autoPickMeal.first())
        repo.setAutoPickMeal(true)
        assertTrue(repo.autoPickMeal.first())
    }
}
