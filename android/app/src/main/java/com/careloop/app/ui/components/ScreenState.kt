package com.careloop.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.theme.CareDimens

/**
 * The seed value for `collectAsStateWithLifecycle`, before a repository flow's first real
 * emission arrives.
 *
 * Every screen used to seed unconditionally with `MockData`, which was fine as long as
 * [MockCareLoopRepository] was the only repository that existed -- its `StateFlow`s already
 * start at that exact dataset, so there was nothing for the seed to paper over. That stopped
 * being true the moment a real Firebase-backed repository existed: its Firestore listener
 * takes a genuine round trip to emit, so seeding with `MockData` unconditionally means a
 * signed-in stranger sees Margaret's medications for however long that round trip takes.
 * That is the exact bug CLAUDE.md's TASK 1 exists to remove, and it lived in every screen's
 * `collectAsStateWithLifecycle` call, not only in the repository. [demo] is only ever correct
 * once there is no backend to wait on at all.
 */
fun <T> initialSeed(empty: T, demo: T): T =
    if (AppContainer.isBackendConfigured) empty else demo

/**
 * A back control with a visible text label next to the arrow, not an icon alone.
 *
 * [com.careloop.app.ui.screens.medications.MedicationDetailScreen] established this exact
 * row first; pulled out here so the medication form and the manual vitals entry screen
 * don't each redefine it slightly differently.
 */
@Composable
fun CareBackRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = CareDimens.TouchTarget)
            .clickable(onClick = onBack)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
