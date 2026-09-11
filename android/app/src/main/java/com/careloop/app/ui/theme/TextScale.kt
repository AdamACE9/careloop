package com.careloop.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * CareLoop's own text-size control, layered on top of the system font scale.
 *
 * This is deliberately a second knob rather than a replacement for the system one.
 * Android 14+'s non-linear font scaling already stretches small text more than large
 * text when the *system* setting is raised, which is correct behaviour for the system
 * setting's job — but it means we cannot assume "the system setting is enough" just
 * because it exists. In practice a lot of the target cohort never touches Settings at
 * all (assisted setup by an adult child, see CLAUDE.md §10), so CareLoop needs its own
 * in-context control that this app actually surfaces, not a promise that a system
 * setting somewhere else covers it. Everything this multiplies is `sp` (see Type.kt),
 * never `dp`, so the two controls stack instead of fighting each other.
 */
enum class TextSize(val scale: Float, val label: String) {
    NORMAL(1.0f, "Normal"),
    LARGE(1.15f, "Large"),
    LARGER(1.3f, "Larger"),
    LARGEST(1.5f, "Largest"),
}

private val Context.textScaleDataStore by preferencesDataStore(name = "careloop_text_scale")
private val TEXT_SIZE_KEY = stringPreferencesKey("text_size")

/**
 * Persists the chosen [TextSize] across launches.
 *
 * Kept self-contained here rather than folded into the app's Firebase/mock repository
 * split (see AppContainer) — this is a device-local display preference, not account
 * data, so it has no business going through either backend and no reason to change
 * when one is swapped for the other.
 */
object TextScaleStore {

    fun flow(context: Context): Flow<TextSize> =
        context.textScaleDataStore.data.map { prefs ->
            val stored = prefs[TEXT_SIZE_KEY]
            TextSize.entries.firstOrNull { it.name == stored } ?: TextSize.NORMAL
        }

    suspend fun set(context: Context, size: TextSize) {
        context.textScaleDataStore.edit { prefs ->
            prefs[TEXT_SIZE_KEY] = size.name
        }
    }
}

/**
 * The active text-scale multiplier. Defaults to `1f` so any composable that reads this
 * outside of [CareLoopTextScale] — a preview, a test — still gets a fully legible value
 * rather than an unscaled crash-adjacent surprise.
 */
val LocalTextScale = compositionLocalOf { 1f }

/** Provides [scale] as [LocalTextScale] to [content]. */
@Composable
fun CareLoopTextScale(scale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextScale provides scale, content = content)
}

/**
 * Reads the persisted [TextSize], defaulting to [TextSize.NORMAL] until DataStore has
 * produced its first value — composition proceeds immediately at a legible default
 * rather than blocking the first frame on disk I/O.
 */
@Composable
fun rememberTextSize(): TextSize {
    val context = LocalContext.current
    val size by remember(context) { TextScaleStore.flow(context) }
        .collectAsState(initial = TextSize.NORMAL)
    return size
}
