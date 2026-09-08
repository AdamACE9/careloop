package com.careloop.app.di

import com.careloop.app.data.repository.CareLoopRepository
import com.careloop.app.data.repository.MockCareLoopRepository

/**
 * Manual dependency container.
 *
 * ## Why not Hilt
 *
 * Hilt is the conventional answer, and for a large team it is the right one. It was
 * deliberately not used here: it requires KSP and a Hilt Gradle plugin whose versions must
 * align with the Kotlin version, and version drift between those three is one of the most
 * common ways an Android build breaks. For a frontend running entirely on mock data, that
 * risk buys nothing — the only thing we actually need is one swappable binding.
 *
 * Google's own architecture guidance treats manual DI as legitimate at this scale.
 *
 * ## Swapping in the real backend
 *
 * Change one line:
 * ```
 * val repository: CareLoopRepository = FirebaseCareLoopRepository(Firebase.firestore)
 * ```
 * No screen, ViewModel, or navigation code should need to change. If it does, something
 * has leaked through the repository interface and should be pushed back behind it.
 *
 * If DI needs grow (multiple scopes, real tests, per-user instances), migrating this to
 * Hilt later is mechanical — the interface boundary is already in the right place.
 */
object AppContainer {

    /** The one line that changes when the backend lands. */
    val repository: CareLoopRepository by lazy { MockCareLoopRepository() }

    /** Typed access for demo-only helpers that don't belong on the interface. */
    val mockRepository: MockCareLoopRepository?
        get() = repository as? MockCareLoopRepository
}
