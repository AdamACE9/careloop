package com.careloop.app.ui.screens.call

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.careloop.app.call.CallSession
import com.careloop.app.call.GeminiLiveClient
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.CaraActivity
import com.careloop.app.data.model.DrugInteraction
import com.careloop.app.data.model.Speaker
import com.careloop.app.data.model.TranscriptFlag
import com.careloop.app.data.model.TranscriptLine
import com.careloop.app.data.model.VitalType
import com.careloop.app.data.repository.CareLoopRepository
import com.careloop.app.data.repository.CheckInSubmission
import com.careloop.app.data.repository.RecordedVital
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Drives an actual conversation with Cara.
 *
 * ## What this replaced
 *
 * Until this existed, the live call screen played a recorded transcript on a
 * timer. [GeminiLiveClient] had been written but had no call site anywhere in the
 * app, so the product's signature interaction was a very convincing animation.
 * This is the piece that connects them.
 *
 * ## Two modes, and why the fake one stays
 *
 * [Mode.LIVE] is a real Gemini Live session. [Mode.DEMO] plays the scripted
 * conversation, and it is kept deliberately rather than deleted. It is the
 * fallback when there is no Firebase project, no microphone permission, or no
 * free-tier quota left, and each of those is a realistic state to be in five
 * minutes before demonstrating this to somebody. A screen that says "Cara could
 * not connect" and then does nothing is worse than one that shows what the
 * product does, provided it never claims the scripted call was real. The UI
 * labels demo mode plainly.
 *
 * ## Tool calls are the agency
 *
 * Everything that makes this agentic rather than conversational happens in
 * [handleToolCall]: checking an interaction against the person's real medication
 * list mid-sentence, recording what was actually taken, noticing something worth
 * returning to days later, and escalating in the moment when something is wrong.
 * The model decides which of those to do and when. This class only executes them
 * and keeps the UI honest about what is happening.
 */
