package com.careloop.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens

/**
 * Shared building blocks for elder-facing screens.
 *
 * Two rules are enforced here rather than left to each screen, because they are the two
 * most likely to be quietly broken under time pressure:
 *
 * 1. **Every tappable thing is at least [CareDimens.TouchTarget] tall.** Not the 48dp
 *    research floor — comfortably above it.
 * 2. **Status is never communicated by colour alone.** [StatusPill] requires an icon
 *    alongside its colour, because a user whose blue-yellow discrimination has degraded
 *    must still be able to read every state. If you find yourself wanting a colour-only
 *    badge, add the icon instead.
 */

/** Primary action. Large, unambiguous, one per screen wherever possible. */
@Composable
fun CarePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.LargeTouchTarget),
        shape = RoundedCornerShape(CareDimens.ButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CareColors.Navy,
            contentColor = CareColors.White,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(CareDimens.SpaceSm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary action. Same size as primary — smaller targets are how mistakes happen. */
@Composable
fun CareSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.TouchTarget),
        shape = RoundedCornerShape(CareDimens.ButtonRadius),
        border = BorderStroke(2.dp, CareColors.Navy),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CareColors.Navy),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(CareDimens.SpaceSm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CareCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Column(Modifier.padding(CareDimens.SpaceLg), content = content)
    }
}

/**
 * Status indicator.
 *
 * [icon] is intentionally **not** optional. Colour alone is not an accessible signal for
 * this audience, and making the icon required means it cannot be forgotten.
 */
@Composable
fun StatusPill(
    text: String,
    icon: ImageVector,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(CareDimens.PillRadius))
            .padding(horizontal = CareDimens.SpaceMd, vertical = CareDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.padding(bottom = CareDimens.SpaceMd)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Empty state. Elder-facing empty states must say what will happen next, not just that
 * nothing is here — "nothing yet" is unsettling when you are unsure whether you have
 * broken something.
 */
@Composable
fun CareEmptyState(
    title: String,
    whatHappensNext: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CareDimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoopMark(size = CareDimens.LoopSmall, animated = false)
        Spacer(Modifier.height(CareDimens.SpaceLg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            whatHappensNext,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
