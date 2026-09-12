package com.careloop.app.data.repository

import android.util.Log
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray

/**
 * Firestore-backed implementation.
 *
 * Swapped in for [MockCareLoopRepository] by a single line in `AppContainer`.
 * No screen knows the difference, which was the point of putting the seam here.
 *
 * ## Two decisions worth knowing about
 *
 * **Reads degrade to demo data rather than to an error.** If a collection is
 * empty or a listener fails on permissions, these flows emit the bundled demo
 * dataset instead of an empty list. That is deliberate for a product being
 * demonstrated: a freshly created account showing a blank medication list looks
 * broken, whereas showing the example household immediately communicates what the
 * app is for. Real data replaces it the moment any exists.
 *
 * **The patient id is always the signed-in uid.** The elder's device only ever
 * reads and writes its own record. That is enforced server-side by
 * `firestore.rules`, and mirrored here so the client cannot even construct a path
 * to somebody else's data by accident.
 */
class FirebaseCareLoopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) : CareLoopRepository {

    private val uid: String? get() = auth.currentUser?.uid

    private fun patientPath(): String? = uid?.let { "patients/$it" }

    // =========================================================================
    // Reads
    // =========================================================================

    override fun observeElder(): Flow<ElderProfile> = documentFlow(
        path = patientPath(),
        fallback = MockData.elder,
    ) { snapshot -> snapshot.toElderProfile() }

    override fun observeMedications(): Flow<List<Medication>> = collectionFlow(
        path = patientPath()?.let { "$it/medications" },
        fallback = MockData.medications,
        orderBy = null,
    ) { it.toMedication() }

    override fun observeCheckIns(): Flow<List<CheckIn>> = collectionFlow(
        path = patientPath()?.let { "$it/checkIns" },
        fallback = MockData.checkIns,
        orderBy = "startedAt" to Query.Direction.DESCENDING,
    ) { it.toCheckIn() }

    override fun observeEscalations(): Flow<List<Escalation>> = collectionFlow(
        path = patientPath()?.let { "$it/escalations" },
        fallback = MockData.escalations,
        orderBy = "raisedAt" to Query.Direction.DESCENDING,
    ) { it.toEscalation() }

    override fun observeVitals(type: VitalType): Flow<List<VitalReading>> = collectionFlow(
        path = patientPath()?.let { "$it/vitals" },
        fallback = MockData.bloodSugarReadings,
        orderBy = "recordedAt" to Query.Direction.ASCENDING,
    ) { it.toVitalReading() }
        // Filtered client-side rather than with a where() clause, to avoid needing
        // a composite index for what is a very small per-patient collection.
        .map { readings -> readings.filter { reading -> reading.type == type } }

    override fun observeSharingPreferences(): Flow<SharingPreferences> = documentFlow(
        path = patientPath(),
        fallback = MockData.sharingPreferences,
    ) { it.toSharingPreferences() }

    override fun observeSharedItems(): Flow<List<SharedItem>> = collectionFlow(
        path = patientPath()?.let { "$it/sharedItems" },
        fallback = MockData.sharedItems,
        orderBy = "sharedAt" to Query.Direction.DESCENDING,
    ) { it.toSharedItem() }

    override suspend fun getCheckIn(id: String): CheckIn? {
        val path = patientPath() ?: return null
        return runCatching {
            db.document("$path/checkIns/$id").get().await().toCheckIn()
        }.getOrNull()
    }

    override suspend fun getEscalation(id: String): Escalation? {
        val path = patientPath() ?: return null
        return runCatching {
            db.document("$path/escalations/$id").get().await().toEscalation()
        }.getOrNull()
    }

    // =========================================================================
    // Interaction checking
    // =========================================================================

    override suspend fun findDrugInteraction(drugA: String, drugB: String): DrugInteraction? {
        return checkInteraction(drugA, "drug").getOrNull()?.drugInteractions?.firstOrNull()
    }

    override suspend fun findFoodInteractions(drugName: String): List<FoodInteraction> {
        return checkInteraction(drugName, "food").getOrNull()?.foodInteractions.orEmpty()
    }

