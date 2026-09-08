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

    override fun observeElder(): Flow<ElderProfile> = elderState.asStateFlow()
    override fun observeMedications(): Flow<List<Medication>> = medicationsState.asStateFlow()
    override fun observeCheckIns(): Flow<List<CheckIn>> = checkInsState.asStateFlow()
    override fun observeEscalations(): Flow<List<Escalation>> = escalationsState.asStateFlow()

    override fun observeVitals(type: VitalType): Flow<List<VitalReading>> =
        checkInsState.map { checkIns ->
            checkIns.flatMap { it.vitals }
                .filter { it.type == type }
                .sortedBy { it.recordedAt }
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

    /** Demo helper: appends a completed check-in so the history visibly grows on stage. */
    fun appendDemoCheckIn(checkIn: CheckIn) {
        checkInsState.value = (listOf(checkIn) + checkInsState.value)
            .sortedByDescending { it.startedAt }
    }

    @Suppress("unused")
    private fun now(): LocalDateTime = LocalDateTime.now()
}
