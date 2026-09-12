package com.careloop.app.data.mock

import com.careloop.app.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The demo dataset.
 *
 * ## Why this data is shaped the way it is
 *
 * The clinical detail is deliberate, not decoration. The whole product claim is that the
 * agent reasons over *patterns weighted by medical seriousness*, and that claim is only
 * legible if the underlying data is medically coherent.
 *
 * The narrative:
 * - Margaret takes **warfarin** for atrial fibrillation. Warfarin is [Criticality.CRITICAL]:
 *   missing it carries real stroke risk, so the agent's concern threshold is a single dose,
 *   not three. This is why CareLoop escalates on two misses when a reminder app would not
 *   escalate at all.
 * - She missed it **Tuesday and Thursday**, and on both calls sounded uncertain about
 *   whether she had already taken it. One miss is noise. A miss plus confusion, twice, on a
 *   critical anticoagulant, is a pattern — and that pattern is the trigger.
 * - On Thursday she also mentions taking **ibuprofen** for knee pain. Warfarin + NSAID is a
 *   well-established, genuinely dangerous combination (materially increased bleeding risk).
 *   Cara catches it mid-call, which is the single best demonstration of live async
 *   function-calling in the whole product.
 * - **Iron and dairy**: her ferrous sulfate is blocked by calcium. Cara explains the
 *   mechanism rather than saying "avoid", because explaining *why* is what earns compliance.
 *
 * Dates are relative to today so the demo never looks stale.
 *
 * TODO(backend): replace wholesale with Firestore reads. Nothing else should need to change.
 */
object MockData {

    private val today: LocalDate = LocalDate.now()
    private fun daysAgo(n: Long): LocalDate = today.minusDays(n)

    // -----------------------------------------------------------------------
    // People
    // -----------------------------------------------------------------------

    val caretaker = Caretaker(
        id = "care-1",
        name = "Sarah Whitfield-Chen",
        relationship = "Daughter",
        phone = "+44 7700 900142",
        email = "sarah.wc@example.com",
    )

    val elder = ElderProfile(
        id = "elder-1",
        firstName = "Margaret",
        lastName = "Whitfield",
        preferredName = "Margaret",
        age = 78,
        conditions = listOf(
            Condition.ATRIAL_FIBRILLATION,
            Condition.TYPE_2_DIABETES,
            Condition.HYPERTENSION,
            Condition.ANAEMIA,
        ),
        dailyCheckInTime = LocalTime.of(9, 0),
        caretaker = caretaker,
    )

    // -----------------------------------------------------------------------
    // Medications
    // -----------------------------------------------------------------------

    val warfarin = Medication(
        id = "med-warfarin",
        name = "Warfarin",
        dose = "3 mg",
        purpose = "Keeps your blood from clotting too easily, because of your heart rhythm.",
        schedule = listOf(LocalTime.of(18, 0)),
        criticality = Criticality.CRITICAL,
        dosesRemaining = 9,
        dosesPerDay = 1,
        refillLeadTimeDays = 10,
        foodGuidance = "Keep greens like spinach and kale steady day to day, not none, " +
            "just don't suddenly eat a lot more or less than usual.",
    )

    val metformin = Medication(
        id = "med-metformin",
        name = "Metformin",
        dose = "500 mg",
        purpose = "Helps keep your blood sugar steady.",
        schedule = listOf(LocalTime.of(9, 0), LocalTime.of(19, 0)),
        criticality = Criticality.HIGH,
        dosesRemaining = 44,
        dosesPerDay = 2,
        refillLeadTimeDays = 7,
        foodGuidance = "Take with food, it's much gentler on your stomach that way.",
    )

    val ramipril = Medication(
        id = "med-ramipril",
        name = "Ramipril",
        dose = "5 mg",
        purpose = "Keeps your blood pressure in a safe range.",
        schedule = listOf(LocalTime.of(9, 0)),
        criticality = Criticality.HIGH,
        dosesRemaining = 26,
        dosesPerDay = 1,
        refillLeadTimeDays = 7,
    )

    val ferrousSulfate = Medication(
        id = "med-iron",
        name = "Ferrous sulfate",
        dose = "200 mg",
        purpose = "Builds your iron back up, for the anaemia.",
        schedule = listOf(LocalTime.of(13, 0)),
        criticality = Criticality.MEDIUM,
        dosesRemaining = 31,
        dosesPerDay = 1,
        refillLeadTimeDays = 7,
        foodGuidance = "Leave about two hours between this and milk, cheese, or yoghurt. " +
            "Calcium and iron compete to be absorbed, so taken together you get less iron.",
    )

