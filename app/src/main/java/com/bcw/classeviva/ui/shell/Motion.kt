package com.bcw.classeviva.ui.shell

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.spring

/** Transizioni di Material 3 (asse condiviso, dissolvenza incrociata), come `Motion.swift` dell'app. */
object Transitions {
    /** Asse condiviso X: giorni, schede dei Voti, pagine avanti e indietro. `dir` = +1 avanti, -1 indietro. */
    fun sharedAxisX(dir: Int, reduced: Boolean = false): ContentTransform {
        if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(100))
        val d = if (dir < 0) -1 else 1
        val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 700f)
        return (slideInHorizontally(slide) { (it * 0.18f * d).toInt() } + fadeIn(tween(210, delayMillis = 60, easing = LinearOutSlowInEasing))) togetherWith
            (slideOutHorizontally(slide) { (-it * 0.18f * d).toInt() } + fadeOut(tween(90, easing = FastOutSlowInEasing)))
    }

    /** Asse condiviso Y (es. passaggi dell'introduzione). */
    fun sharedAxisY(dir: Int, reduced: Boolean = false): ContentTransform {
        if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(100))
        val d = if (dir < 0) -1 else 1
        val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 600f)
        return (slideInVertically(slide) { (it * 0.12f * d).toInt() } + fadeIn(tween(210, delayMillis = 60))) togetherWith
            (slideOutVertically(slide) { (-it * 0.12f * d).toInt() } + fadeOut(tween(90)))
    }

    /** Dissolvenza incrociata di M3: contenuti senza relazione (ricerca, Lista/Giorno, schede). */
    fun fadeThrough(reduced: Boolean = false): ContentTransform {
        if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(100))
        return (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) + scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.94f)) togetherWith
            fadeOut(tween(90, easing = FastOutSlowInEasing))
    }
}
