package com.careloop.app.ui.screens.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.AgentThread
import com.careloop.app.data.model.ThreadStatus
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CareEmptyState
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What Cara is keeping an eye on.
 *
 * The agent's memory of a person, shown to that person.
 *
 * This screen exists because of a specific finding: nearly half of older adults
 * are uncomfortable with being monitored even when the safety benefit is
 * obvious, and what resolves that discomfort is not a better privacy policy but
 * genuine, visible control. An agent that quietly builds a picture of someone
 * across weeks and only shows it to their family is surveillance, however
 * kindly it is meant. Showing the same picture to both sides is the whole
 * position, and until now the elder's side of it did not exist in the app.
 *
 * These are not decorative. Cara writes them during calls and reads them back
 * when deciding what to ask about next, so the list genuinely steers her
 * behaviour rather than describing it afterwards.
 *
 * Two deliberate choices:
 *
 *  - [AgentThread.why] is shown verbatim, in Cara's own words. Rewriting it for
 *    the elder while the family sees the original would be a quiet asymmetry of
 *    exactly the kind this screen exists to prevent.
 *  - Resolved threads are kept rather than cleared. Seeing what Cara stopped
 *    worrying about, and why, is most of what makes the open ones credible.
 */
@Composable
fun AgentThreadsScreen(modifier: Modifier = Modifier) {
    val threads by AppContainer.repository.observeAgentThreads()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    AgentThreadsContent(threads = threads, modifier = modifier)
}

@Composable
private fun AgentThreadsContent(
    threads: List<AgentThread>,
    modifier: Modifier = Modifier,
) {
    val open = remember(threads) { threads.filter { it.status == ThreadStatus.OPEN } }
    val settled = remember(threads) { threads.filter { it.status == ThreadStatus.RESOLVED } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            horizontal = CareDimens.ScreenPadding,
            vertical = CareDimens.SpaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
    ) {
        item(key = "header") {
            Column {
                Text(
                    "What Cara is keeping an eye on",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(CareDimens.SpaceSm))
                Text(
                    "Cara remembers a few things between calls so she does not ask you " +
                        "the same question every morning. This is all of it, in her words. " +
                        "Your family sees exactly this list and nothing more.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (threads.isEmpty()) {
            item(key = "empty") {
                CareEmptyState(
                    title = "Nothing yet",
                    whatHappensNext = "When something comes up on a call that Cara thinks " +
                        "is worth returning to, it will appear here before she asks you " +
                        "about it again.",
                )
            }
        }

        if (open.isNotEmpty()) {
            item(key = "open-heading") { SectionHeading("Still watching") }
            items(open, key = { it.id }) { thread -> ThreadCard(thread) }
        }

        if (settled.isNotEmpty()) {
            item(key = "settled-heading") { SectionHeading("Settled") }
            items(settled, key = { it.id }) { thread -> ThreadCard(thread) }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ThreadCard(thread: AgentThread) {
    val settled = thread.status == ThreadStatus.RESOLVED

    CareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon and word together, never colour alone. Blue-yellow
            // discrimination is the first to go with age, so nothing in this
            // product carries meaning in hue by itself.
            Icon(
                imageVector = if (settled) Icons.Filled.CheckCircle else Icons.Filled.Visibility,
                contentDescription = null,
                tint = if (settled) CareColors.Good else CareColors.GoldDeep,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Text(
                text = thread.topic,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceMd))

        // Cara's own reason, verbatim. See the screen comment.
        Text(
            text = thread.why,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (settled && thread.resolution != null) {
            Spacer(Modifier.height(CareDimens.SpaceMd))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CareDimens.CardRadius),
                colors = CardDefaults.cardColors(containerColor = CareColors.GoodSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = thread.resolution,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = CareColors.Good,
                    modifier = Modifier.padding(CareDimens.SpaceMd),
                )
            }
        }

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = footnote(thread),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The quiet line at the bottom of each card.
 *
 * Says how often this has come up and, for open threads, roughly when Cara
 * intends to raise it again. That second part matters more than it looks:
 * knowing she will ask on Thursday rather than every single morning is the
 * difference between a companion and a nag, and saying so out loud is what
 * makes it feel like a plan rather than a watchlist.
 */
private fun footnote(thread: AgentThread): String {
    val times = when (thread.timesRaised) {
        0, 1 -> "Came up once"
        2 -> "Came up twice"
        else -> "Came up ${thread.timesRaised} times"
    }

    if (thread.status == ThreadStatus.RESOLVED) {
        val on = thread.resolvedAt?.let { ", settled ${friendly(it)}" } ?: ""
        return "$times$on"
    }

    val next = thread.followUpAfter ?: return times
    val now = LocalDateTime.now()
    return when {
        next.isBefore(now) -> "$times. Cara may ask about this on your next call."
        next.toLocalDate() == now.toLocalDate() ->
            "$times. Cara may ask about this today."
        else -> "$times. Cara will leave it until ${friendly(next)}."
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE")
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM")

private fun friendly(at: LocalDateTime): String {
    val today = LocalDateTime.now().toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), today)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days in 2..6 -> "on ${at.format(dayFormat)}"
        days == -1L -> "tomorrow"
        days in -6..-2 -> "on ${at.format(dayFormat)}"
        else -> "on ${at.format(dateFormat)}"
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AgentThreadsPreview() {
    CareLoopTheme {
        AgentThreadsContent(threads = MockData.agentThreads)
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AgentThreadsEmptyPreview() {
    CareLoopTheme {
        AgentThreadsContent(threads = emptyList())
    }
}

/** The whole screen has to survive the largest text size Settings offers. */
@PreviewFontScale
@Composable
private fun AgentThreadsFontScalePreview() {
    CareLoopTheme {
        AgentThreadsContent(threads = MockData.agentThreads.take(1))
    }
}