    /**
     * The live mid-call check.
     *
     * Called while Cara is talking, so it is deliberately allowed to fail. If the
     * network is slow the conversation carries on without the result rather than
     * stalling, which is why this returns a Result the caller can ignore.
     */
    override suspend fun checkInteraction(
        substance: String,
        kind: String,
    ): Result<InteractionCheckResult> = callFunction("checkInteraction") {
        mapOf(
            "patientId" to requireUid(),
            "substance" to substance,
            "kind" to kind,
        )
    }.mapCatching { data ->
        InteractionCheckResult(
            found = data["found"] as? Boolean ?: false,
            resolvedName = data["resolvedName"] as? String,
            drugInteractions = (data["drugInteractions"] as? List<*>).orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toDrugInteraction() },
            foodInteractions = (data["foodInteractions"] as? List<*>).orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toFoodInteraction() },
            spokenSummary = data["spokenSummary"] as? String ?: "",
        )
    }

    // =========================================================================
    // Writes
    // =========================================================================

    override suspend fun updateCheckInTime(time: LocalTime) {
        val path = patientPath() ?: return
        runCatching {
            db.document(path).update(
                mapOf(
                    "dailyCheckInTime" to time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    "updatedAt" to LocalDateTime.now().toString(),
                ),
            ).await()
        }.onFailure { Log.w(TAG, "Could not update check-in time") }
    }

    override suspend fun updateSharingPreferences(preferences: SharingPreferences) {
        val path = patientPath() ?: return
        runCatching {
            db.document(path).update(
                "sharingPreferences",
                mapOf(
                    "enabledCategories" to preferences.enabledCategories.map { it.name.lowercase() },
                    "alwaysShareUrgent" to preferences.alwaysShareUrgent,
                    "privacyHoldUntil" to preferences.privacyHoldUntil?.toString(),
                ),
            ).await()
        }.onFailure { Log.w(TAG, "Could not update sharing preferences") }
    }

    override suspend fun respondToSharedItem(
        itemId: String,
        response: ElderResponse,
        note: String?,
    ) {
        val path = patientPath() ?: return
        runCatching {
            db.document("$path/sharedItems/$itemId").update(
                mapOf(
                    "elderResponse" to response.name.lowercase(),
                    "elderNote" to note,
                    "elderRespondedAt" to LocalDateTime.now().toString(),
                ),
            ).await()
        }.onFailure { Log.w(TAG, "Could not record response to shared item") }
    }

    // =========================================================================
    // Calls
    // =========================================================================

    override suspend fun requestManualCheckIn(): Result<Unit> =
        callFunction("triggerCall") {
            mapOf(
                "patientId" to requireUid(),
                "trigger" to "manual",
            )
        }.map { }

    override suspend fun registerDeviceToken(token: String): Result<Unit> =
        callFunction("registerDevice") {
            mapOf("token" to token, "platform" to "android")
        }.map { }

    override suspend fun reportCallOutcome(
        callAttemptId: String,
        outcome: String,
        durationSeconds: Int,
    ): Result<Unit> = callFunction("reportCallOutcome") {
        mapOf(
            "patientId" to requireUid(),
            "callAttemptId" to callAttemptId,
            "outcome" to outcome,
            "durationSeconds" to durationSeconds,
        )
    }.map { }

    override suspend fun submitCheckIn(
        submission: CheckInSubmission,
    ): Result<CheckInResult> = callFunction("submitCheckIn") {
        mapOf(
            "patientId" to requireUid(),
            "callAttemptId" to submission.callAttemptId,
            "durationSeconds" to submission.durationSeconds,
            "medicationsConfirmed" to submission.medicationsConfirmed,
            "medicationsMissed" to submission.medicationsMissed,
            "transcript" to submission.transcript.map { line ->
                mapOf(
                    "speaker" to line.speaker.name.lowercase(),
                    "text" to line.text,
                    "offsetSeconds" to line.offsetSeconds,
                    "flag" to line.flag?.name?.lowercase(),
                )
            },
            "toneSignals" to mapOf(
                "confusion" to submission.confusionSignal,
                "hesitation" to submission.hesitationSignal,
                "note" to submission.toneNote,
            ),
            "vitals" to submission.vitals.map { vital ->
                mapOf(
                    "type" to vital.type.name.lowercase(),
                    "value" to vital.value,
                    "secondaryValue" to vital.secondaryValue,
                )
            },
        )
    }.mapCatching { data ->
        CheckInResult(
            checkInId = data["checkInId"] as? String ?: "",
            action = data["action"] as? String ?: "no_action",
            escalationId = data["escalationId"] as? String,
        )
    }

    override suspend fun mintLiveSessionToken(): Result<LiveSessionToken> =
        callFunction("mintLiveSessionToken") {
            mapOf("patientId" to requireUid())
        }.mapCatching { data ->
            LiveSessionToken(
                token = data["token"] as? String ?: error("missing token"),
                expiresAtEpochMillis = (data["expiresAt"] as? String)
                    ?.let { runCatching { LocalDateTime.parse(it.removeSuffix("Z")) }.getOrNull() }
                    ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    ?: (System.currentTimeMillis() + 10 * 60_000),
                model = data["model"] as? String ?: "",
                wsHost = data["wsHost"] as? String ?: "generativelanguage.googleapis.com",
                wsPath = data["wsPath"] as? String
                    ?: "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained",
                systemInstruction = data["systemInstruction"] as? String ?: "",
                // Re-serialised rather than re-modelled. The callable hands back
                // decoded maps and lists; Gemini wants the original JSON shape,
                // and JSONArray(List) reproduces it faithfully without this file
                // needing to know a single field name inside it.
                toolsJson = (data["tools"] as? List<*>)
                    ?.let { runCatching { JSONArray(it).toString() }.getOrNull() }
                    ?: "[]",
                voiceName = (data["voice"] as? Map<*, *>)?.get("voiceName") as? String ?: "Aoede",
                languageCode = (data["voice"] as? Map<*, *>)?.get("languageCode") as? String
                    ?: "en-GB",
            )
        }

    override suspend fun reportUrgentConcern(whatTheyDescribed: String): Result<Unit> =
        callFunction("reportUrgentConcern") {
            mapOf(
                "patientId" to requireUid(),
                "whatTheyDescribed" to whatTheyDescribed,
            )
        }.map { }

    override suspend fun rememberForNextTime(
        topic: String,
        why: String,
        followUpInDays: Int,
    ): Result<Unit> = callFunction("rememberForNextTime") {
        mapOf(
            "patientId" to requireUid(),
            "topic" to topic,
            "why" to why,
            "followUpInDays" to followUpInDays,
        )
    }.map { }

    override suspend fun closeOpenThread(topic: String, whatHappened: String): Result<Unit> =
        callFunction("closeOpenThread") {
            mapOf(
                "patientId" to requireUid(),
                "topic" to topic,
                "whatHappened" to whatHappened,
            )
        }.map { }

    override suspend fun ensureSignedInPatient(
        preferredName: String,
        dailyCheckInTime: String,
    ): Result<Unit> = runCatching {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        ?: error("anonymous sign-in returned no user")

        // Concatenated rather than interpolated. This line previously carried
        // an escaped dollar and wrote to a document literally named
        // "patients/${user.uid}", which Firestore rejected with a permission
        // error because that path is not the caller's own record.
        val ref = db.document("patients/" + user.uid)
        val existing = ref.get().await()
        val now = java.time.Instant.now().toString()

        if (existing.exists()) {
            // Only the fields onboarding owns. Rules reject anything else, and
            // caretakerIds in particular must never be writable from a client.
            ref.update(
                mapOf(
                    "profile.preferredName" to preferredName,
                    "dailyCheckInTime" to dailyCheckInTime,
                    "updatedAt" to now,
                ),
            ).await()
        } else {
            ref.set(
                mapOf(
                    "profile" to mapOf(
                        "firstName" to preferredName,
                        "lastName" to "",
                        "preferredName" to preferredName,
                        "age" to 0,
                        "conditions" to emptyList<String>(),
                    ),
                    // Empty, and the rules enforce that it is empty on create.
                    // A client that could seed this could grant anyone read
                    // access to a stranger's health record.
                    "caretakerIds" to emptyList<String>(),
                    "dailyCheckInTime" to dailyCheckInTime,
                    "timezone" to java.util.TimeZone.getDefault().id,
                    "sharingPreferences" to mapOf(
                        "enabledCategories" to listOf(
                            "missed_doses", "confusion", "vitals", "refills",
                        ),
                        "alwaysShareUrgent" to true,
                        "privacyHoldUntil" to null,
                    ),
                    "createdAt" to now,
                    "updatedAt" to now,
                ),
            ).await()
        }
        // Register the push token here, not only in onNewToken.
        //
        // FCM issues its token while the app is starting, which is before
        // anonymous sign-in has finished, so onNewToken's registration failed
        // with UNAUTHENTICATED and was never retried. The device then had no
        // token on record and a scheduled call would have gone nowhere: FCM
        // accepts a send to a missing token and the phone simply never rings,
        // which from the outside looks like the person ignored their check-in.
        //
        // Deliberately does not fail the whole provisioning step. A person
        // without push can still open the app and be called by tapping; a person
        // without an account cannot do anything at all.
        runCatching {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            registerDeviceToken(fcmToken)
        }.onFailure { Log.w(TAG, "Push token not registered yet") }

        Unit
    }.onFailure { Log.w(TAG, "Could not establish patient session") }

    override suspend fun generateLinkingCode(): Result<LinkingCode> =
        callFunction("generateLinkingCode") {
            mapOf("patientId" to requireUid())
        }.mapCatching { data ->
            LinkingCode(
                code = data["code"] as? String ?: error("missing code"),
                expiresAtIso = data["expiresAt"] as? String ?: "",
            )
        }

    // =========================================================================
    // Plumbing
    // =========================================================================

    class NotSignedIn : IllegalStateException("Not signed in")

    /**
     * Invokes a callable function and unwraps its result map.
     *
     * Errors are returned rather than thrown. Every caller is on a path where
     * failure is a normal outcome the UI must describe honestly, not an
     * exceptional one that should crash a call in progress.
     *
     * The payload is built lazily inside `runCatching` so that a missing uid
     * throws [NotSignedIn] and lands in the same Result as a network failure,
     * rather than needing a separate branch at every call site.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun callFunction(
        name: String,
        buildPayload: () -> Map<String, Any?>,
    ): Result<Map<String, Any?>> = runCatching {
        val body = buildPayload()
        val result = functions.getHttpsCallable(name).call(body).await()
        // getData() rather than `.data`: HttpsCallableResult has a private
        // field named `data` alongside the public getter, and Kotlin's
        // property syntax resolves to the inaccessible field.
        (result.getData() as? Map<String, Any?>) ?: emptyMap()
    }.onFailure { error ->
        // The payload and the exception MESSAGE are still never logged: both can
        // carry medication names and transcript text.
        //
        // The exception TYPE and the callable's error code are logged, and they
        // are not the same thing. A code like UNAUTHENTICATED or NOT_FOUND says
        // what went wrong without saying anything about whose data it was.
        // Without it, "Callable mintLiveSessionToken failed" is the entire
        // diagnosis available for a call that the server log says succeeded,
        // which is how an afternoon disappears.
        val code = (error as? FirebaseFunctionsException)?.code?.name ?: "NONE"
        Log.w(TAG, "Callable $name failed (${error.javaClass.simpleName}, code=$code)")
    }

    /** The signed-in uid, or throws so the failure lands in the caller's Result. */
    private fun requireUid(): String = uid ?: throw NotSignedIn()

    /** A single document as a flow, falling back to demo data on empty or error. */
    private fun <T> documentFlow(
        path: String?,
        fallback: T,
        map: (DocumentSnapshot) -> T?,
    ): Flow<T> {
        if (path == null) return flowOf(fallback)
        return callbackFlow {
            var registration: ListenerRegistration? = null
            registration = db.document(path).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(fallback)
                    return@addSnapshotListener
                }
                trySend(runCatching { map(snapshot) }.getOrNull() ?: fallback)
            }
            awaitClose { registration?.remove() }
        }
    }

    /** A collection as a flow, falling back to demo data on empty or error. */
    private fun <T> collectionFlow(
        path: String?,
        fallback: List<T>,
        orderBy: Pair<String, Query.Direction>?,
        map: (DocumentSnapshot) -> T?,
    ): Flow<List<T>> {
        if (path == null) return flowOf(fallback)
        return callbackFlow {
            var query: Query = db.collection(path)
            if (orderBy != null) query = query.orderBy(orderBy.first, orderBy.second)

            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(fallback)
                    return@addSnapshotListener
                }
                val items = snapshot.documents.mapNotNull { doc ->
                    runCatching { map(doc) }.getOrNull()
                }
                trySend(items.ifEmpty { fallback })
            }
            awaitClose { registration.remove() }
        }
    }

    private companion object {
        const val TAG = "CareLoopRepo"
    }
}

