package com.bcw.classeviva.ui.screens.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import com.bcw.classeviva.data.AppModel

object Onboarding {
    var monochrome = false
    fun isFirstRun(context: Context, model: AppModel): Boolean = false
}

@Composable fun OnboardingScreen(onFinish: () -> Unit) { onFinish() }
