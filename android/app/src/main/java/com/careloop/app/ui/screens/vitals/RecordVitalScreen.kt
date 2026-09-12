package com.careloop.app.ui.screens.vitals

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.model.VitalType
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareBackRow
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import kotlinx.coroutines.launch

/**
 * Manual "record a reading" flow.
 *
 * ## Why this exists
 *
 * [VitalsScreen]'s "Record a reading" button used to be a `TODO(backend)` that did nothing --
 * a real gap called out in CLAUDE.md's TASK 3. This is that flow: a reading taken between
 * calls, e.g. at a pharmacy blood-pressure machine, rather than the ones Cara asks for live
 * during a check-in.
 *
 * ## Why it writes exactly these fields
 *
 * `firestore.rules`' `vitals` create rule is unusually strict for this codebase: the document
 * must have EXACTLY the keys `type, value, secondaryValue, recordedAt, source`, `source` must
 * equal `"manual"`, and `value` must be a number. [CareLoopRepository.recordVitalReading]
 * is where that shape actually gets built; this screen's only job is collecting a type and a
 * number (or two, for blood pressure) large enough to enter without precision typing.
 */
@Composable
fun RecordVitalScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    RecordVitalScreenContent(
        isSaving = isSaving,
        errorText = errorText,
        onBack = onDone,
        onSave = { type, value, secondaryValue ->
            scope.launch {
                isSaving = true
                errorText = null
                val result = AppContainer.repository.recordVitalReading(type, value, secondaryValue)
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

@Composable
private fun RecordVitalScreenContent(
    isSaving: Boolean,
    errorText: String?,
    onBack: () -> Unit,
    onSave: (VitalType, Float, Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf<VitalType?>(null) }
    // Re-keyed on the selected type: switching from blood pressure to blood sugar shouldn't
    // leave a stray diastolic value behind that this screen goes on to silently ignore.
    var primaryText by remember(selectedType) { mutableStateOf("") }
    var secondaryText by remember(selectedType) { mutableStateOf("") }

    val needsSecondary = selectedType == VitalType.BLOOD_PRESSURE
    val primaryValue = primaryText.toFloatOrNull()
    val secondaryValue = secondaryText.toFloatOrNull()
    val canSave = selectedType != null &&
        primaryValue != null &&
        (!needsSecondary || secondaryValue != null) &&
        !isSaving

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
        CareBackRow(label = "Back to your readings", onBack = onBack)
        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text("Record a reading", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            "For a reading you took between calls, like at the pharmacy.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        Text("What did you measure?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(CareDimens.SpaceMd))
        Column(verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm)) {
            VitalType.entries.forEach { type ->
                val selected = type == selectedType
                CareCard(
                    onClick = { selectedType = type },
                    selected = selected,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.displayName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(CareDimens.SpaceXs))
                            Text(
                                "Measured in ${type.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = CareColors.Navy)
                        }
                    }
                }
            }
        }

        val type = selectedType
        if (type != null) {
            Spacer(Modifier.height(CareDimens.SpaceXl))

            if (needsSecondary) {
                Text("Systolic (the higher number)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                NumberField(value = primaryText, onValueChange = { primaryText = it }, placeholder = "e.g. 128")

                Spacer(Modifier.height(CareDimens.SpaceLg))

                Text("Diastolic (the lower number)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                NumberField(value = secondaryText, onValueChange = { secondaryText = it }, placeholder = "e.g. 82")
            } else {
                Text("Your reading, in ${type.unit}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                NumberField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    placeholder = "e.g. ${examplePlaceholder(type)}",
                )
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        if (errorText != null) {
            Text(errorText, style = MaterialTheme.typography.bodyMedium, color = CareColors.Concern)
            Spacer(Modifier.height(CareDimens.SpaceMd))
        }

        CarePrimaryButton(
            text = "Save this reading",
            icon = Icons.Rounded.Check,
            enabled = canSave,
            onClick = {
                // canSave already gates whether this button is even tappable; these locals
                // just give the compiler non-null types to hand to onSave without a second,
                // looser set of nullability rules living here alongside it.
                val savedType = selectedType
                val value = primaryValue
                if (savedType != null && value != null) {
                    onSave(savedType, value, if (needsSecondary) secondaryValue else null)
                }
            },
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineLarge,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.headlineLarge) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.LargeTouchTarget),
    )
}

private fun examplePlaceholder(type: VitalType): String = when (type) {
    VitalType.BLOOD_SUGAR -> "5.6"
    VitalType.HEART_RATE -> "72"
    VitalType.WEIGHT -> "68"
    VitalType.BLOOD_PRESSURE -> "128"
}

// -----------------------------------------------------------------------------
// Preview
// -----------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun RecordVitalScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        RecordVitalScreenContent(
            isSaving = false,
            errorText = null,
            onBack = {},
            onSave = { _, _, _ -> },
        )
    }
}
