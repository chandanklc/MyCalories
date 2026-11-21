package com.example.mycalories.ui.intro

/**
 * Decides when the in-app intro animation plays.
 *
 * Android shows the system splash only when the app starts fresh. To play the animation on every open,
 * the intro also plays when the user comes back after leaving the app (Home, recents, another app),
 * but not after rotating, switching language, or returning from a screen the app itself opened
 * (camera, barcode scanner, share sheet, Health Connect).
 */
class IntroGate {
    private var leftApp = false
    private var openingOtherScreen = false

    /** The app is about to open another screen (camera, scanner, share sheet…) and will come straight back. */
    fun onOpeningOtherScreen() {
        openingOtherScreen = true
    }

    fun onStop(changingConfigurations: Boolean) {
        if (!changingConfigurations && !openingOtherScreen) leftApp = true
    }

    /** True when the intro should play now. */
    fun onStart(): Boolean {
        val play = leftApp
        leftApp = false
        openingOtherScreen = false
        return play
    }
}
