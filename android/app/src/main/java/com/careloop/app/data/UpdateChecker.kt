package com.careloop.app.data

import com.careloop.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells the app when a newer build has been published.
 *
 * ## Why not a real auto-update
 *
 * An app installed outside the Play Store cannot update itself silently. That
 * is Android protecting people, not a gap in this code: every install from an
 * unknown source needs the person to confirm it. What IS possible is noticing a
 * newer build exists and making installing it a single tap, which is what this
 * does. The CareLoop keystore is committed, so the new APK installs over the old
 * one and keeps the account, the linking and every record.
 *
 * ## How it knows
 *
 * CI sets each build's versionCode to the workflow run number and writes the
 * same number into the rolling release's notes as `versionCode=NNN`. This reads
 * those notes from GitHub's public API, which needs no key and no login.
 *
 * Fails quiet. No network, a rate-limited API or an unexpected body all mean
 * "no update known", because a care app that shows an error about its own
 * update check has the priorities backwards.
 */
object UpdateChecker {

    private const val RELEASE_API =
        "https://api.github.com/repos/AdamACE9/careloop/releases/latest"

    /** Served through the website, so it stays valid if the release host moves. */
    const val DOWNLOAD_URL = "https://careloop--careloop-adam.europe-west4.hosted.app/careloop.apk"

    suspend fun newerVersionAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                if (connection.responseCode != 200) return@runCatching false
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val notes = JSONObject(body).optString("body")
                val published = Regex("versionCode=(\\d+)").find(notes)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@runCatching false
                published > BuildConfig.VERSION_CODE
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}