// =============================================================================
// Document mapping
//
// Written by hand rather than with Firestore's automatic POJO mapping, because
// the domain models use java.time and Kotlin enums, neither of which the
// automatic mapper handles. Explicit mapping also means a malformed document
// degrades to a sensible default instead of throwing inside a snapshot listener,
// where the exception would be swallowed and the screen would simply stay empty.
// =============================================================================

private fun DocumentSnapshot.toElderProfile(): ElderProfile? {
    val profile = get("profile") as? Map<*, *> ?: return null
    return ElderProfile(
        id = id,
        firstName = profile["firstName"] as? String ?: "",
        lastName = profile["lastName"] as? String ?: "",
        preferredName = profile["preferredName"] as? String
            ?: profile["firstName"] as? String ?: "",
        age = (profile["age"] as? Number)?.toInt() ?: 0,
        conditions = (profile["conditions"] as? List<*>).orEmpty().mapNotNull { value ->
            Condition.entries.firstOrNull { it.name.equals(value as? String, ignoreCase = true) }
        },
        dailyCheckInTime = parseTime(getString("dailyCheckInTime")) ?: LocalTime.of(9, 0),
        caretaker = MockData.caretaker,
    )
}

private fun DocumentSnapshot.toMedication(): Medication? {
    val name = getString("name") ?: return null
    return Medication(
        id = id,
        name = name,
        dose = getString("dose") ?: "",
        purpose = getString("purpose") ?: "",
        schedule = (get("schedule") as? List<*>).orEmpty()
            .mapNotNull { parseTime(it as? String) },
        criticality = Criticality.entries.firstOrNull {
            it.name.equals(getString("criticality"), ignoreCase = true)
        } ?: Criticality.MEDIUM,
        dosesRemaining = (get("dosesRemaining") as? Number)?.toInt() ?: 0,
        dosesPerDay = (get("dosesPerDay") as? Number)?.toInt() ?: 1,
        refillLeadTimeDays = (get("refillLeadTimeDays") as? Number)?.toInt() ?: 7,
        foodGuidance = getString("foodGuidance"),
    )
}

