package com.careloop.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "careloop_onboarding")
private val COMPLETED = booleanPreferencesKey("completed")

/**
 * Remembers that setup is finished.
 *
 * This was deliberately NOT persisted while the app ran on demo data, so the
 * onboarding flow could be replayed on demand during a demonstration. That
 * reasoning expired the moment onboarding started doing real work: it now signs
 * the person in, writes their patient record, and can mint a linking code. An
 * 80-year-old reopening the app should not be walked through account setup
 * again, and certainly should not be handed a second code that invalidates the
 * one they already read out to their daughter.
 *
 * Replaying it is still possible and is now an explicit choice rather than an
 * accident of every cold start. See [reset].
 */
object OnboardingStore {

    fun completed(context: Context): Flow<Boolean> =
        context.onboardingDataStore.data.map { prefs -> prefs[COMPLETED] ?: false }

    suspend fun markCompleted(context: Context) {
        context.onboardingDataStore.edit { prefs -> prefs[COMPLETED] = true }
    }

    /** Replays setup on the next launch. Used by the demo control in Settings. */
    suspend fun reset(context: Context) {
        context.onboardingDataStore.edit { prefs -> prefs[COMPLETED] = false }
    }
}
