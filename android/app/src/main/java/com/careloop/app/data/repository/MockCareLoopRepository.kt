package com.careloop.app.data.repository

import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * In-memory implementation backed by [MockData].
 *
 * Mutations are real within a session — changing the check-in time or disputing a shared
 * item genuinely updates the flows and the UI reacts. That matters for a live demo: a
 * screen that silently ignores input reads as broken even when everything else is polished.
 *
 * Small artificial delays are included where the real thing would hit the network, so the
 * loading states are actually exercised rather than being dead code that breaks the first
 * time a real backend is slower than zero milliseconds.
 */
class MockCareLoopRepository : CareLoopRepository {

    private val elderState = MutableStateFlow(MockData.elder)
    private val medicationsState = MutableStateFlow(MockData.medications)
    private val checkInsState = MutableStateFlow(MockData.checkIns)
    private val escalationsState = MutableStateFlow(MockData.escalations)
    private val sharingPrefsState = MutableStateFlow(MockData.sharingPreferences)
    private val sharedItemsState = MutableStateFlow(MockData.sharedItems)

    /**
     * Vitals are stored in their own right, not derived from check-ins.
     *
     * They were originally derived by flat-mapping `checkIns.vitals`, which was wrong: only
     * 7 of the 14 blood-sugar readings are attached to a check-in, so a chart would render
     * 14 points on its first frame and then visibly snap down to 7 when the flow emitted.
     *
     * It is also the more accurate model. A reading is a fact about the person, not a
     * property of a phone call — Firestore will store these in their own collection, and
     * readings taken outside a check-in still need somewhere to live.
     */
    private val vitalsState = MutableStateFlow(
        MockData.bloodSugarReadings + MockData.bloodPressureReadings
    )

    override fun observeElder(): Flow<ElderProfile> = elderState.asStateFlow()
    override fun observeMedications(): Flow<List<Medication>> = medicationsState.asStateFlow()
    override fun observeCheckIns(): Flow<List<CheckIn>> = checkInsState.asStateFlow()
    override fun observeEscalations(): Flow<List<Escalation>> = escalationsState.asStateFlow()

    override fun observeVitals(type: VitalType): Flow<List<VitalReading>> =
        vitalsState.map { readings ->
            readings.filter { it.type == type }.sortedBy { it.recordedAt }
        }

    override fun observeSharingPreferences(): Flow<SharingPreferences> =
        sharingPrefsState.asStateFlow()

    override fun observeSharedItems(): Flow<List<SharedItem>> = sharedItemsState.asStateFlow()

    override suspend fun getCheckIn(id: String): CheckIn? =
        checkInsState.value.firstOrNull { it.id == id }

    override suspend fun getEscalation(id: String): Escalation? =
        escalationsState.value.firstOrNull { it.id == id }

    /**
     * TODO(backend): replace with a live openFDA / RxNorm lookup. This is called
     * asynchronously *during* a call via Gemini Live function-calling, so it must never
     * block the conversation — keep it suspending and keep the UI responsive while it runs.
     */
    override suspend fun findDrugInteraction(drugA: String, drugB: String): DrugInteraction? {
        delay(600) // stand-in for the network round trip
        val a = drugA.trim().lowercase()
        val b = drugB.trim().lowercase()
        return MockData.drugInteractions.firstOrNull {
            (it.drugA.lowercase() == a && it.drugB.lowercase() == b) ||
                (it.drugA.lowercase() == b && it.drugB.lowercase() == a)
        }
    }

    override suspend fun findFoodInteractions(drugName: String): List<FoodInteraction> {
        delay(200)
        return MockData.foodInteractions.filter {
            it.drugName.equals(drugName.trim(), ignoreCase = true)
        }
    }

    override suspend fun updateCheckInTime(time: LocalTime) {
        elderState.value = elderState.value.copy(dailyCheckInTime = time)
    }

    override suspend fun updateSharingPreferences(preferences: SharingPreferences) {
        sharingPrefsState.value = preferences
    }

    // -----------------------------------------------------------------------
    // Medications (add / edit / remove)
    //
    // Mutations are real within a session, same rule as the rest of this class: a demo
    // build where "Add a medication" visibly does nothing is worse than one where it works.
    // -----------------------------------------------------------------------

    override suspend fun addMedication(input: MedicationInput): Result<Unit> {
        delay(300)
        medicationsState.value = medicationsState.value + Medication(
            id = "med-${System.currentTimeMillis()}",
            name = input.name,
            dose = input.dose,
            purpose = input.purpose,
            schedule = input.schedule,
            criticality = input.criticality,
            dosesRemaining = input.dosesRemaining,
            dosesPerDay = input.dosesPerDay,
            refillLeadTimeDays = input.refillLeadTimeDays,
            foodGuidance = input.foodGuidance,
        )
        return Result.success(Unit)
    }