class LiveCallViewModel(
    private val repository: CareLoopRepository,
) : ViewModel() {

    enum class Mode {
        /** Minting a token and opening the socket. */
        CONNECTING,

        /** A real conversation. */
        LIVE,

        /** The scripted conversation, labelled as such on screen. */
        DEMO,

        /** Could not connect and could not fall back. */
        FAILED,
    }

    private val _mode = MutableStateFlow(Mode.CONNECTING)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** Plain-language reason, shown to the user when it is not a live call. */
    private val _statusNote = MutableStateFlow<String?>(null)
    val statusNote: StateFlow<String?> = _statusNote.asStateFlow()

    private val _activity = MutableStateFlow(CaraActivity.LISTENING)
    val activity: StateFlow<CaraActivity> = _activity.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val transcript: StateFlow<List<TranscriptLine>> = _transcript.asStateFlow()

    private val _interaction = MutableStateFlow<DrugInteraction?>(null)
    val interaction: StateFlow<DrugInteraction?> = _interaction.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    // ---- What the call produced ---------------------------------------------
    // Accumulated from Cara's tool calls and submitted as one check-in at the
    // end. Writing each one to Firestore as it arrives would mean a call that
    // drops halfway leaves a half-written record that the reasoning engine would
    // then read as a set of missed doses.

    private val confirmed = mutableListOf<String>()
    private val missed = mutableListOf<String>()
    private val vitals = mutableListOf<RecordedVital>()
    private var confusionSignal = 0f
    private var hesitationSignal = 0f
    private var toneNote: String? = null

    private var client: GeminiLiveClient? = null
    private var started = false

    /**
     * @param hasAudioPermission whether RECORD_AUDIO has actually been granted.
     *   Passed in rather than checked here so this class needs no Context, and
     *   so the screen owns the permission conversation.
     */
    fun start(hasAudioPermission: Boolean) {
        if (started) return
        started = true

        if (!hasAudioPermission) {
            // Not an error worth a red screen. The person can still see exactly
            // what a call looks like, and the missing permission is fixable.
            beginDemo("Cara needs the microphone to talk with you. This is a recorded example.")
            return
        }

        viewModelScope.launch {
            val token = repository.mintLiveSessionToken().getOrElse { error ->
                beginDemo(reasonFor(error))
                return@launch
            }

            if (token.systemInstruction.isBlank()) {
                // Connecting anyway would produce a Cara with no persona and no
                // idea what this person takes, which is worse than an honest
                // scripted call.
                beginDemo("Cara is not set up for this account yet. This is a recorded example.")
                return@launch
            }

            connectLive(token)
        }
    }

    @SuppressLint("MissingPermission") // start() only reaches here once granted.
    private fun connectLive(token: com.careloop.app.data.repository.LiveSessionToken) {
        val live = GeminiLiveClient(
            scope = viewModelScope,
            onToolCall = ::handleToolCall,
        )
        client = live

        viewModelScope.launch {
            live.activity.collect { _activity.value = it }
        }
        viewModelScope.launch {
            live.transcript.collect { lines ->
                _transcript.value = lines
                _elapsedSeconds.value = live.sessionDurationSeconds
            }
        }
        viewModelScope.launch {
            live.connectionState.collect { state ->
                when (state) {
                    GeminiLiveClient.ConnectionState.CONNECTED -> {
                        _mode.value = Mode.LIVE
                        _statusNote.value = null
                    }
                    GeminiLiveClient.ConnectionState.RECONNECTING ->
                        _statusNote.value = "Reconnecting"
                    GeminiLiveClient.ConnectionState.FAILED ->
                        // Mid-call, so the scripted fallback would be a lie about
                        // a conversation the person was already having.
                        _statusNote.value = "The line dropped. You can end the call and try again."
                    else -> Unit
                }
            }
        }

        live.connect(token)
    }

    private fun beginDemo(note: String) {
        _mode.value = Mode.DEMO
        _statusNote.value = note
        playScriptedCall()
    }

    /**
     * Turns a token failure into something a person can act on.
     *
     * Quota exhaustion in particular deserves its own wording: it is temporary
     * and it is nobody's mistake, and "something went wrong" invites someone to
     * go looking for a fault that is not there.
     */
    private fun reasonFor(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("DEMO_MODE") ->
                "Running on example data. Connect a Firebase project to make real calls."
            message.contains("QUOTA", ignoreCase = true) ||
                message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                "Cara has reached today's call limit. This is a recorded example."
            message.contains("BUSY", ignoreCase = true) ->
                "Cara is on another call right now. This is a recorded example."
            // The backend could not find this person's record. That is a setup
            // problem, not a network one, and saying "could not reach Cara"
            // sends someone to check their wifi for something wifi cannot fix.
            message.contains("NOT_FOUND", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true) ->
                "Your setup is not finished, so Cara does not know who to ask about. Open Settings to finish it."
            else -> "Could not reach Cara just now. This is a recorded example."
        }
    }

    // =========================================================================
    // Cara's tools
    // =========================================================================

    /**
     * Executes a tool the model chose to call, and returns what it should be told.
     *
     * Every branch returns a result rather than throwing. A thrown exception here
     * surfaces to Gemini as a tool failure in the middle of a spoken sentence,
     * and the recovery from that is usually Cara apologising for something the
     * person never saw.
     */
    private suspend fun handleToolCall(name: String, args: JSONObject): JSONObject =
        when (name) {
            "check_interaction" -> checkInteraction(args)
            "record_medication_status" -> recordMedicationStatus(args)
            "record_vital" -> recordVital(args)
            "report_urgent_concern" -> reportUrgent(args)
            "remember_for_next_time" -> rememberForNextTime(args)
            "close_open_thread" -> closeThread(args)
            else -> {
                Log.w(TAG, "Unknown tool requested: $name")
                JSONObject().put("error", "unknown_tool")
            }
        }

    private suspend fun checkInteraction(args: JSONObject): JSONObject {
        val substance = args.optString("substance")
        val kind = args.optString("kind").ifBlank { "drug" }

        val result = repository.checkInteraction(substance, kind).getOrElse {
            // The conversation continues without it. Cara is told plainly that
            // the lookup failed so she can say so rather than invent an answer.
            return JSONObject()
                .put("found", false)
                .put("lookupFailed", true)
        }

        result.drugInteractions.firstOrNull()?.let { _interaction.value = it }

        // Mark the line that caused this, so the transcript shows the reasoning
        // attached to the words that triggered it rather than floating loose.
        flagLastElderLine(TranscriptFlag.INTERACTION_CHECK)

        return JSONObject().apply {
            put("found", result.found)
            put("resolvedName", result.resolvedName ?: substance)
            put("summary", result.spokenSummary)
        }
    }

    private fun recordMedicationStatus(args: JSONObject): JSONObject {
        val medication = args.optString("medicationName")
        if (medication.isBlank()) return JSONObject().put("recorded", false)

        val taken = args.optBoolean("taken", false)
        val uncertain = args.optBoolean("uncertain", false)

        when {
            uncertain -> {
                // Uncertainty is recorded as a miss AND as a tone signal. The
                // reasoning engine weighs "not sure" above a clean no, and it can
                // only do that if both facts survive the call.
                if (!missed.contains(medication)) missed += medication
                confusionSignal = maxOf(confusionSignal, 0.8f)
                hesitationSignal = maxOf(hesitationSignal, 0.6f)
                toneNote = "unsure whether $medication had been taken"
                flagLastElderLine(TranscriptFlag.OBSERVATION)
            }
            taken -> if (!confirmed.contains(medication)) confirmed += medication
            else -> if (!missed.contains(medication)) missed += medication
        }

        return JSONObject().put("recorded", true)
    }

    private fun recordVital(args: JSONObject): JSONObject {
        val type = when (args.optString("type")) {
            "blood_sugar" -> VitalType.BLOOD_SUGAR
            "blood_pressure" -> VitalType.BLOOD_PRESSURE
            "heart_rate" -> VitalType.HEART_RATE
            "weight" -> VitalType.WEIGHT
            else -> return JSONObject().put("recorded", false)
        }

        if (!args.has("value")) return JSONObject().put("recorded", false)

        vitals += RecordedVital(
            type = type,
            value = args.optDouble("value").toFloat(),
            secondaryValue = if (args.has("secondaryValue")) {
                args.optDouble("secondaryValue").toFloat()
            } else {
                null
            },
        )

        return JSONObject().put("recorded", true)
    }

    private suspend fun reportUrgent(args: JSONObject): JSONObject {
        val description = args.optString("whatTheyDescribed")
        flagLastElderLine(TranscriptFlag.SAFETY_CONCERN)

        val sent = repository.reportUrgentConcern(description).isSuccess
        // Told either way. If this failed, Cara needs to know so she can stay
        // with the person and tell them to ring for help themselves, rather than
        // reassuring them that their family has been notified when they have not.
        return JSONObject().put("familyNotified", sent)
    }

    private suspend fun rememberForNextTime(args: JSONObject): JSONObject {
        val saved = repository.rememberForNextTime(
            topic = args.optString("topic"),
            why = args.optString("why"),
            followUpInDays = args.optInt("follow_up_in_days", 2),
        ).isSuccess
        return JSONObject().put("saved", saved)
    }

    private suspend fun closeThread(args: JSONObject): JSONObject {
        val closed = repository.closeOpenThread(
            topic = args.optString("topic"),
            whatHappened = args.optString("what_happened"),
        ).isSuccess
        return JSONObject().put("closed", closed)
    }

    /** Attaches a flag to the most recent thing the person said. */
    private fun flagLastElderLine(flag: TranscriptFlag) {
        val lines = _transcript.value
        val index = lines.indexOfLast { it.speaker == Speaker.ELDER }
        if (index < 0) return
        _transcript.value = lines.toMutableList().also {
            it[index] = it[index].copy(flag = flag)
        }
    }

    // =========================================================================
    // Ending
    // =========================================================================

    /**
     * Ends the call and records what happened.
     *
     * The socket closes first. Submitting before disconnecting would leave the
     * microphone live while a network round trip completes, and an open mic after
     * someone has pressed "end call" is not a detail to get wrong in a product
     * about being trusted in someone's home.
     */
    fun endCall(onFinished: () -> Unit) {
        val duration = _elapsedSeconds.value
        client?.disconnect()
        client = null

        val attemptId = CallSession.callAttemptId
        if (_mode.value != Mode.LIVE || attemptId == null) {
            onFinished()
            return
        }

        viewModelScope.launch {
            repository.submitCheckIn(
                CheckInSubmission(
                    callAttemptId = attemptId,
                    durationSeconds = duration,
                    medicationsConfirmed = confirmed.toList(),
                    medicationsMissed = missed.toList(),
                    transcript = _transcript.value,
                    confusionSignal = confusionSignal,
                    hesitationSignal = hesitationSignal,
                    toneNote = toneNote,
                    vitals = vitals.toList(),
                ),
            ).onFailure { Log.w(TAG, "Check-in submission failed") }

            repository.reportCallOutcome(attemptId, "answered", duration)
            onFinished()
        }
    }

    override fun onCleared() {
        super.onCleared()
        client?.disconnect()
    }

    // =========================================================================
    // The scripted fallback
    // =========================================================================

    /**
     * Plays the recorded conversation.
     *
     * Time-compressed against the real transcript offsets so a call of about a
     * hundred seconds demonstrates in roughly thirty-five, while keeping the
     * rhythm of the original. The pauses land where they actually landed, which
     * is what makes it read as a conversation rather than text on a timer.
     */
    private fun playScriptedCall() {
        val script = MockData.checkIns.first { it.id == "ci-2" }.transcript

        viewModelScope.launch {
            var previousOffset = 0

            script.forEach { line ->
                val gap = ((line.offsetSeconds - previousOffset) * 1000 * SCRIPT_SPEED).toLong()
                previousOffset = line.offsetSeconds

                _activity.value = if (line.speaker == Speaker.CARA) {
                    CaraActivity.SPEAKING
                } else {
                    CaraActivity.LISTENING
                }
                delay(gap.coerceAtLeast(700L))

                _transcript.value = _transcript.value + line
                _elapsedSeconds.value = line.offsetSeconds

                if (line.flag == TranscriptFlag.INTERACTION_CHECK) {
                    _activity.value = CaraActivity.CHECKING
                    delay(1800)
                    _interaction.value = MockData.warfarinIbuprofen
                }
            }

            _activity.value = CaraActivity.LISTENING
        }
    }

    companion object {
        private const val TAG = "LiveCallViewModel"
        private const val SCRIPT_SPEED = 0.35

        fun factory(repository: CareLoopRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LiveCallViewModel(repository) as T
            }
    }
}
