package com.careloop.app.ui.screens.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.ElderResponse
import com.careloop.app.data.model.ShareCategory
import com.careloop.app.data.model.SharedItem
import com.careloop.app.data.model.SharingPreferences
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.CareSecondaryButton
import com.careloop.app.ui.components.SectionHeader
import com.careloop.app.ui.components.StatusPill
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * "What I've told Sarah" — CareLoop's answer to a specific, evidenced problem.
 *
 * ## Why this screen exists
 *
 * Research on passive monitoring for older adults keeps landing on the same uncomfortable
 * number: roughly half of them are uneasy with it even when they accept the safety case.
 * The uneasiness doesn't come from *what* is shared — it comes from not knowing, and not
 * having a say. Softer privacy copy doesn't move that number. **Perceived control does.**
 * No shipped product we could find actually gives the elder that control; most either hide
 * the sharing entirely or bury a static disclosure in a settings page nobody re-reads.
 *
 * The design principle here is **symmetric transparency**: Margaret sees the exact same
 * thing Sarah sees, in the same words, at the same time — and, critically, she can talk
 * back. That last part is what turns "monitoring" into "being kept company by someone who
 * checks with you first."
 *
 * ## The three parts, and why each is shaped the way it is
 *
 * A. **The feed.** Every [SharedItem] is Cara's own account of what she told Sarah, written
 *    to Margaret, not about her. An item Margaret hasn't reacted to yet gets two big, plain
 *    buttons — agree, or add her own words — because a feed she can only read is not
 *    control, it's a window. `shr-3` already shows the far end of that flow: Margaret
 *    disputed something, and her own words are shown as prominently as Cara's, because a
 *    correction that vanishes into a database is worse than no correction screen at all.
 *
 * B. **Preferences.** Four independent switches, not one master toggle. A single on/off
 *    would technically satisfy "the elder can control sharing," but granular, per-category
 *    control is specifically what the research ties to reduced discomfort — a blanket
 *    switch reads as all-or-nothing and doesn't give Margaret a real choice to make.
 *
 * C. **The safety floor.** [SharingPreferences.alwaysShareUrgent] means a genuine emergency
 *    reaches Sarah no matter what Margaret has switched off. We say so, plainly, in the same
 *    screen as the switches that look like they control everything — because a safety floor
 *    that only appears in fine print during onboarding is functionally a secret, and a
 *    secret safety net is exactly the paternalism this feature is meant to replace with
 *    something honest.
 *
 * Tone matters as much as structure: Margaret is an adult who owns her own information.
 * Nothing here is phrased as being "for her own good," and nothing treats her correction as
 * a complaint to be managed — it's the whole point of the screen.
 *
 * As with [com.careloop.app.ui.screens.history.HistoryScreen], the stateful entry point is
 * kept separate from the rendering ([WhatISharedScreenContent]), so the `@Preview` doesn't
 * depend on a real `LifecycleOwner` for [collectAsStateWithLifecycle].
 */
@Composable
fun WhatISharedScreen(
    modifier: Modifier = Modifier,
) {
    val sharedItems by AppContainer.repository.observeSharedItems()
        .collectAsStateWithLifecycle(initialValue = MockData.sharedItems)
    val preferences by AppContainer.repository.observeSharingPreferences()
        .collectAsStateWithLifecycle(initialValue = MockData.sharingPreferences)

    val scope = rememberCoroutineScope()

    WhatISharedScreenContent(
        sharedItems = sharedItems,
        preferences = preferences,
        modifier = modifier,
        onRespond = { itemId, response, note ->
            scope.launch {
                AppContainer.repository.respondToSharedItem(itemId, response, note)
            }
        },
        onToggleCategory = { category, enabled ->
            val updatedCategories = if (enabled) {
                preferences.enabledCategories + category
            } else {
                preferences.enabledCategories - category
            }
            scope.launch {
                AppContainer.repository.updateSharingPreferences(
                    preferences.copy(enabledCategories = updatedCategories),
                )
            }
        },
        onTogglePrivacyHold = { pausing ->
            // TODO(backend): a client-side flag is enough for this demo, but the hold must
            // ultimately be enforced by the agent/server policy layer that decides whether to
            // share something — not merely read here — so that muting can never be bypassed
            // by a client that doesn't check it. The safety floor (alwaysShareUrgent) must
            // stay enforced there too, independent of this flag.
            val until = if (pausing) LocalDateTime.now().plusHours(24) else null
            scope.launch {
                AppContainer.repository.updateSharingPreferences(
                    preferences.copy(privacyHoldUntil = until),
                )
            }
        },
    )
}

