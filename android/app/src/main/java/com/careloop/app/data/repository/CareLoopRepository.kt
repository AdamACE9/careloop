package com.careloop.app.data.repository

import com.careloop.app.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the UI and wherever data actually lives.
 *
 * Everything above this line (ViewModels, Compose) knows only this interface. That is the
 * whole point: swapping [MockCareLoopRepository] for a Firestore-backed implementation
 * should require **zero changes to any screen**.
 *
 * Returns [Flow] rather than suspend functions even though the mock data is static,
 * because Firestore snapshot listeners are naturally streams. Designing the interface
 * around the eventual shape means the swap doesn't force a rewrite of every call site.
 *
 * TODO(backend): add `FirebaseCareLoopRepository` implementing this against Firestore,
 * then change the one line in `AppContainer`. Do not add Firebase types to this interface.
 */
interface CareLoopRepository {

    fun observeElder(): Flow<ElderProfile>
    fun observeMedications(): Flow<List<Medication>>
    fun observeCheckIns(): Flow<List<CheckIn>>
    fun observeEscalations(): Flow<List<Escalation>>
    fun observeVitals(type: VitalType): Flow<List<VitalReading>>

    fun observeSharingPreferences(): Flow<SharingPreferences>
    fun observeSharedItems(): Flow<List<SharedItem>>

    suspend fun getCheckIn(id: String): CheckIn?
    suspend fun getEscalation(id: String): Escalation?

    suspend fun findDrugInteraction(drugA: String, drugB: String): DrugInteraction?
    suspend fun findFoodInteractions(drugName: String): List<FoodInteraction>

    suspend fun updateCheckInTime(time: java.time.LocalTime)
    suspend fun updateSharingPreferences(preferences: SharingPreferences)

    /** The elder confirming or disputing something Cara shared. The dignity loop. */
    suspend fun respondToSharedItem(itemId: String, response: ElderResponse, note: String?)

    /** Caretaker-triggered "check on them now". */
    suspend fun requestManualCheckIn(): Result<Unit>
}
