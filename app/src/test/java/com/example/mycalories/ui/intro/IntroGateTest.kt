package com.example.mycalories.ui.intro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroGateTest {

    private val gate = IntroGate()

    @Test
    fun `first start does not play it, the system splash already did`() {
        assertFalse(gate.onStart())
    }

    @Test
    fun `coming back after leaving the app plays it`() {
        gate.onStart()
        gate.onStop(changingConfigurations = false)
        assertTrue(gate.onStart())
    }

    @Test
    fun `it plays once per return`() {
        gate.onStart()
        gate.onStop(false)
        gate.onStart()
        assertFalse(gate.onStart())
    }

    @Test
    fun `rotating or switching language does not play it`() {
        gate.onStart()
        gate.onStop(changingConfigurations = true)
        assertFalse(gate.onStart())
    }

    @Test
    fun `returning from the camera, scanner or share sheet does not play it`() {
        gate.onStart()
        gate.onOpeningOtherScreen()
        gate.onStop(false)
        assertFalse(gate.onStart())
    }

    @Test
    fun `after a camera return, leaving the app again plays it next time`() {
        gate.onStart()
        gate.onOpeningOtherScreen()
        gate.onStop(false)
        gate.onStart()
        gate.onStop(false)
        assertTrue(gate.onStart())
    }
}
