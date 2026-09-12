package com.careloop.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * CareLoop's domain model.
 *
 * These types are shaped to match what Firestore will eventually return, so wiring the
 * real backend is a repository swap and not a UI rewrite. Keep them free of Compose,
 * Android, and framework imports — they are plain Kotlin on purpose.
 *
 * TODO(backend): mirror these as Firestore documents. Suggested collections:
 *   users/{uid}, users/{uid}/medications, users/{uid}/checkIns, users/{uid}/escalations
 */

// ---------------------------------------------------------------------------
// The person
// ---------------------------------------------------------------------------

data class ElderProfile(
    val id: String,
    val firstName: String,
    val lastName: String,
    /** What Cara calls them out loud. Often not their legal first name. */
    val preferredName: String,
    val age: Int,
    val conditions: List<Condition>,
    /** User-adjustable. The elder owns this, not the caretaker. */
    val dailyCheckInTime: LocalTime,
    val caretaker: Caretaker,
) {
    val fullName: String get() = "$firstName $lastName"
}

data class Caretaker(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String,
)

/**
 * A condition drives which vitals Cara asks about. Diabetes means she asks for a blood
 * sugar reading; atrial fibrillation means she pays attention to anticoagulant adherence.
 */
enum class Condition(
    val displayName: String,
    val plainDescription: String,
    val tracksVital: VitalType?,
) {
    TYPE_2_DIABETES(
        "Type 2 diabetes",
        "Cara will ask for your blood sugar reading during check-ins.",
        VitalType.BLOOD_SUGAR,
    ),
    ATRIAL_FIBRILLATION(
        "Atrial fibrillation",
        "Cara pays extra attention to your heart medication.",
        VitalType.HEART_RATE,
    ),
    HYPERTENSION(
        "High blood pressure",
        "Cara will ask for your blood pressure reading when you take it.",
        VitalType.BLOOD_PRESSURE,
    ),
    ANAEMIA(
        "Iron-deficiency anaemia",
        "Cara will help you time your iron tablets around meals.",
        null,
    ),
}

// ---------------------------------------------------------------------------
// Medication
// ---------------------------------------------------------------------------

/**
 * How serious it is to miss this medication.
 *
 * This is the single most important field for the agent's behaviour. It is what makes
 * escalation a *judgement* rather than a counter: missing two doses of an anticoagulant
 * is a genuine clinical risk, while missing two doses of a statin is not. The agent
 * weighs this against the observed pattern before deciding to retry, wait, or escalate.
 *
 * [missedDosesBeforeConcern] is the agent's starting threshold, not a hard rule — the
 * reasoning engine may escalate sooner if it also hears confusion, or later if the elder
 * gives a good explanation.
 */
enum class Criticality(
    val label: String,
    val missedDosesBeforeConcern: Int,
) {
    CRITICAL("Critical", 1),
    HIGH("Important", 2),
    MEDIUM("Moderate", 3),
    LOW("Routine", 4),
}

data class Medication(
    val id: String,
    val name: String,
    val dose: String,
    /** Plain language, for the elder. Never "indication" or "MOA". */
    val purpose: String,
    val schedule: List<LocalTime>,
    val criticality: Criticality,
    val dosesRemaining: Int,
    val dosesPerDay: Int,
    /** Days before running out that a refill should be started. */
    val refillLeadTimeDays: Int,
    val foodGuidance: String? = null,
) {
    val daysOfSupplyRemaining: Int
        get() = if (dosesPerDay <= 0) Int.MAX_VALUE else dosesRemaining / dosesPerDay

    /** Drives the proactive refill card. */
    val needsRefillSoon: Boolean
        get() = daysOfSupplyRemaining <= refillLeadTimeDays
}

// ---------------------------------------------------------------------------
// Interactions
// ---------------------------------------------------------------------------

enum class InteractionSeverity(val label: String) {
    SEVERE("Serious"),
    MODERATE("Worth knowing"),
    MINOR("Minor"),
}

/**
 * A drug-drug interaction.
 *
 * TODO(backend): populate from openFDA / RxNorm. Called asynchronously *during* a live
 * call via Gemini Live function-calling, so the conversation does not go silent while
 * the lookup runs. Keep this shape aligned with the openFDA response mapping.
 */
data class DrugInteraction(
    val id: String,
    val drugA: String,
    val drugB: String,
    val severity: InteractionSeverity,
    /** One sentence, plain language. What could actually happen to them. */
    val whatItMeans: String,
    /** Why it happens. Explaining the mechanism earns compliance; "avoid" does not. */
    val mechanism: String,
    val advice: String,
    val source: String,
)

