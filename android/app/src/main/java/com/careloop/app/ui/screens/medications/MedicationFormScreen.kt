package com.careloop.app.ui.screens.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.Criticality
import com.careloop.app.data.model.Medication
import com.careloop.app.data.repository.MedicationInput
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareBackRow
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.initialSeed
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Add or edit a medication.
 *
 * ## Why this exists
 *
 * [MedicationsScreen]'s "Add a medication" button used to be `onClick = {}` -- a real gap
 * called out in CLAUDE.md's TASK 2. This is that flow, for both directions: [medicationId]
 * null means adding a new medication, non-null means editing one that already exists. The
 * two cases share one form because they ask the same questions the same way.
 *
 * ## Why selection over typing
 *
 * This app's design system exists because text entry is a documented onboarding blocker for
 * this cohort (CLAUDE.md §5, §10), so wherever a fact can be a choice instead of a sentence,
 * it is one here: schedule is a set of times of day, not a time picker; criticality is a
 * plain-language question with four big answers, never the internal [Criticality] names
 * ("critical"/"high"/"medium"/"low") CLAUDE.md's TASK 2 explicitly says not to surface; doses
 * on hand is a stepper, not a keyboard. Only name, dose, and the two genuinely optional notes
 * are free text, because there is no honest way to select a medication's name from a list.
 */
@Composable
fun MedicationFormScreen(
    medicationId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medications by AppContainer.repository.observeMedications()
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.medications),
        )
    val existing = medicationId?.let { id -> medications.firstOrNull { it.id == id } }
    val scope = rememberCoroutineScope()

    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    MedicationFormScreenContent(
        existing = existing,
        isSaving = isSaving,
        errorText = errorText,
        onBack = onDone,
        onSave = { input ->
            scope.launch {
                isSaving = true
                errorText = null
                val result = if (existing != null) {
                    AppContainer.repository.updateMedication(existing.id, input)
                } else {
                    AppContainer.repository.addMedication(input)
                }
                isSaving = false
                result
                    .onSuccess { onDone() }
                    .onFailure {
                        errorText = "Cara couldn't save that just now. Please try again."
                    }
            }
        },
        modifier = modifier,
    )
}

// -----------------------------------------------------------------------------
// Selection options
// -----------------------------------------------------------------------------

private data class TimeOfDayOption(val label: String, val time: LocalTime)

/**
 * Four fixed slots rather than a time picker. A precise clock-face drag is exactly the
 * fine-motor task hand tremor defeats (CLAUDE.md §5); picking from four named times of day
 * is a tap. A medication seeded with a schedule that doesn't land on one of these four exact
 * times (only possible for data this form didn't itself create) simply starts with nothing
 * pre-selected here -- see [MedicationFormScreenContent]'s `selectedTimes` for the trade-off.
 */
private val timeOfDayOptions = listOf(
    TimeOfDayOption("Morning", LocalTime.of(8, 0)),
    TimeOfDayOption("Midday", LocalTime.of(12, 0)),
    TimeOfDayOption("Evening", LocalTime.of(18, 0)),
    TimeOfDayOption("Bedtime", LocalTime.of(21, 0)),
)

private data class CriticalityOption(
    val criticality: Criticality,
    val title: String,
    val subtitle: String,
)

/** Plain language for "how important is it that you never miss this?" — never the enum's
 *  own critical/high/medium/low wording, per CLAUDE.md's TASK 2. */
private val criticalityOptions = listOf(
    CriticalityOption(
        Criticality.CRITICAL,
        "Never miss it",
        "Cara follows up quickly if a dose is missed.",
    ),
    CriticalityOption(
        Criticality.HIGH,
        "Try hard not to miss it",
        "Cara keeps a close eye on this one.",
    ),
    CriticalityOption(
        Criticality.MEDIUM,
        "Take it regularly",
        "Part of your normal routine.",
    ),
    CriticalityOption(
        Criticality.LOW,
        "Not urgent",
        "Missing it occasionally is fine.",
    ),
)

// -----------------------------------------------------------------------------
// Content
// -----------------------------------------------------------------------------