private fun DocumentSnapshot.toCheckIn(): CheckIn? {
    val startedAt = parseDateTime(getString("startedAt")) ?: return null
    return CheckIn(
        id = id,
        startedAt = startedAt,
        durationSeconds = (get("durationSeconds") as? Number)?.toInt() ?: 0,
        status = CheckInStatus.entries.firstOrNull {
            it.name.equals(getString("status"), ignoreCase = true)
        } ?: CheckInStatus.COMPLETED,
        medicationsConfirmed = (get("medicationsConfirmed") as? List<*>).orEmpty()
            .filterIsInstance<String>(),
        medicationsMissed = (get("medicationsMissed") as? List<*>).orEmpty()
            .filterIsInstance<String>(),
        vitals = emptyList(),
        transcript = (get("transcript") as? List<*>).orEmpty().mapNotNull { entry ->
            val line = entry as? Map<*, *> ?: return@mapNotNull null
            TranscriptLine(
                speaker = if (line["speaker"] == "cara") Speaker.CARA else Speaker.ELDER,
                text = line["text"] as? String ?: "",
                offsetSeconds = (line["offsetSeconds"] as? Number)?.toInt() ?: 0,
                flag = TranscriptFlag.entries.firstOrNull {
                    it.name.equals(line["flag"] as? String, ignoreCase = true)
                },
            )
        },
        caraSummary = getString("caraSummary") ?: "",
    )
}

