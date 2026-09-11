package com.careloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.call.CallNotifier
import com.careloop.app.data.local.OnboardingStore
import com.careloop.app.ui.navigation.CareLoopApp
import com.careloop.app.ui.screens.onboarding.OnboardingScreen
import com.careloop.app.ui.theme.CareLoopTheme
import kotlinx.coroutines.launch

/**
 * App entry point.
 *
 * Onboarding completion is persisted. It used to be held in memory so the flow could be
 * replayed on demand during a demo, which was the right call while the app ran on example
 * data and the wrong one the moment setup started signing people in, writing their patient
 * record and minting linking codes. Replaying it is now a deliberate action rather than a
 * side effect of every cold start.
 *
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, which is where the library installs itself. It
        // also swaps the activity over to the post-splash theme, so the navy
        // launch background does not persist behind the whole app.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Registered up front so the channel exists before any call can arrive. Creating a
        // channel is idempotent and does not prompt the user.
        CallNotifier.ensureChannel(this)

        setContent {
            CareLoopTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // null while the stored flag is still being read. Rendering
                // onboarding during that gap would flash the setup flow at
                // someone who finished it weeks ago, every single launch.
                val completed by OnboardingStore.completed(context)
                    .collectAsStateWithLifecycle(initialValue = null as Boolean?)

                when (completed) {
                    null -> Unit
                    true -> CareLoopApp()
                    false -> OnboardingScreen(
                        onComplete = {
                            scope.launch { OnboardingStore.markCompleted(context) }
                        },
                    )
                }
            }
        }
    }
}