    val atorvastatin = Medication(
        id = "med-atorvastatin",
        name = "Atorvastatin",
        dose = "20 mg",
        purpose = "Helps manage your cholesterol.",
        schedule = listOf(LocalTime.of(21, 0)),
        criticality = Criticality.MEDIUM,
        dosesRemaining = 52,
        dosesPerDay = 1,
        refillLeadTimeDays = 7,
        foodGuidance = "Skip grapefruit and grapefruit juice with this one.",
    )

    val medications = listOf(warfarin, metformin, ramipril, ferrousSulfate, atorvastatin)

    // -----------------------------------------------------------------------
    // Interactions
    // -----------------------------------------------------------------------

    /** The one Cara catches live, mid-call. */
    val warfarinIbuprofen = DrugInteraction(
        id = "int-warf-ibu",
        drugA = "Warfarin",
        drugB = "Ibuprofen",
        severity = InteractionSeverity.SEVERE,
        whatItMeans = "Taken together, these make bleeding much more likely, including " +
            "bleeding in the stomach that can be hard to notice at first.",
        mechanism = "Ibuprofen irritates the stomach lining and also stops platelets " +
            "clumping properly. Warfarin is already slowing your clotting. The two " +
            "effects stack.",
        advice = "Paracetamol is usually the safer choice for pain alongside warfarin. " +
            "Worth checking with your GP or pharmacist before taking any more ibuprofen.",
        source = "openFDA / DrugBank",
    )

    val drugInteractions = listOf(warfarinIbuprofen)

    // -----------------------------------------------------------------------
    // What Cara is keeping an eye on
    // -----------------------------------------------------------------------

    /**
     * Deliberately written the way Margaret would describe it, not the way a
     * chart would. She reads this screen too, and a record kept about you in
     * language you cannot follow is not transparency.
     */
    val agentThreads = listOf(
        AgentThread(
            id = "th-knee",
            topic = "Her left knee",
            why = "She mentioned it hurt on Tuesday and again on Thursday, and it is " +
                "why she started taking ibuprofen. Worth knowing whether it has settled.",
            status = ThreadStatus.OPEN,
            raisedAt = LocalDateTime.now().minusDays(4),
            followUpAfter = LocalDateTime.now().plusDays(1),
            timesRaised = 2,
            resolution = null,
            resolvedAt = null,
        ),
        AgentThread(
            id = "th-warfarin",
            topic = "Being unsure about the warfarin",
            why = "Twice this week she was not certain whether she had taken it. Not a " +
                "clean miss either time, which is the part worth watching.",
            status = ThreadStatus.OPEN,
            raisedAt = LocalDateTime.now().minusDays(2),
            followUpAfter = LocalDateTime.now(),
            timesRaised = 2,
            resolution = null,
            resolvedAt = null,
        ),
        AgentThread(
            id = "th-sleep",
            topic = "Sleeping badly",
            why = "She sounded tired for a few mornings running and said she was not " +
                "sleeping well.",
            status = ThreadStatus.RESOLVED,
            raisedAt = LocalDateTime.now().minusDays(10),
            followUpAfter = null,
            timesRaised = 3,
            resolution = "She said it settled once the weather cooled down.",
            resolvedAt = LocalDateTime.now().minusDays(6),
        ),
    )

    val foodInteractions = listOf(
        FoodInteraction(
            id = "food-iron-calcium",
            drugName = "Ferrous sulfate",
            food = "Milk, cheese, yoghurt",
            severity = InteractionSeverity.MODERATE,
            whatItMeans = "Your iron tablet works much less well if you take it with dairy.",
            mechanism = "Calcium and iron are absorbed through the same route in your gut, " +
                "so they compete. Calcium usually wins.",
            advice = "Leave about two hours either side. Your lunchtime tablet is already " +
                "well away from your morning tea.",
        ),
        FoodInteraction(
            id = "food-warf-vitk",
            drugName = "Warfarin",
            food = "Spinach, kale, broccoli",
            severity = InteractionSeverity.MODERATE,
            whatItMeans = "Big changes in how many greens you eat can push your warfarin " +
                "level up or down.",
            mechanism = "These are high in vitamin K, which is exactly what warfarin works " +
                "against. It's the *change* that matters, not the greens themselves.",
            advice = "Keep them roughly steady week to week. You don't need to avoid them.",
        ),
        FoodInteraction(
            id = "food-atorva-grapefruit",
            drugName = "Atorvastatin",
            food = "Grapefruit",
            severity = InteractionSeverity.MODERATE,
            whatItMeans = "Grapefruit can raise the amount of atorvastatin in your blood " +
                "more than intended.",
            mechanism = "Grapefruit blocks the liver enzyme that normally clears the drug, " +
                "so more of it stays in your system.",
            advice = "Other citrus is fine, oranges don't do this.",
        ),
    )