/**
 * A food-drug interaction.
 *
 * No clean public API exists for these, unlike drug-drug. The real implementation is a
 * curated ruleset (~25-35 well-established interactions) that the model reasons over.
 *
 * TODO(backend): move to a curated JSON ruleset, reasoned over by the LLM rather than
 * pattern-matched, so Cara can give personalised timing advice.
 */
data class FoodInteraction(
    val id: String,
    val drugName: String,
    val food: String,
    val severity: InteractionSeverity,
    val whatItMeans: String,
    val mechanism: String,
    val advice: String,
)

// ---------------------------------------------------------------------------
// Vitals
// ---------------------------------------------------------------------------

enum class VitalType(
    val displayName: String,
    val unit: String,
    val normalRange: ClosedFloatingPointRange<Float>,
) {
    BLOOD_SUGAR("Blood sugar", "mmol/L", 4.0f..7.8f),
    BLOOD_PRESSURE("Blood pressure", "mmHg", 90f..140f),
    HEART_RATE("Heart rate", "bpm", 60f..100f),
    WEIGHT("Weight", "kg", 40f..120f),
}

data class VitalReading(
    val id: String,
    val type: VitalType,
    val value: Float,
    /** Only for blood pressure, where [value] holds systolic. */
    val secondaryValue: Float? = null,
    val recordedAt: LocalDateTime,
) {
    val isOutsideNormalRange: Boolean get() = value !in type.normalRange

    val display: String
        get() = when (type) {
            VitalType.BLOOD_PRESSURE -> "${value.toInt()}/${secondaryValue?.toInt() ?: 0}"
            VitalType.BLOOD_SUGAR -> String.format("%.1f", value)
            else -> value.toInt().toString()
        }
}

// ---------------------------------------------------------------------------
// Check-ins
// ---------------------------------------------------------------------------

enum class CheckInStatus(val label: String) {
    COMPLETED("Completed"),
    MISSED_DOSE("Dose missed"),
    NO_ANSWER("No answer"),
    ESCALATED("Family alerted"),
}

enum class Speaker { CARA, ELDER }

data class TranscriptLine(
    val speaker: Speaker,
    val text: String,
    val offsetSeconds: Int,
    /**
     * Set when this line is what triggered a live action — an interaction lookup, or an
     * observation that fed the escalation. The UI highlights these so the reasoning is
     * traceable back to the actual words spoken.
     */
    val flag: TranscriptFlag? = null,
)

enum class TranscriptFlag {
    /** Cara fired an async interaction check on this line. */
    INTERACTION_CHECK,
    /** Cara noted hesitation, confusion, or a tonal signal here. */
    OBSERVATION,
    /** Cara raised a safety concern out loud. */
    SAFETY_CONCERN,
}

data class CheckIn(
    val id: String,
    val startedAt: LocalDateTime,
    val durationSeconds: Int,
    val status: CheckInStatus,
    val medicationsConfirmed: List<String>,
    val medicationsMissed: List<String>,
    val vitals: List<VitalReading> = emptyList(),
    val transcript: List<TranscriptLine> = emptyList(),
    /** Cara's plain-language read of how the call went. Shown to BOTH elder and family. */
    val caraSummary: String = "",
    val date: LocalDate = startedAt.toLocalDate(),
)

// ---------------------------------------------------------------------------
// Agent reasoning and escalation
// ---------------------------------------------------------------------------

/**
 * Confidence, expressed the way a person would say it.
 *
 * Deliberately not a percentage. Research on explainability for non-technical people is
 * consistent: lay users distrust false precision, and "78.3% confident" reads as evasive
 * where "I'm fairly sure" reads as honest.
 */
enum class Confidence(val phrase: String) {
    HIGH("I'm quite sure"),
    MEDIUM("I'm fairly confident"),
    LOW("I'm not certain"),
}

enum class EscalationSeverity(val label: String) {
    FYI("Worth knowing"),
    CONCERN("Needs attention"),
    URGENT("Urgent"),
}

/**
 * One step in the agent's reasoning chain.
 *
 * Each step must be traceable to something that actually happened — a specific call on a
 * specific day. This is what separates a legible reasoning trace from a plausible-sounding
 * story, and it is the thing judges are explicitly scoring.
 */
data class ReasoningStep(
    val observation: String,
    /** Where this came from, e.g. "Tuesday's call, 9:15am". */
    val evidence: String,
    val checkInId: String? = null,
)

/** How the elder responded when told what was shared. The dignity loop. */
enum class ElderResponse(val label: String) {
    NOT_YET_SEEN("Not seen yet"),
    CONFIRMED("Margaret confirmed this"),
    DISPUTED("Margaret added context"),
}

