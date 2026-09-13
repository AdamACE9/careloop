package com.careloop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.careloop.app.data.model.Condition
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import java.time.Year

/**
 * The two things onboarding never asked, used by onboarding and by Settings.
 *
 * Without a birth year every account was "0 years old", on the dashboard and in
 * what Cara was told. Without conditions Cara had no reason to ask about blood
 * pressure or blood sugar, so she never did.
 *
 * Both are selection, not typing. Text entry is the single biggest onboarding
 * blocker for this audience, so the year is a stepper with a large number and
 * the conditions are plain-language rows with a tick.
 */

/** A sensible starting year for the stepper when none has been chosen. */
fun defaultBirthYear(): Int = Year.now().value - 70

@Composable
fun BirthYearPicker(
    birthYear: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = Year.now().value
    val shown = birthYear ?: defaultBirthYear()

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundStepper(symbol = "−", description = "One year earlier") {
                onChange((shown - 1).coerceAtLeast(current - 110))
            }
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 150.dp),
            )
            RoundStepper(symbol = "+", description = "One year later") {
                onChange((shown + 1).coerceAtMost(current - 1))
            }
        }
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            text = if (birthYear == null) "Tap + or − to set your year" else "About ${current - shown} years old",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        // Ten-year jumps, because stepping from 1990 to 1948 one tap at a time
        // is forty taps, which is its own accessibility failure.
        Row(horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm)) {
            TextButton(onClick = { onChange((shown - 10).coerceAtLeast(current - 110)) }) {
                Text("10 years earlier", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { onChange((shown + 10).coerceAtMost(current - 1)) }) {
                Text("10 years later", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RoundStepper(symbol: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(CareDimens.TouchTarget)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = symbol, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun ConditionPicker(
    selected: Set<Condition>,
    onToggle: (Condition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm)) {
        Condition.entries.forEach { condition ->
            val isOn = condition in selected
            Surface(
                onClick = { onToggle(condition) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CareDimens.TouchTarget),
                shape = RoundedCornerShape(CareDimens.ButtonRadius),
                color = if (isOn) CareColors.Navy else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isOn) CareColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (isOn) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = CareDimens.SpaceMd, vertical = CareDimens.SpaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A tick as well as the fill, so the choice never rests on
                    // telling two colours apart.
                    Text(
                        text = if (isOn) "✓" else "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(condition.plainName, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