    // -----------------------------------------------------------------------
    // Vitals — 14 days of blood sugar, gently trending up late in the week
    // -----------------------------------------------------------------------

    val bloodSugarReadings: List<VitalReading> = listOf(
        13L to 6.1f, 12L to 5.8f, 11L to 6.4f, 10L to 6.0f, 9L to 6.3f,
        8L to 5.9f, 7L to 6.2f, 6L to 6.6f, 5L to 6.9f, 4L to 7.4f,
        3L to 7.9f, 2L to 8.3f, 1L to 8.1f, 0L to 7.6f,
    ).map { (ago, value) ->
        VitalReading(
            id = "bs-$ago",
            type = VitalType.BLOOD_SUGAR,
            value = value,
            recordedAt = daysAgo(ago).atTime(9, 12),
        )
    }

    val bloodPressureReadings: List<VitalReading> = listOf(
        Triple(6L, 132f, 78f), Triple(4L, 138f, 82f),
        Triple(2L, 145f, 86f), Triple(0L, 141f, 84f),
    ).map { (ago, sys, dia) ->
        VitalReading(
            id = "bp-$ago",
            type = VitalType.BLOOD_PRESSURE,
            value = sys,
            secondaryValue = dia,
            recordedAt = daysAgo(ago).atTime(9, 14),
        )
    }

    // -----------------------------------------------------------------------
    // The Thursday transcript — the call that triggered escalation
    // -----------------------------------------------------------------------

    private val thursdayTranscript = listOf(
        TranscriptLine(Speaker.CARA,
            "Good morning, Margaret. It's Cara. How are you feeling today?", 0),
        TranscriptLine(Speaker.ELDER,
            "Oh, morning. I'm alright. A bit stiff, but alright.", 4),
        TranscriptLine(Speaker.CARA,
            "Stiff where, if you don't mind me asking?", 9),
        TranscriptLine(Speaker.ELDER,
            "My knee, mostly. I've been taking ibuprofen for it. Couple a day.", 12,
            TranscriptFlag.INTERACTION_CHECK),
        TranscriptLine(Speaker.CARA,
            "Thank you for telling me, let me just look at that alongside your other " +
                "tablets while we talk. Did you manage your warfarin yesterday evening?", 19),
        TranscriptLine(Speaker.ELDER,
            "The warfarin... I think so. I usually do it after the news. " +
                "Did I say that already? I'm not sure if I took it or not, love.", 26,
            TranscriptFlag.OBSERVATION),
        TranscriptLine(Speaker.CARA,
            "That's alright. Is the Thursday box still full, or is it empty?", 35),
        TranscriptLine(Speaker.ELDER,
            "Let me see... it's still got the tablet in it.", 40),
        TranscriptLine(Speaker.CARA,
            "Then it looks like yesterday's was missed. That's the second one this week, " +
                "Tuesday's was missed too. I'm not worried about you, but warfarin is the " +
                "one I'd rather we didn't let slip.", 45,
            TranscriptFlag.SAFETY_CONCERN),
        TranscriptLine(Speaker.ELDER,
            "Oh dear. I hadn't realised.", 56),
        TranscriptLine(Speaker.CARA,
            "There's one more thing I want to mention, and then I'll let you go. The " +
                "ibuprofen you've been taking for your knee doesn't sit well with warfarin, " +
                "together they make bleeding more likely. Paracetamol is usually safer. " +
                "Could you have a word with your GP before taking any more?", 60,
            TranscriptFlag.SAFETY_CONCERN),
        TranscriptLine(Speaker.ELDER,
            "I didn't know that. I'll ring them today.", 74),
        TranscriptLine(Speaker.CARA,
            "Thank you, Margaret. Because it's warfarin and it's happened twice, I'd like " +
                "to let Sarah know, just so she can help you get back on track. Is that " +
                "alright with you?", 79),
        TranscriptLine(Speaker.ELDER,
            "Yes, that's fine. She worries anyway.", 90),
        TranscriptLine(Speaker.CARA,
            "I'll keep it brief with her. Take care of that knee, and I'll speak to you " +
                "tomorrow morning.", 94),
    )

