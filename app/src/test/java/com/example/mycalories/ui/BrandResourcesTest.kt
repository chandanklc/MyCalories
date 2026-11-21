package com.example.mycalories.ui

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.mycalories.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrandResourcesTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `app icon is an adaptive icon with a themed monochrome layer`() {
        val icon = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.foreground)
        assertNotNull(icon.background)
        assertNotNull("Android 13 themed icon", icon.monochrome)
        assertTrue(ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }

    @Test
    fun `icon layers, notification icon and splash animation all load`() {
        listOf(
            R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_background, R.drawable.ic_launcher_monochrome,
            R.drawable.ic_stat_food, R.drawable.splash_icon_animated,
        ).forEach { assertNotNull(ContextCompat.getDrawable(ctx, it)) }
    }

    @Test
    fun `the launch screen uses the splash theme`() {
        val pm = ctx.packageManager
        val info = pm.getActivityInfo(android.content.ComponentName(ctx, com.example.mycalories.MainActivity::class.java), 0)
        assertEquals(R.style.Theme_MyCalories_Starting, info.themeResource)
    }

    @Test
    fun `C4 katori and calculator icon uses white lines on brand green with a saffron total key`() {
        val green = 0xFF1E7F55.toInt()
        assertEquals(green, ContextCompat.getColor(ctx, R.color.brand_green))
        assertEquals(green, ContextCompat.getColor(ctx, R.color.icon_background))
        assertEquals(0xFFFFFFFF.toInt(), ContextCompat.getColor(ctx, R.color.icon_line))
        assertEquals(0xFFFFD58A.toInt(), ContextCompat.getColor(ctx, R.color.icon_accent))
        assertEquals(green, ContextCompat.getColor(ctx, R.color.splash_background))
        assertEquals(0xFFFFFFFF.toInt(), ContextCompat.getColor(ctx, R.color.splash_line))
        assertEquals(0xFFFFD58A.toInt(), ContextCompat.getColor(ctx, R.color.splash_accent))
    }
}
