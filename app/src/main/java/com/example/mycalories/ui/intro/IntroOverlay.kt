package com.example.mycalories.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.mycalories.R
import kotlinx.coroutines.delay

object IntroOverlay {
    /** The katori animation runs about 1 s; it then holds briefly and fades away. */
    const val PLAY_MS = 1000L
    const val FADE_MS = 260
    const val TOTAL_MS = PLAY_MS + FADE_MS
}

/** Same animation as the launch splash, played over the app when you come back to it. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun IntroOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.splash_icon_animated)
    var atEnd by remember { mutableStateOf(false) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        atEnd = true
        delay(IntroOverlay.PLAY_MS)
        fade.animateTo(0f, tween(IntroOverlay.FADE_MS))
        onFinished()
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = fade.value
                val grow = 1f + (1f - fade.value) * 0.08f
                scaleX = grow
                scaleY = grow
            }
            .background(colorResource(R.color.splash_background))
            .pointerInput(Unit) { detectTapGestures { } } // the app underneath can't be tapped mid-intro
            .testTag("intro"),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberAnimatedVectorPainter(image, atEnd), contentDescription = null, modifier = Modifier.size(240.dp))
    }
}
