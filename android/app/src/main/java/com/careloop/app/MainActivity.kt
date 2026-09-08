package com.careloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.careloop.app.call.CallNotifier
import com.careloop.app.ui.navigation.CareLoopApp
import com.careloop.app.ui.screens.onboarding.OnboardingScreen
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * App entry point.
 *
 * Onboarding state is held in memory rather than persisted, deliberately: on stage the
 * onboarding flow needs to be re-runnable on demand, and a persisted "seen it" flag would
 * mean clearing app data between demos.
 *
 * TODO(backend): persist completion (DataStore) and skip onboarding for returning users.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Registered up front so the channel exists before any call can arrive. Creating a
        // channel is idempotent and does not prompt the user.
        CallNotifier.ensureChannel(this)

        setContent {
            CareLoopTheme {
                var onboardingComplete by rememberSaveable { mutableStateOf(false) }

                if (onboardingComplete) {
                    CareLoopApp()
                } else {
                    OnboardingScreen(onComplete = { onboardingComplete = true })
                }
            }
        }
    }
}