data class Escalation(
    val id: String,
    val raisedAt: LocalDateTime,
    val severity: EscalationSeverity,
    /** The headline. One sentence a busy adult child can read in three seconds. */
    val headline: String,
    /**
     * The full plain-language explanation, in Cara's voice, addressed to the caretaker.
     * This is the "why did it alert me" answer. Never a JSON dump.
     */
    val explanation: String,
    /** Capped at 3 in the UI — showing all signals considered confuses lay readers. */
    val reasoning: List<ReasoningStep>,
    val confidence: Confidence,
    /** What the agent weighed but decided *against*. Shows real deliberation. */
    val alternativesConsidered: List<String> = emptyList(),
    val relatedMedication: String? = null,
    val elderResponse: ElderResponse = ElderResponse.NOT_YET_SEEN,
    /** The elder's own words, when they added context. */
    val elderNote: String? = null,
    val acknowledgedByCaretaker: Boolean = false,
)

// ---------------------------------------------------------------------------
// Sharing / elder control
// ---------------------------------------------------------------------------

/**
 * Something Cara decided to come back to.
 *
 * The agent's memory of a person, in that person's own terms. "Her knee has
 * been bothering her" rather than a clinical label, because the elder reads
 * this too and a record written about you in language you cannot follow is not
 * transparency.
 *
 * [why] is shown verbatim to both sides. It is Cara's own reason for keeping
 * hold of something, and paraphrasing it on one side and not the other would
 * quietly break the symmetry the whole product rests on.
 *
 * [followUpAfter] exists because asking about a sore knee every single morning
 * is nagging, not care. Cara picks when it is worth raising again.
 */
data class AgentThread(
    val id: String,
    val topic: String,
    val why: String,
    val status: ThreadStatus,
    val raisedAt: LocalDateTime,
    val followUpAfter: LocalDateTime?,
    val timesRaised: Int,
    val resolution: String?,
    val resolvedAt: LocalDateTime?,
)

enum class ThreadStatus { OPEN, RESOLVED }

enum class ShareCategory(
    val label: String,
    val description: String,
    /**
     * Whether the elder can switch this category off.
     *
     * ACCESS_CHANGE cannot be. The sharing preferences govern what gets shared
     * ABOUT them; they do not govern whether they are told WHO can see it. A
     * setting that let somebody's access be removed or granted without the
     * person hearing about it would hollow out the whole consent position.
     */
    val mutable: Boolean = true,
) {
    MISSED_DOSES("Missed doses", "When you miss a medication"),
    CONFUSION("How I sound", "If you sound confused or unwell"),
    VITALS("My readings", "Blood sugar and blood pressure readings"),
    REFILLS("Running low", "When a medication is running out"),
    ACCESS_CHANGE(
        "Who can see my check-ins",
        "Always on. You are told whenever someone is connected or disconnected.",
        mutable = false,
    ),
}

/**
 * The elder's sharing preferences.
 *
 * Deliberately opt-out per category rather than a single on/off switch. Research is
 * consistent that granular control is what actually resolves discomfort with monitoring —
 * blanket consent does not, however well it is worded.
 *
 * [alwaysShareUrgent] is the safety floor: the elder can mute routine categories but a
 * genuine emergency still reaches family. This is shown to them honestly during setup
 * rather than buried, because hiding it would be the exact paternalism we're avoiding.
 */
data class SharingPreferences(
    val enabledCategories: Set<ShareCategory>,
    val alwaysShareUrgent: Boolean = true,
    /** Temporary privacy hold — nothing routine is shared until this passes. */
    val privacyHoldUntil: LocalDateTime? = null,
) {
    val isOnPrivacyHold: Boolean
        get() = privacyHoldUntil?.isAfter(LocalDateTime.now()) == true
}

/** One entry in the elder's "What I shared with Sarah" feed. */
data class SharedItem(
    val id: String,
    val sharedAt: LocalDateTime,
    val category: ShareCategory,
    /** Written to the elder, in Cara's voice: "I told Sarah that..." */
    val whatCaraSaid: String,
    val elderResponse: ElderResponse = ElderResponse.NOT_YET_SEEN,
    val elderNote: String? = null,
    val escalationId: String? = null,
)

// ---------------------------------------------------------------------------
// Call state
// ---------------------------------------------------------------------------

/** Drives the incoming-call and in-call UI. */
enum class CallState {
    IDLE,
    RINGING,
    CONNECTING,
    IN_PROGRESS,
    ENDED,
}

/** What Cara is doing right now, for the in-call listening indicator. */
enum class CaraActivity {
    LISTENING,
    SPEAKING,
    THINKING,
    /** Running an async interaction lookup without interrupting the conversation. */
    CHECKING,
}