    override suspend fun updateMedication(
        medicationId: String,
        input: MedicationInput,
    ): Result<Unit> {
        delay(300)
        medicationsState.value = medicationsState.value.map { medication ->
            if (medication.id != medicationId) {
                medication
            } else {
                medication.copy(
                    name = input.name,
                    dose = input.dose,
                    purpose = input.purpose,
                    schedule = input.schedule,
                    criticality = input.criticality,
                    dosesRemaining = input.dosesRemaining,
                    dosesPerDay = input.dosesPerDay,
                    refillLeadTimeDays = input.refillLeadTimeDays,
                    foodGuidance = input.foodGuidance,
                )
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteMedication(medicationId: String): Result<Unit> {
        delay(200)
        medicationsState.value = medicationsState.value.filter { it.id != medicationId }
        return Result.success(Unit)
    }

    // -----------------------------------------------------------------------
    // Vitals (manual entry)
    // -----------------------------------------------------------------------

    override suspend fun recordVitalReading(
        type: VitalType,
        value: Float,
        secondaryValue: Float?,
    ): Result<Unit> {
        delay(300)
        vitalsState.value = vitalsState.value + VitalReading(
            id = "vital-${System.currentTimeMillis()}",
            type = type,
            value = value,
            secondaryValue = secondaryValue,
            recordedAt = LocalDateTime.now(),
        )
        return Result.success(Unit)
    }

    override suspend fun respondToSharedItem(
        itemId: String,
        response: ElderResponse,
        note: String?,
    ) {
        sharedItemsState.value = sharedItemsState.value.map { item ->
            if (item.id == itemId) {
                item.copy(elderResponse = response, elderNote = note)
            } else {
                item
            }
        }
        // Keep the caretaker's view honest: the elder's response propagates to the
        // escalation the family sees. Symmetric transparency, in both directions.
        sharedItemsState.value.firstOrNull { it.id == itemId }?.escalationId?.let { escId ->
            escalationsState.value = escalationsState.value.map { esc ->
                if (esc.id == escId) esc.copy(elderResponse = response, elderNote = note) else esc
            }
        }
    }

    /**
     * TODO(backend): POST to a Cloud Function that sends a high-priority FCM data message
     * to the elder's device, which triggers the CallStyle notification.
     */
    override suspend fun requestManualCheckIn(): Result<Unit> {
        delay(900)
        return Result.success(Unit)
    }

    // -----------------------------------------------------------------------
    // Backend operations, simulated
    //
    // These succeed locally so every screen behaves identically with or without
    // a backend. A demo build that silently no-ops is fine; one that throws
    // would make the UI look broken when it is not.
    // -----------------------------------------------------------------------

    override suspend fun checkInteraction(
        substance: String,
        kind: String,
    ): Result<InteractionCheckResult> {
        delay(600)
        val drug = MockData.drugInteractions.firstOrNull {
            substance.contains(it.drugB, ignoreCase = true) ||
                substance.contains(it.drugA, ignoreCase = true)
        }
        val food = MockData.foodInteractions.filter {
            substance.contains(it.food.substringBefore(',').trim(), ignoreCase = true)
        }
        return Result.success(
            InteractionCheckResult(
                found = drug != null || food.isNotEmpty(),
                resolvedName = substance,
                drugInteractions = listOfNotNull(drug),
                foodInteractions = food,
                spokenSummary = drug?.let { "${it.whatItMeans} ${it.mechanism}" }
                    ?: food.firstOrNull()?.let { "${it.whatItMeans} ${it.mechanism}" }
                    ?: "",
            ),
        )
    }

    override suspend fun registerDeviceToken(token: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun reportCallOutcome(
        callAttemptId: String,
        outcome: String,
        durationSeconds: Int,
    ): Result<Unit> {
        delay(200)
        return Result.success(Unit)
    }

    override suspend fun submitCheckIn(submission: CheckInSubmission): Result<CheckInResult> {
        delay(500)
        return Result.success(
            CheckInResult(
                checkInId = "demo-${System.currentTimeMillis()}",
                action = if (submission.medicationsMissed.isEmpty()) "no_action" else "escalate",
                escalationId = null,
            ),
        )
    }

    /**
     * No token in demo mode.
     *
     * Returns a failure rather than a fake token so the call screen takes its
     * scripted path deliberately, instead of attempting a real WebSocket with a
     * bogus credential and failing in a confusing way.
     */
    override suspend fun mintLiveSessionToken(): Result<LiveSessionToken> {
        return Result.failure(IllegalStateException("DEMO_MODE"))
    }

    // Cara's own tool calls. In demo mode these succeed silently: the scripted
    // call never invokes them, and returning a failure would make a future caller
    // think something had gone wrong when nothing had.
    override suspend fun reportUrgentConcern(whatTheyDescribed: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun rememberForNextTime(
        topic: String,
        why: String,
        followUpInDays: Int,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun closeOpenThread(topic: String, whatHappened: String): Result<Unit> =
        Result.success(Unit)

    /**
     * A stable, obviously-fake code.
     *
     * Not random: on stage this screen may be shown more than once, and a code
     * that changes every time invites someone to ask whether it is real. The
     * alphabet still excludes O, 0, I, 1 and L so it demonstrates the actual
     * rule.
     */
    override suspend fun ensureSignedInPatient(
        preferredName: String,
        dailyCheckInTime: String,
    ): Result<Unit> = Result.success(Unit)

    override fun observeAgentThreads(): Flow<List<AgentThread>> =
        MutableStateFlow(MockData.agentThreads).asStateFlow()

    override suspend fun unlinkCaretaker(caretakerId: String): Result<Boolean> =
        Result.success(true)

    override suspend fun generateLinkingCode(): Result<LinkingCode> =
        Result.success(
            LinkingCode(code = "DEMOCARE", expiresAtIso = ""),
        )

    /** Demo helper: appends a completed check-in so the history visibly grows on stage. */
    fun appendDemoCheckIn(checkIn: CheckIn) {
        checkInsState.value = (listOf(checkIn) + checkInsState.value)
            .sortedByDescending { it.startedAt }
    }

    @Suppress("unused")
    private fun now(): LocalDateTime = LocalDateTime.now()
}