    // -----------------------------------------------------------------------
    // Check-in history — 7 days
    // -----------------------------------------------------------------------

    val checkIns: List<CheckIn> = listOf(
        CheckIn(
            id = "ci-6", startedAt = daysAgo(6).atTime(9, 2), durationSeconds = 71,
            status = CheckInStatus.COMPLETED,
            medicationsConfirmed = listOf("Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = emptyList(),
            vitals = listOf(bloodSugarReadings[7]),
            caraSummary = "All medications taken. Margaret sounded like herself.",
        ),
        CheckIn(
            id = "ci-5", startedAt = daysAgo(5).atTime(9, 1), durationSeconds = 64,
            status = CheckInStatus.COMPLETED,
            medicationsConfirmed = listOf("Warfarin", "Metformin", "Ramipril"),
            medicationsMissed = listOf("Ferrous sulfate"),
            vitals = listOf(bloodSugarReadings[8]),
            caraSummary = "Iron tablet missed at lunch. Margaret said she'd forgotten it " +
                "was in the kitchen drawer. Not concerning on its own.",
        ),
        CheckIn(
            id = "ci-4", startedAt = daysAgo(4).atTime(9, 3), durationSeconds = 88,
            status = CheckInStatus.MISSED_DOSE,
            medicationsConfirmed = listOf("Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = listOf("Warfarin"),
            vitals = listOf(bloodSugarReadings[9]),
            caraSummary = "Warfarin missed last night. Margaret wasn't sure whether she'd " +
                "taken it. I've made a note to watch this, it's the first time.",
        ),
        CheckIn(
            id = "ci-3", startedAt = daysAgo(3).atTime(9, 0), durationSeconds = 59,
            status = CheckInStatus.COMPLETED,
            medicationsConfirmed = listOf("Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = emptyList(),
            vitals = listOf(bloodSugarReadings[10]),
            caraSummary = "Back on track. Warfarin taken.",
        ),
        CheckIn(
            id = "ci-2", startedAt = daysAgo(2).atTime(9, 1), durationSeconds = 112,
            status = CheckInStatus.ESCALATED,
            medicationsConfirmed = listOf("Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = listOf("Warfarin"),
            vitals = listOf(bloodSugarReadings[11], bloodPressureReadings[2]),
            transcript = thursdayTranscript,
            caraSummary = "Warfarin missed again, and Margaret was unsure whether she'd " +
                "taken it, the same uncertainty as Tuesday. She also mentioned taking " +
                "ibuprofen for her knee, which doesn't mix well with warfarin. I let " +
                "Sarah know.",
        ),
        CheckIn(
            id = "ci-1", startedAt = daysAgo(1).atTime(9, 2), durationSeconds = 78,
            status = CheckInStatus.COMPLETED,
            medicationsConfirmed = listOf("Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = emptyList(),
            vitals = listOf(bloodSugarReadings[12]),
            caraSummary = "Warfarin taken, and Margaret had spoken to her GP about the " +
                "ibuprofen. She's switched to paracetamol.",
        ),
        CheckIn(
            id = "ci-0", startedAt = today.atTime(9, 0), durationSeconds = 66,
            status = CheckInStatus.COMPLETED,
            medicationsConfirmed = listOf("Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"),
            medicationsMissed = emptyList(),
            vitals = listOf(bloodSugarReadings[13], bloodPressureReadings[3]),
            caraSummary = "Everything taken. Blood sugar is coming back down.",
        ),
    ).sortedByDescending { it.startedAt }

    // -----------------------------------------------------------------------
    // Escalations
    // -----------------------------------------------------------------------

    val primaryEscalation = Escalation(
        id = "esc-1",
        raisedAt = daysAgo(2).atTime(9, 4),
        severity = EscalationSeverity.CONCERN,
        headline = "Margaret has missed her warfarin twice this week",
        explanation = "I'm reaching out because your mother missed her warfarin on Tuesday " +
            "evening and again on Thursday, and on both calls she wasn't sure whether " +
            "she'd taken it. One missed dose wouldn't have worried me. Two, with the same " +
            "uncertainty each time, on the medication that matters most for her heart " +
            "rhythm, is a pattern I didn't want to sit on.\n\n" +
            "She also mentioned she's been taking ibuprofen for her knee. That doesn't mix " +
            "well with warfarin, together they make bleeding more likely, so I asked her " +
            "to speak to her GP before taking any more. She said she would.\n\n" +
            "She knows I'm telling you. I asked her on the call and she was happy for me to.",
        reasoning = listOf(
            ReasoningStep(
                observation = "Warfarin missed on two of the last five evenings",
                evidence = "Tuesday and Thursday check-ins",
                checkInId = "ci-2",
            ),
            ReasoningStep(
                observation = "She was unsure whether she'd taken it, both times, unprompted",
                evidence = "\"I'm not sure if I took it or not, love\", Thursday, 9:01am",
                checkInId = "ci-2",
            ),
            ReasoningStep(
                observation = "Warfarin is her highest-risk medication, so my threshold for " +
                    "it is lower than for the others",
                evidence = "Anticoagulant for atrial fibrillation",
            ),
        ),
        confidence = Confidence.HIGH,
        alternativesConsidered = listOf(
            "Waiting another day, I decided against it because warfarin is the one " +
                "medication where a wait carries real risk.",
            "Treating it as forgetfulness only, but the same uncertainty twice, rather " +
                "than simply forgetting, is what changed my mind.",
            "Calling her again in the evening instead, I'll still do this, but it " +
                "didn't feel like a reason to delay telling you.",
        ),
        relatedMedication = "Warfarin",
        elderResponse = ElderResponse.CONFIRMED,
        acknowledgedByCaretaker = true,
    )

    val minorEscalation = Escalation(
        id = "esc-2",
        raisedAt = daysAgo(1).atTime(9, 6),
        severity = EscalationSeverity.FYI,
        headline = "Warfarin runs out in about nine days",
        explanation = "Not urgent, but worth starting now, repeat prescriptions for " +
            "warfarin usually take a few days, and it's not one I'd want her to run out of. " +
            "She has nine days left.",
        reasoning = listOf(
            ReasoningStep(
                observation = "9 doses remaining, one per day",
                evidence = "Counted down from her last refill",
            ),
            ReasoningStep(
                observation = "Repeat prescriptions typically take 3-5 working days",
                evidence = "Her usual pharmacy's stated turnaround",
            ),
        ),
        confidence = Confidence.HIGH,
        relatedMedication = "Warfarin",
    )

    val escalations = listOf(primaryEscalation, minorEscalation)

    // -----------------------------------------------------------------------
    // Sharing / elder control
    // -----------------------------------------------------------------------

    val sharingPreferences = SharingPreferences(
        enabledCategories = setOf(
            ShareCategory.MISSED_DOSES,
            ShareCategory.CONFUSION,
            ShareCategory.REFILLS,
        ),
        alwaysShareUrgent = true,
        privacyHoldUntil = null,
    )

    val sharedItems: List<SharedItem> = listOf(
        SharedItem(
            id = "shr-1",
            sharedAt = daysAgo(2).atTime(9, 4),
            category = ShareCategory.MISSED_DOSES,
            whatCaraSaid = "I told Sarah that you'd missed your warfarin on Tuesday and " +
                "Thursday, and that you weren't sure whether you'd taken it.",
            elderResponse = ElderResponse.CONFIRMED,
            escalationId = "esc-1",
        ),
        SharedItem(
            id = "shr-2",
            sharedAt = daysAgo(1).atTime(9, 6),
            category = ShareCategory.REFILLS,
            whatCaraSaid = "I mentioned to Sarah that your warfarin is running low, so she " +
                "can help you order more.",
            elderResponse = ElderResponse.NOT_YET_SEEN,
            escalationId = "esc-2",
        ),
        SharedItem(
            id = "shr-3",
            sharedAt = daysAgo(5).atTime(9, 3),
            category = ShareCategory.MISSED_DOSES,
            whatCaraSaid = "I told Sarah you'd missed your iron tablet at lunchtime.",
            elderResponse = ElderResponse.DISPUTED,
            elderNote = "I did take it, just later in the afternoon with my tea.",
        ),
    )

    // -----------------------------------------------------------------------
    // Today
    // -----------------------------------------------------------------------

    val todaysCheckIn: CheckIn? get() = checkIns.firstOrNull { it.date == today }
    val nextCallTime: LocalTime get() = elder.dailyCheckInTime
    val medicationsNeedingRefill: List<Medication> get() = medications.filter { it.needsRefillSoon }
}