@Composable
private fun WhatISharedScreenContent(
    sharedItems: List<SharedItem>,
    preferences: SharingPreferences,
    onRespond: (itemId: String, response: ElderResponse, note: String?) -> Unit,
    onToggleCategory: (ShareCategory, Boolean) -> Unit,
    onTogglePrivacyHold: (pausing: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedItems = remember(sharedItems) { sharedItems.sortedByDescending { it.sharedAt } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = CareDimens.ScreenPadding,
            vertical = CareDimens.SpaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
    ) {
        item(key = "header") {
            Column {
                Text("What I've told Sarah", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                Text(
                    "Sarah only ever sees what's written here, the same words, at the " +
                        "same time as you. If something's not quite right, you can say so.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- A. The feed --------------------------------------------------
        items(sortedItems, key = { it.id }) { item ->
            SharedItemCard(
                item = item,
                onConfirm = { onRespond(item.id, ElderResponse.CONFIRMED, null) },
                onDispute = { note -> onRespond(item.id, ElderResponse.DISPUTED, note) },
            )
        }

        // ---- B. Preferences -------------------------------------------------
        item(key = "preferences") {
            Column {
                SectionHeader(
                    title = "What you share automatically",
                    subtitle = "Turn any of these off whenever you like, Cara will simply " +
                        "stay quiet about it with Sarah.",
                )
                SharingPreferencesCard(
                    enabledCategories = preferences.enabledCategories,
                    onToggleCategory = onToggleCategory,
                )
            }
        }

        // ---- C. The safety floor, stated plainly ---------------------------
        item(key = "safety-floor") {
            Column {
                SectionHeader(title = "If something's seriously wrong")
                SafetyFloorCard()
            }
        }

        item(key = "privacy-hold") {
            PrivacyHoldSection(
                preferences = preferences,
                onTogglePrivacyHold = onTogglePrivacyHold,
            )
        }

        item(key = "bottom-space") {
            Spacer(Modifier.height(CareDimens.SpaceLg))
        }
    }
}

// ---------------------------------------------------------------------------
// A. The feed
// ---------------------------------------------------------------------------

/**
 * One entry in the feed. Holds its own tiny bit of UI state for the "add something" flow —
 * whether the note field is open, and its current draft text — scoped to this card via
 * `remember(item.id)`, so each item's in-progress note survives recomposition but never
 * leaks into another item's state.
 */
@Composable
private fun SharedItemCard(
    item: SharedItem,
    onConfirm: () -> Unit,
    onDispute: (note: String) -> Unit,
) {
    var isAddingNote by remember(item.id) { mutableStateOf(false) }
    var noteDraft by remember(item.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            // Category + when, both plain text/icon — never colour standing alone for meaning.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text(
                    text = item.category.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text(
                    text = "· ${formatSharedAt(item.sharedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(CareDimens.SpaceMd))

            Text(
                text = "Cara told Sarah:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Text(
                text = item.whatCaraSaid,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(CareDimens.SpaceLg))

            when (item.elderResponse) {
                ElderResponse.NOT_YET_SEEN -> {
                    Text(
                        text = "Does that sound right to you?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(CareDimens.SpaceMd))
                    CarePrimaryButton(
                        text = "That's right",
                        icon = Icons.Filled.Check,
                        onClick = onConfirm,
                    )
                    Spacer(Modifier.height(CareDimens.SpaceSm))
                    CareSecondaryButton(
                        text = "I'd add something",
                        icon = Icons.Filled.ChatBubbleOutline,
                        onClick = { isAddingNote = !isAddingNote },
                    )

                    if (isAddingNote) {
                        Spacer(Modifier.height(CareDimens.SpaceMd))
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp),
                            placeholder = {
                                Text(
                                    "What would you like Sarah to know?",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(CareDimens.SpaceSm))
                        CarePrimaryButton(
                            text = "Share this with Sarah",
                            icon = Icons.Filled.Check,
                            enabled = noteDraft.isNotBlank(),
                            onClick = {
                                onDispute(noteDraft.trim())
                                isAddingNote = false
                            },
                        )
                    }
                }

                ElderResponse.CONFIRMED -> {
                    StatusPill(
                        text = "You confirmed this",
                        icon = Icons.Filled.Check,
                        contentColor = CareColors.Good,
                        containerColor = CareColors.GoodSurface,
                    )
                }

                ElderResponse.DISPUTED -> {
                    StatusPill(
                        text = "You added your own words",
                        icon = Icons.Filled.ChatBubbleOutline,
                        contentColor = CareColors.Good,
                        containerColor = CareColors.GoodSurface,
                    )
                    if (!item.elderNote.isNullOrBlank()) {
                        Spacer(Modifier.height(CareDimens.SpaceMd))
                        ElderNoteCallout(note = item.elderNote)
                    }
                }
            }
        }
    }
}

/**
 * Margaret's own correction, given the same visual weight as Cara's statement above it —
 * on purpose. A dispute that reads smaller or greyer than the thing it's correcting would
 * quietly tell her the correction didn't really count. It should look like it landed.
 */
@Composable
private fun ElderNoteCallout(note: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CareColors.GoodSurface, RoundedCornerShape(CareDimens.ButtonRadius))
            .padding(CareDimens.SpaceMd),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = CareColors.Good,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text(
                    text = "In your own words:",
                    style = MaterialTheme.typography.titleMedium,
                    color = CareColors.Good,
                )
            }
            Spacer(Modifier.height(CareDimens.SpaceSm))
            Text(
                text = "“$note”",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun ShareCategory.icon(): ImageVector = when (this) {
    ShareCategory.MISSED_DOSES -> Icons.Filled.ErrorOutline
    ShareCategory.CONFUSION -> Icons.Filled.ChatBubbleOutline
    ShareCategory.VITALS -> Icons.Filled.Info
    ShareCategory.REFILLS -> Icons.Filled.Schedule
}

// ---------------------------------------------------------------------------
// B. Preferences
// ---------------------------------------------------------------------------

/**
 * Four switches, not one. Each category is a decision Margaret can make on its own terms —
 * she might be entirely comfortable with Sarah knowing about a missed dose but not want her
 * day-to-day blood pressure numbers shared, and that distinction is the whole reason this
 * is granular rather than a single "share with family" toggle.
 */
@Composable
private fun SharingPreferencesCard(
    enabledCategories: Set<ShareCategory>,
    onToggleCategory: (ShareCategory, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(horizontal = CareDimens.SpaceLg)) {
            ShareCategory.entries.forEachIndexed { index, category ->
                CategoryToggleRow(
                    category = category,
                    checked = category in enabledCategories,
                    onCheckedChange = { onToggleCategory(category, it) },
                )
                if (index != ShareCategory.entries.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryToggleRow(
    category: ShareCategory,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.TouchTarget)
            .padding(vertical = CareDimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            category.icon(),
            contentDescription = null,
            tint = CareColors.Navy,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Text(
                category.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(CareDimens.SpaceMd))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------------------------------------------------------------------
// C. The safety floor
// ---------------------------------------------------------------------------

/**
 * Stated in the same breath as the switches that look like they control everything. Burying
 * this in onboarding-only copy would let Margaret believe the switches above are absolute —
 * they aren't, and telling her so here, next to them, is the honest version of the same
 * safety guarantee a hidden one would give her anyway. She just also gets to know about it.
 */
@Composable
private fun SafetyFloorCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Row(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = CareColors.Navy,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Column {
                Text("Emergencies always reach Sarah", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                Text(
                    "Even with everything above switched off, a genuine emergency still " +
                        "reaches Sarah. That means Cara not being able to reach you, or " +
                        "something seriously wrong. It is not a setting you can turn off, " +
                        "and we would rather tell you plainly than have you find out by " +
                        "surprise.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A temporary, whole-categories-muted pause — separate from the per-category switches above,
 * for the times Margaret just wants a quiet day without reconfiguring anything permanently.
 * UI-only for now; see the TODO(backend) in [WhatISharedScreen] for what real enforcement
 * needs.
 */
@Composable
private fun PrivacyHoldSection(
    preferences: SharingPreferences,
    onTogglePrivacyHold: (pausing: Boolean) -> Unit,
) {
    Column {
        if (preferences.isOnPrivacyHold) {
            Text(
                text = "Sharing is paused until ${formatHoldUntil(preferences.privacyHoldUntil)}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(CareDimens.SpaceMd))
            CareSecondaryButton(
                text = "Resume sharing now",
                icon = Icons.Filled.Check,
                onClick = { onTogglePrivacyHold(false) },
            )
        } else {
            CareSecondaryButton(
                text = "Pause sharing for today",
                icon = Icons.Filled.Schedule,
                onClick = { onTogglePrivacyHold(true) },
            )
            Spacer(Modifier.height(CareDimens.SpaceSm))
            Text(
                text = "Emergencies still reach Sarah even while paused.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

private fun formatSharedAt(dateTime: LocalDateTime, today: LocalDate = LocalDate.now()): String {
    val date = dateTime.toLocalDate()
    val time = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))
    val day = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }
    return "$day, $time"
}

private fun formatHoldUntil(dateTime: LocalDateTime?): String {
    if (dateTime == null) return ""
    return dateTime.format(DateTimeFormatter.ofPattern("h:mm a, EEEE"))
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 400, heightDp = 1200)
@Composable
private fun WhatISharedScreenPreview() {
    CareLoopTheme {
        WhatISharedScreenContent(
            sharedItems = MockData.sharedItems,
            preferences = MockData.sharingPreferences,
            onRespond = { _, _, _ -> },
            onToggleCategory = { _, _ -> },
            onTogglePrivacyHold = {},
        )
    }
}