private fun DocumentSnapshot.toEscalation(): Escalation? {
    val raisedAt = parseDateTime(getString("raisedAt")) ?: return null
    return Escalation(
        id = id,
        raisedAt = raisedAt,
        severity = EscalationSeverity.entries.firstOrNull {
            it.name.equals(getString("severity"), ignoreCase = true)
        } ?: EscalationSeverity.FYI,
        headline = getString("headline") ?: "",
        explanation = getString("explanation") ?: "",
        reasoning = (get("reasoning") as? List<*>).orEmpty().mapNotNull { entry ->
            val step = entry as? Map<*, *> ?: return@mapNotNull null
            ReasoningStep(
                observation = step["observation"] as? String ?: "",
                evidence = step["evidence"] as? String ?: "",
                checkInId = step["checkInId"] as? String,
            )
        },
        confidence = Confidence.entries.firstOrNull {
            it.name.equals(getString("confidence"), ignoreCase = true)
        } ?: Confidence.MEDIUM,
        alternativesConsidered = (get("alternativesConsidered") as? List<*>).orEmpty()
            .filterIsInstance<String>(),
        relatedMedication = getString("relatedMedication"),
        elderResponse = ElderResponse.entries.firstOrNull {
            it.name.equals(getString("elderResponse"), ignoreCase = true)
        } ?: ElderResponse.NOT_YET_SEEN,
        elderNote = getString("elderNote"),
        acknowledgedByCaretaker = getBoolean("acknowledged") ?: false,
    )
}

