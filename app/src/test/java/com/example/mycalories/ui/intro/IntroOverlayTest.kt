package com.example.mycalories.ui.intro

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntroOverlayTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `intro shows the animation and then finishes on its own`() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { IntroOverlay(onFinished = { finished++ }) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("intro").assertExists()
        assertEquals(0, finished)
        compose.mainClock.advanceTimeBy(IntroOverlay.TOTAL_MS + 100)
        assertEquals(1, finished)
    }
}
