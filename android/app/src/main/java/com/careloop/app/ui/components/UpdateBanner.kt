package com.careloop.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.careloop.app.data.UpdateChecker
import com.careloop.app.ui.theme.CareDimens

/**
 * "A newer CareLoop is ready", with one button.
 *
 * Checked once per time the screen is shown, not polled. The button opens the
 * download in the browser, where Android's own installer takes over: see
 * [UpdateChecker] for why that final confirmation cannot be skipped.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        available = UpdateChecker.newerVersionAvailable()
    }

    if (!available) return

    CareCard(modifier = modifier) {
        Text("A newer CareLoop is ready", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            "It installs over this one, so your calls, your medications and your family link all stay as they are.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceMd))
        CarePrimaryButton(
            text = "Get the update",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.DOWNLOAD_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}
