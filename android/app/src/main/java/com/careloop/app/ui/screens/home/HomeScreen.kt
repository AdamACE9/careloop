package com.careloop.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.BuildConfig
import com.careloop.app.call.startIncomingCallDemo
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.CheckInStatus
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.*
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import java.time.format.DateTimeFormatter

/**
 * Margaret's home screen.
 *
 * The whole screen answers one question a 78-year-old actually has: *am I alright today?*
 * Everything else is secondary. So today's status is the largest element, the answer is
 * given in words rather than a status colour, and there is exactly one obvious next action.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val elder by AppContainer.repository.observeElder()
        .collectAsStateWithLifecycle(initialValue = MockData.elder)
    val checkIns by AppContainer.repository.observeCheckIns()
        .collectAsStateWithLifecycle(initialValue = MockData.checkIns)
    val medications by AppContainer.repository.observeMedications()
        .collectAsStateWithLifecycle(initialValue = MockData.medications)

    val today = checkIns.firstOrNull { it.date == java.time.LocalDate.now() }
    val timeFormat = androidx.compose.runtime.remember {
        DateTimeFormatter.ofPattern("h:mm a")
    }
    val refillNeeded = medications.filter { it.needsRefillSoon }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(CareDimens.ScreenPadding),
    ) {
        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = greeting(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = elder.preferredName,
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        // --- Today ---
        CareCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoopMark(size = CareDimens.LoopSmall, animated = false)
                Spacer(Modifier.width(CareDimens.SpaceMd))
                Column {
                    Text("Today's check-in", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (today != null) {
                            "Done at ${today.startedAt.format(timeFormat)}"
                        } else {
                            "Cara will call at ${elder.dailyCheckInTime.format(timeFormat)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (today != null) {
                Spacer(Modifier.height(CareDimens.SpaceMd))
                StatusPill(
                    text = if (today.status == CheckInStatus.COMPLETED) {
                        "All medications taken"
                    } else {
                        today.status.label
                    },
                    icon = Icons.Rounded.CheckCircle,
                    contentColor = CareColors.Good,
                    containerColor = CareColors.GoodSurface,
                )
                if (today.caraSummary.isNotBlank()) {
                    Spacer(Modifier.height(CareDimens.SpaceMd))
                    Text(
                        text = today.caraSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceMd))

        // --- Next call ---
        CareCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = CareColors.Navy,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceMd))
                Column {
                    Text("Next check-in", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Tomorrow at ${elder.dailyCheckInTime.format(timeFormat)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // --- Proactive refill ---
        if (refillNeeded.isNotEmpty()) {
            Spacer(Modifier.height(CareDimens.SpaceMd))
            val med = refillNeeded.first()
            CareCard {
                Text("Running low", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    text = "Your ${med.name.lowercase()} runs out in about " +
                        "${med.daysOfSupplyRemaining} days. Repeat prescriptions usually " +
                        "take a few days, so it's worth ordering now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXl))

        // --- Trigger a real call ---
        // Debug builds only. "Simulate" undersold this button: it posts the exact same
        // CallStyle notification that a real FCM push will post, so the call that follows
        // is the genuine code path, not a mock-up standing in for one. The copy below says
        // that plainly rather than hedging with "demo" language that isn't true of what
        // actually happens on tap.
        if (BuildConfig.DEBUG) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        CareColors.Cloud,
                        RoundedCornerShape(CareDimens.CardRadius),
                    )
                    .padding(CareDimens.SpaceLg),
            ) {
                Text(
                    "Try it now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(CareDimens.SpaceSm))
                Text(
                    "This rings your phone exactly the way a scheduled check-in would. " +
                        "It's a real call from Cara, not a preview of one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(CareDimens.SpaceMd))
                CarePrimaryButton(
                    text = "Have Cara call me now",
                    icon = Icons.Rounded.PhoneInTalk,
                    onClick = { context.startIncomingCallDemo() },
                )
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceXxl))
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun HomePreview() {
    CareLoopTheme { HomeScreen() }
}

/** Confirms the greeting, status card, and refill card survive 200% scale. */
@PreviewFontScale
@Composable
private fun HomeFontScalePreview() {
    CareLoopTheme { HomeScreen() }
}
