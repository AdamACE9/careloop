package com.careloop.app.di

import android.util.Log
import com.careloop.app.BuildConfig
import com.careloop.app.data.repository.CareLoopRepository
import com.careloop.app.data.repository.FirebaseCareLoopRepository
import com.careloop.app.data.repository.MockCareLoopRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Manual dependency container.
 *
 * ## Why not Hilt
 *
 * Hilt is the conventional answer and for a large team it is the right one. It
 * was deliberately not used here: it requires KSP and a Gradle plugin whose
 * versions must track the Kotlin version, and drift between those three is one of
 * the most common ways an Android build breaks. What this app actually needs is
 * one swappable binding, and that does not justify the machinery. Google's own
 * architecture guidance treats manual DI as legitimate at this scale.
 *
 * ## The swap
 *
 * [repository] resolves to Firestore when the app has been configured with a
 * Firebase project, and to in-memory demo data when it has not. Nothing above
 * this line knows which one it got.
 *
 * That fallback is not a development convenience left in by accident. It means
 * the app is installable and fully explorable before any backend exists, which
 * matters for demonstrating it, and it means a transient Firebase
 * misconfiguration degrades to something usable rather than a crash on launch.
 */
object AppContainer {

    /**
     * True when google-services.json was present at build time.
     *
     * Set by the Gradle build (see app/build.gradle.kts). Checking a build flag
     * rather than catching an exception at runtime means the decision is made once,
     * visibly, rather than being inferred from a failure.
     */
    val isBackendConfigured: Boolean get() = BuildConfig.FIREBASE_ENABLED

    /**
     * A scope that lives as long as the process.
     *
     * Account provisioning belongs here and not in a screen. It was previously
     * launched from rememberCoroutineScope inside onboarding, which is cancelled
     * the moment that screen leaves the composition. Tapping "Done" therefore
     * cancelled the write that creates the patient record, roughly a second
     * after starting it, and every later call failed with NOT_FOUND.
     *
     * SupervisorJob so one failed write cannot take the others down with it.
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: CareLoopRepository by lazy { createRepository() }

    /** Typed access for demo-only helpers that do not belong on the interface. */
    val mockRepository: MockCareLoopRepository?
        get() = repository as? MockCareLoopRepository

    private fun createRepository(): CareLoopRepository {
        if (!isBackendConfigured) {
            Log.i(TAG, "No Firebase configuration; running on demo data.")
            return MockCareLoopRepository()
        }

        // Guarded because Firebase initialisation can still fail at runtime even
        // when the config file was present at build time, for example if the
        // project was deleted. Falling back keeps the app usable instead of
        // crashing on launch, which for a medication reminder is the difference
        // between degraded and useless.
        return runCatching {
            FirebaseCareLoopRepository(
                db = FirebaseFirestore.getInstance(),
                auth = FirebaseAuth.getInstance(),
                // Region is explicit. The SDK defaults to us-central1, and
                // this project's functions are in Europe alongside its
                // Firestore, so omitting it fails at call time with a
                // not-found that looks like a missing function.
                functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION),
            ) as CareLoopRepository
        }.getOrElse { error ->
            Log.e(TAG, "Firebase failed to initialise; falling back to demo data.", error)
            MockCareLoopRepository()
        }
    }

    /** Must match REGION in functions/src/lib/config.ts. */
    private const val FUNCTIONS_REGION = "europe-west1"

    private const val TAG = "AppContainer"
}