@Composable
private fun MedicationFormScreenContent(
    existing: Medication?,
    isSaving: Boolean,
    errorText: String?,
    onBack: () -> Unit,
    onSave: (MedicationInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var dose by remember(existing) { mutableStateOf(existing?.dose ?: "") }
    var purpose by remember(existing) { mutableStateOf(existing?.purpose ?: "") }
    var foodGuidance by remember(existing) { mutableStateOf(existing?.foodGuidance ?: "") }
    var selectedTimes by remember(existing) {
        mutableStateOf(
            existing?.schedule
                ?.let { schedule -> timeOfDayOptions.filter { it.time in schedule }.toSet() }
                ?: emptySet(),
        )
    }
    var criticality by remember(existing) {
        mutableStateOf(existing?.criticality ?: Criticality.MEDIUM)
    }
    var dosesRemaining by remember(existing) {
        mutableStateOf(existing?.dosesRemaining ?: 30)
    }

    val canSave = name.isNotBlank() && dose.isNotBlank() && selectedTimes.isNotEmpty() && !isSaving

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // imePadding, because the keyboard covers the bottom of this form.
            // Both of these forms end in the control that saves the thing, and
            // on a real device the keyboard sat over it: the criticality options
            // and the save button were unreachable, and scrolling could not
            // reveal them because the scroll area itself ended behind the IME.
            // Found by filling the form on a device rather than reading it.
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(CareDimens.ScreenPadding),
    ) {
        CareBackRow(
            label = if (existing != null) "Back to ${existing.name}" else "Back to your medications",
            onBack = onBack,
        )
        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(
            text = if (existing != null) "Edit medication" else "Add a medication",
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        FormLabel("Name")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("e.g. Warfarin", style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CareDimens.TouchTarget),
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        FormLabel("Dose")
        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("e.g. 5mg", style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CareDimens.TouchTarget),
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        FormLabel("What's it for? (optional)")
        OutlinedTextField(
            value = purpose,
            onValueChange = { purpose = it },
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    "e.g. Keeps your blood from clotting",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CareDimens.TouchTarget),
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        FormLabel("When do you take it?")
        Text(
            "Choose every time that applies.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceMd))
        Column(verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm)) {
            timeOfDayOptions.forEach { option ->
                val selected = option in selectedTimes
                CareCard(
                    onClick = {
                        selectedTimes = if (selected) selectedTimes - option else selectedTimes + option
                    },
                    selected = selected,
                ) {
                    SelectableRow(title = option.label, subtitle = null, selected = selected)
                }
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        FormLabel("How important is it that you never miss this?")
        Spacer(Modifier.height(CareDimens.SpaceMd))
        Column(verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm)) {
            criticalityOptions.forEach { option ->
                val selected = criticality == option.criticality
                CareCard(
                    onClick = { criticality = option.criticality },
                    selected = selected,
                ) {
                    SelectableRow(title = option.title, subtitle = option.subtitle, selected = selected)
                }
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        FormLabel("How many doses do you have on hand?")
        Spacer(Modifier.height(CareDimens.SpaceMd))
        DosesRemainingStepper(value = dosesRemaining, onChange = { dosesRemaining = it.coerceAtLeast(0) })

        Spacer(Modifier.height(CareDimens.SpaceXl))

        FormLabel("Any food tip for Cara to mention? (optional)")
        OutlinedTextField(
            value = foodGuidance,
            onValueChange = { foodGuidance = it },
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    "e.g. Take with food to avoid an upset stomach",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CareDimens.TouchTarget),
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        if (errorText != null) {
            Text(errorText, style = MaterialTheme.typography.bodyMedium, color = CareColors.Concern)
            Spacer(Modifier.height(CareDimens.SpaceMd))
        }

        CarePrimaryButton(
            text = if (existing != null) "Save changes" else "Add this medication",
            icon = Icons.Rounded.Check,
            enabled = canSave,
            onClick = {
                onSave(
                    MedicationInput(
                        name = name.trim(),
                        dose = dose.trim(),
                        purpose = purpose.trim(),
                        schedule = selectedTimes.map { it.time }.sorted(),
                        criticality = criticality,
                        dosesRemaining = dosesRemaining,
                        dosesPerDay = selectedTimes.size,
                        refillLeadTimeDays = existing?.refillLeadTimeDays ?: 7,
                        foodGuidance = foodGuidance.trim().ifBlank { null },
                    ),
                )
            },
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))
    }
}

@Composable
private fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
    Spacer(Modifier.height(CareDimens.SpaceSm))
}

@Composable
private fun SelectableRow(title: String, subtitle: String?, selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Icon(Icons.Rounded.Check, contentDescription = null, tint = CareColors.Navy)
        }
    }
}

@Composable
private fun DosesRemainingStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(icon = Icons.Rounded.Remove, description = "Fewer doses on hand") {
            onChange(value - 1)
        }
        Text(text = "$value", style = MaterialTheme.typography.displayMedium)
        StepperButton(icon = Icons.Rounded.Add, description = "More doses on hand") {
            onChange(value + 1)
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(CareDimens.TouchTarget)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = CareColors.Navy, modifier = Modifier.size(28.dp))
    }
}

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

@Preview(showBackground = true, name = "Add medication")
@Composable
private fun MedicationFormScreenAddPreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationFormScreenContent(
            existing = null,
            isSaving = false,
            errorText = null,
            onBack = {},
            onSave = {},
        )
    }
}

@Preview(showBackground = true, name = "Edit medication")
@Composable
private fun MedicationFormScreenEditPreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationFormScreenContent(
            existing = MockData.warfarin,
            isSaving = false,
            errorText = null,
            onBack = {},
            onSave = {},
        )
    }
}
