package com.careloop.app.ui.screens.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.Medication
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CareEmptyState
import com.careloop.app.ui.components.CareTipCallout
import com.careloop.app.ui.components.StatusPill
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * One medication, in full — what it's for, when it's taken, how much is left, and any food
 * guidance, in the same plain language and the same visual language as the list screen.
 *
 * ## Why this exists
 *
 * [MedicationsScreen]'s `onMedicationClick` used to be wired to nowhere — a real gap noted
 * in CLAUDE.md §7/§13. A tap that goes nowhere is worse than no tap at all on a screen built
 * for this cohort: it reads as the app being broken, not as "there's nothing more to see
 * here." This screen is the destination.
 *
 * ## Why it repeats rather than reinvents
 *
 * The criticality pill, the food tip, and the schedule wording are the exact same functions
 * [MedicationsScreen] already uses ([criticalityPresentation], [CareTipCallout],
 * [scheduleText]). A medication's story has to read identically whether Margaret meets it on
 * the list or taps into it — two slightly different descriptions of the same pill would cost
 * more trust than the convenience of a bespoke detail layout is worth.
 *
 * ## What's new here, and why
 *
 * The two stat tiles (doses left, days of supply) are new — the list card already states
 * "days of supply left" as one line of text, but a detail screen earns a real graphic: two
 * big numbers a glance can absorb, rather than a sentence that has to be read. [FlowRow], not
 * [Row], because two tiles that comfortably sit side by side at normal text size do not at
 * 200% — see the note on [StatTile] below.
 */
@Composable
fun MedicationDetailScreen(
    medicationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same seed-with-mock-data pattern as MedicationsScreen: the list flow is already live
    // elsewhere, so this just finds the one medication in it rather than asking the
    // repository for a lookup shape it doesn't expose.
    val medications by AppContainer.repository.observeMedications()
        .collectAsStateWithLifecycle(initialValue = MockData.medications)
    val medication = medications.firstOrNull { it.id == medicationId }

    MedicationDetailScreenContent(
        medication = medication,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun MedicationDetailScreenContent(
    medication: Medication?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(CareDimens.ScreenPadding),
    ) {
        BackRow(onBack = onBack)
        Spacer(Modifier.height(CareDimens.SpaceLg))

        if (medication == null) {
            // Reachable only if a medication is removed while this screen is open — the mock
            // repository never does this today, but a real backend swap could, and a blank
            // screen would read as broken rather than "this was removed."
            CareEmptyState(
                title = "This medication is no longer on your list",
                whatHappensNext = "Go back and Cara's current list will be right there.",
            )
        } else {
            MedicationDetailBody(medication)
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // A text label alongside the arrow, not an icon-only button — icon-only controls are the
    // one thing this whole app's evidence base is strictest about, and a back button is not
    // an exception just because most apps treat it as one.
    Row(
        modifier = modifier
            .heightIn(min = CareDimens.TouchTarget)
            .clickable(onClick = onBack)
            .semantics { contentDescription = "Back to your medications" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text("Back to your medications", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MedicationDetailBody(medication: Medication, modifier: Modifier = Modifier) {
    val criticalityInfo = criticalityPresentation(medication.criticality)

    Column(modifier = modifier) {
        Text(medication.name, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            medication.dose,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceMd))
        StatusPill(
            text = criticalityInfo.text,
            icon = criticalityInfo.icon,
            contentColor = criticalityInfo.contentColor,
            containerColor = criticalityInfo.containerColor,
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        CareCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocalPharmacy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text("What it's for", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(CareDimens.SpaceMd))
            Text(medication.purpose, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        CareCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text("When you take it", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(CareDimens.SpaceMd))
            Text(
                scheduleText(medication.schedule),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        StatTiles(medication)

        val foodGuidance = medication.foodGuidance
        if (foodGuidance != null) {
            Spacer(Modifier.height(CareDimens.SpaceLg))
            CareTipCallout(
                icon = Icons.Outlined.Restaurant,
                label = "A tip from Cara",
                text = foodGuidance,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))
    }
}

/**
 * Two big-number stat tiles: doses left, and the days of supply that works out to.
 *
 * [FlowRow], not [Row], per the same rule as [RefillPlanningCard] in [MedicationsScreen] —
 * two tiles side by side is comfortable at normal text size but not at 200%, where each
 * tile's own number and label grow enough that forcing both into one line would either
 * clip or crush them. Letting them wrap to stack vertically is the whole point of the
 * "avoid fixed heights, let content size containers" rule: nothing here is asked to fit a
 * width it no longer fits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatTiles(medication: Medication, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
    ) {
        StatTile(
            icon = Icons.Rounded.Medication,
            value = "${medication.dosesRemaining}",
            label = "Doses left",
        )
        StatTile(
            icon = Icons.Outlined.Schedule,
            value = "${medication.daysOfSupplyRemaining}",
            label = "Days of supply",
        )
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 150.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(CareDimens.CardRadius))
            .padding(CareDimens.SpaceLg),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = CareColors.Navy,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(value, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Medication detail, warfarin")
@Composable
private fun MedicationDetailScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationDetailScreenContent(medication = MockData.warfarin, onBack = {})
    }
}

@Preview(showBackground = true, name = "Medication detail, no food guidance")
@Composable
private fun MedicationDetailScreenNoFoodPreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationDetailScreenContent(medication = MockData.ramipril, onBack = {})
    }
}

@PreviewFontScale
@Composable
private fun MedicationDetailScreenFontScalePreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationDetailScreenContent(medication = MockData.warfarin, onBack = {})
    }
}
