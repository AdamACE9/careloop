package com.careloop.app.data.repository

import com.careloop.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

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

    /**
     * What Cara is currently keeping an eye on.
     *
     * The agent writes these during calls and reads them back to decide what to
     * ask about next, so they genuinely steer its behaviour rather than
     * describing it after the fact.
     *
     * Readable by the elder on purpose, and the security rules allow exactly
     * that. An agent's memory of a person that the person cannot see is
     * surveillance, however well intentioned the memory is.
     */
    fun observeAgentThreads(): Flow<List<AgentThread>>

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

    // ---- Medications (add / edit / remove) ----------------------------------
    //
    // The elder owns this list outright -- firestore.rules grants the patient full
    // create/update/delete on their own medications subcollection, unlike almost
    // everything else here, which the server writes. No return value beyond
    // success/failure: [observeMedications] is a live snapshot listener, so a
    // successful write shows up in the list on its own, and handing back an id or a
    // full [Medication] would just be data the caller has nothing to do with.

    /** Adds a new medication to the signed-in patient's own list. */
    suspend fun addMedication(input: MedicationInput): Result<Unit>

    /** Replaces an existing medication's fields in place. Same shape as [addMedication]. */
    suspend fun updateMedication(medicationId: String, input: MedicationInput): Result<Unit>

    /** Removes a medication from the signed-in patient's own list. Not reversible. */
    suspend fun deleteMedication(medicationId: String): Result<Unit>

    // ---- Vitals (manual entry) -----------------------------------------------

    /**
     * Records a reading taken between calls, e.g. at a pharmacy blood-pressure machine.
     *
     * [secondaryValue] carries diastolic for [VitalType.BLOOD_PRESSURE] and must be passed
     * as an explicit null for every other type -- `firestore.rules`' `vitals` create rule
     * requires the document to have exactly the keys `type, value, secondaryValue,
     * recordedAt, source`, so an implementation must write the key even when there is
     * nothing to put in it, not omit it.
     */
    suspend fun recordVitalReading(
        type: VitalType,
        value: Float,
        secondaryValue: Float? = null,
    ): Result<Unit>

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

    /**
     * Mints a one-time code this person reads out to whoever they want to share
     * their check-ins with.
     *
     * Generated HERE, on the elder's own device, and never on the caretaker's
     * dashboard. The backend enforces this too: generateLinkingCode requires the
     * caller to be the patient. Access to somebody's health record should be
     * given by them, from a device in their hand, rather than claimed on their
     * behalf and mentioned afterwards.
     */
    suspend fun generateLinkingCode(): Result<LinkingCode>

    /**
     * Takes someone's access to this person's check-ins away.
     *
     * The counterpart to [generateLinkingCode], and it did not exist for a long
     * time, which was a hole rather than a missing convenience. A product whose
     * entire ethical position is that the elder stays in control had a one-way
     * door: access could be granted and never withdrawn. Consent you cannot
     * withdraw is not consent.
     *
     * Returns true if someone was actually removed, false if they already had
     * no access. Both are successes; the caller wanted them not to be able to
     * see this record, and afterwards they cannot.
     */
    suspend fun unlinkCaretaker(caretakerId: String): Result<Boolean>

    /**
     * Makes sure there is a signed-in account and a patient record for it.
     *
     * Called once, at the end of onboarding. Until this existed the app never
     * authenticated at all, so every callable failed with a missing uid and the
     * entire backend was unreachable from the phone. Nothing surfaced it,
     * because each call site treats failure as a normal outcome and degrades to
     * demo data.
     *
     * Sign-in is anonymous by design. This person is 78 and the research on
     * onboarding for this cohort is unambiguous that text entry is the blocker;
     * asking them to invent and remember a password to receive a phone call
     * would lose more users than any other single decision. The anonymous uid is
     * the patient id, it persists on the device, and the caretaker's account is
     * the one with real credentials.
     */
    suspend fun ensureSignedInPatient(
        preferredName: String,
        dailyCheckInTime: String,
    ): Result<Unit>
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

data class LinkingCode(
    val code: String,
    val expiresAtIso: String,
)

/**
 * What the add/edit medication form collects, before it becomes a [Medication].
 *
 * Deliberately a separate type from [Medication] rather than reusing it directly: the form
 * never has (and must never invent) an id, and it has no business carrying [Medication]'s
 * derived getters ([Medication.daysOfSupplyRemaining] etc.) as if they were user input.
 */
data class MedicationInput(
    val name: String,
    val dose: String,
    val purpose: String,
    val schedule: List<LocalTime>,
    val criticality: Criticality,
    val dosesRemaining: Int,
    val dosesPerDay: Int,
    val refillLeadTimeDays: Int = 7,
    val foodGuidance: String? = null,
)

/**
 * What a brand-new signed-in account looks like: no name yet, no caretaker linked, nothing
 * borrowed from a demo persona to paper over it.
 *
 * [FirebaseCareLoopRepository] falls back to this -- never to
 * [com.careloop.app.data.mock.MockData.elder] -- whenever a real patient document is
 * missing, still loading, or fails to parse. A signed-in stranger seeing Margaret's profile
 * for even one frame was the actual bug this repository used to have; a signed-in account
 * seeing nothing at all describes its own state honestly instead.
 */
val EmptyElderProfile = ElderProfile(
    id = "",
    firstName = "",
    lastName = "",
    preferredName = "",
    age = 0,
    conditions = emptyList(),
    dailyCheckInTime = LocalTime.of(9, 0),
    caretaker = Caretaker(id = "", name = "", relationship = "", phone = "", email = ""),
)

data class LiveSessionToken(
    val token: String,
    val expiresAtEpochMillis: Long,
    val model: String,
    val wsHost: String,
    /** Socket path, server-supplied. See GeminiLiveClient.connect. */
    val wsPath: String,
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