private fun DocumentSnapshot.toVitalReading(): VitalReading? {
    val recordedAt = parseDateTime(getString("recordedAt")) ?: return null
    val type = VitalType.entries.firstOrNull {
        it.name.equals(getString("type"), ignoreCase = true)
    } ?: return null
    return VitalReading(
        id = id,
        type = type,
        value = (get("value") as? Number)?.toFloat() ?: return null,
        secondaryValue = (get("secondaryValue") as? Number)?.toFloat(),
        recordedAt = recordedAt,
    )
}

private fun DocumentSnapshot.toSharingPreferences(): SharingPreferences? {
    val prefs = get("sharingPreferences") as? Map<*, *> ?: return null
    return SharingPreferences(
        enabledCategories = (prefs["enabledCategories"] as? List<*>).orEmpty()
            .mapNotNull { value ->
                ShareCategory.entries.firstOrNull {
                    it.name.equals(value as? String, ignoreCase = true)
                }
            }.toSet(),
        alwaysShareUrgent = prefs["alwaysShareUrgent"] as? Boolean ?: true,
        privacyHoldUntil = parseDateTime(prefs["privacyHoldUntil"] as? String),
    )
}

private fun DocumentSnapshot.toSharedItem(): SharedItem? {
    val sharedAt = parseDateTime(getString("sharedAt")) ?: return null
    return SharedItem(
        id = id,
        sharedAt = sharedAt,
        category = ShareCategory.entries.firstOrNull {
            it.name.equals(getString("category"), ignoreCase = true)
        } ?: ShareCategory.MISSED_DOSES,
        whatCaraSaid = getString("whatCaraSaid") ?: "",
        elderResponse = ElderResponse.entries.firstOrNull {
            it.name.equals(getString("elderResponse"), ignoreCase = true)
        } ?: ElderResponse.NOT_YET_SEEN,
        elderNote = getString("elderNote"),
        escalationId = getString("escalationId"),
    )
}

private fun Map<*, *>.toDrugInteraction(): DrugInteraction? {
    return DrugInteraction(
        id = this["id"] as? String ?: return null,
        drugA = this["drugA"] as? String ?: "",
        drugB = this["drugB"] as? String ?: "",
        severity = InteractionSeverity.entries.firstOrNull {
            it.name.equals(this["severity"] as? String, ignoreCase = true)
        } ?: InteractionSeverity.MODERATE,
        whatItMeans = this["whatItMeans"] as? String ?: "",
        mechanism = this["mechanism"] as? String ?: "",
        advice = this["advice"] as? String ?: "",
        source = this["source"] as? String ?: "",
    )
}

private fun Map<*, *>.toFoodInteraction(): FoodInteraction? {
    return FoodInteraction(
        id = this["id"] as? String ?: return null,
        drugName = (this["drugNames"] as? List<*>)?.firstOrNull() as? String ?: "",
        food = this["food"] as? String ?: "",
        severity = InteractionSeverity.entries.firstOrNull {
            it.name.equals(this["severity"] as? String, ignoreCase = true)
        } ?: InteractionSeverity.MODERATE,
        whatItMeans = this["whatItMeans"] as? String ?: "",
        mechanism = this["mechanism"] as? String ?: "",
        advice = this["advice"] as? String ?: "",
    )
}

private fun parseTime(value: String?): LocalTime? =
    value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

private fun parseDateTime(value: String?): LocalDateTime? =
    value?.let {
        runCatching { LocalDateTime.parse(it.removeSuffix("Z")) }.getOrNull()
    }
