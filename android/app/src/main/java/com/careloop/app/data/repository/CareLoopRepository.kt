package com.careloop.app.data.repository

import com.careloop.app.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the UI and wherever data actually lives.
 *
 * Everything above this line (ViewModels, Compose) knows only this interface.
 * `MockCareLoopRepository` and `FirebaseCareLoopRepository` are interchangeable,
 * and swapping them is one line in [com.careloop.app.di.AppContainer].
 *
 * Returns [Flow] rather than suspend functions because Firestore snapshot
 * listeners are naturally streams. Designing the interface around the real shape
 * from the start meant the Firebase implementation slotted in without touching a
 * single screen.
 *
 * Operations that reach the network are `suspend` and return [Result] rather than
 * throwing. A dropped connection during a medication call is an expected event,
 * not an exceptional one, and the UI needs to say something honest about it.
 */
interface CareLoopRepository {

    // ---- Reads -------------------------------------------------------------

    fun observeElder(): Flow<ElderProfile>
    fun observeMedications(): Flow<List<Medication>>
    fun observeCheckIns(): Flow<List<CheckIn>>
    fun observeEscalations(): Flow<List<Escalation>>
    fun observeVitals(type: VitalType): Flow<List<VitalReading>>
    fun observeSharingPreferences(): Flow<SharingPreferences>
    fun observeSharedItems(): Flow<List<SharedItem>>

    suspend fun getCheckIn(id: String): CheckIn?
    suspend fun getEscalation(id: String): Escalation?

    // ---- Interaction checking ---------------------------------------------

    /**
     * Checks a substance against the patient's current medications.
     *
     * Invoked mid-conversation by Cara's `check_interaction` tool, so it must not
     * block the call. Implementations return quickly or not at all.
     */
    suspend fun findDrugInteraction(drugA: String, drugB: String): DrugInteraction?
    suspend fun findFoodInteractions(drugName: String): List<FoodInteraction>

    /** The full check used during a live call. */
    suspend fun checkInteraction(
        substance: String,
        kind: String,
    ): Result<InteractionCheckResult>

    // ---- Writes ------------------------------------------------------------

    suspend fun updateCheckInTime(time: java.time.LocalTime)
    suspend fun updateSharingPreferences(preferences: SharingPreferences)

    /** The elder confirming or disputing something Cara shared. The dignity loop. */
    suspend fun respondToSharedItem(itemId: String, response: ElderResponse, note: String?)

    // ---- Calls -------------------------------------------------------------

    /** Caretaker-triggered, or the app asking for a call on demand. */
    suspend fun requestManualCheckIn(): Result<Unit>

    /**
     * Registers this device so the backend can ring it.
     *
     * Called on launch and whenever FCM rotates the token. A stale token fails
     * silently, so this is called more often than strictly necessary.
     */
    suspend fun registerDeviceToken(token: String): Result<Unit>

    /**
     * Reports how a call ended.
     *
     * FCM cannot tell the server whether anyone answered, so this is the only
     * source of that fact, and the retry and escalation logic depends on it.
     */
    suspend fun reportCallOutcome(
        callAttemptId: String,
        outcome: String,
        durationSeconds: Int,
    ): Result<Unit>

    /** Submits a completed call, which triggers the reasoning engine server-side. */
    suspend fun submitCheckIn(submission: CheckInSubmission): Result<CheckInResult>

    /** Mints a short-lived token for a direct Gemini Live connection. */
    suspend fun mintLiveSessionToken(): Result<LiveSessionToken>

    /**
     * Cara escalating in the moment rather than at the end of the call.
     *
     * Separate from [submitCheckIn] on purpose: the reasoning engine is right for
     * patterns and wrong for chest pain.
     */
    suspend fun reportUrgentConcern(whatTheyDescribed: String): Result<Unit>

    /** Cara noting something to raise on a future call. */
    suspend fun rememberForNextTime(
        topic: String,
        why: String,
        followUpInDays: Int,
    ): Result<Unit>

    /** Cara closing something she had been following up on. */
    suspend fun closeOpenThread(topic: String, whatHappened: String): Result<Unit>
}

// -----------------------------------------------------------------------------
// Transport types
// -----------------------------------------------------------------------------

data class InteractionCheckResult(
    val found: Boolean,
    val resolvedName: String?,
    val drugInteractions: List<DrugInteraction>,
    val foodInteractions: List<FoodInteraction>,
    /** One or two sentences Cara can say out loud. Empty when nothing was found. */
    val spokenSummary: String,
)

data class CheckInSubmission(
    val callAttemptId: String,
    val durationSeconds: Int,
    val medicationsConfirmed: List<String>,
    val medicationsMissed: List<String>,
    val transcript: List<TranscriptLine>,
    val confusionSignal: Float,
    val hesitationSignal: Float,
    val toneNote: String?,
    val vitals: List<RecordedVital>,
)

data class RecordedVital(
    val type: VitalType,
    val value: Float,
    val secondaryValue: Float? = null,
)

data class CheckInResult(
    val checkInId: String,
    /** What the reasoning engine decided: no_action, retry_soon, retry_later, escalate. */
    val action: String,
    val escalationId: String?,
)

data class LiveSessionToken(
    val token: String,
    val expiresAtEpochMillis: Long,
    val model: String,
    val wsHost: String,
    /**
     * Cara's persona, built server-side from this person's real medication list
     * and whatever she left open on earlier calls.
     *
     * Carried with the token rather than compiled into the app. It is
     * per-session data, and the prompt is the product's actual behaviour, so
     * baking it into an APK would mean every wording change waits on a release.
     */
    val systemInstruction: String,
    /**
     * Tool declarations as raw JSON, passed through to the setup frame verbatim.
     *
     * A string rather than a parsed type deliberately: this is the server's
     * contract with Gemini, and re-modelling it here is how the two drift apart.
     * The client's job is to forward it, not to have an opinion about it.
     */
    val toolsJson: String,
    val voiceName: String,
    val languageCode: String,
)
