package com.careloop.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
import com.careloop.app.BuildConfig
import com.careloop.app.data.model.CaraActivity
import com.careloop.app.data.model.Speaker
import com.careloop.app.data.model.TranscriptFlag
import com.careloop.app.data.model.TranscriptLine
import com.careloop.app.data.repository.LiveSessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The live voice session with Cara.
 *
 * Opens a WebSocket straight from the phone to the Gemini Live API using a
 * short-lived token minted by our backend, streams microphone audio up, plays
 * Cara's audio back, and services her tool calls mid-conversation.
 *
 * ## Why the connection is direct rather than relayed
 *
 * The API key never touches this device. The backend mints a single-use ephemeral
 * token, and the app connects with that. The alternative, relaying audio through a
 * Cloud Function, would double the latency on a real-time call and turn one flaky
 * connection into two.
 *
 * ## Audio format, which is not negotiable
 *
 * Input must be 16 kHz, output arrives at 24 kHz, both signed 16-bit little-endian
 * PCM. These are different rates on purpose, and using one `AudioTrack`
 * configuration for both is a classic way to end up with Cara sounding like a
 * chipmunk. Two separate configurations below.
 *
 * ## The most important behaviour here
 *
 * `check_interaction` is declared NON_BLOCKING server-side, which means Gemini
 * keeps talking while the lookup runs. This client honours that: [handleToolCall]
 * dispatches the work on a separate coroutine and posts the result back when it
 * lands, rather than blocking the socket read loop. If this were synchronous, the
 * call would go silent for a second or two mid-sentence, and to an 84-year-old a
 * silent line means the call dropped.
 */
class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onToolCall: suspend (name: String, args: JSONObject) -> JSONObject,
) {

    // ---- Observable state --------------------------------------------------

    private val _activity = MutableStateFlow(CaraActivity.LISTENING)
    val activity: StateFlow<CaraActivity> = _activity.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val transcript: StateFlow<List<TranscriptLine>> = _transcript.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /**
     * Whether the microphone has produced any sound at all.
     *
     * null until we know. False means the capture loop has been running for a
     * while and every read came back empty, which is what happens on an emulator
     * with no host audio input. Cara then hears nothing no matter how loudly
     * somebody talks, and without saying so the app looks broken rather than
     * limited.
     */
    private val _microphoneHeardSomething = MutableStateFlow<Boolean?>(null)
    val microphoneHeardSomething: StateFlow<Boolean?> = _microphoneHeardSomething.asStateFlow()

    fun setMuted(value: Boolean) {
        _muted.value = value
    }

    enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, CLOSED }

    // ---- Internals ---------------------------------------------------------

    private var webSocket: WebSocket? = null
    private var captureJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var sessionStartedAt = 0L
    private var reconnectAttempts = 0

    private val client = OkHttpClient.Builder()
        // The Live API holds the socket open with no traffic during silences, so
        // no read timeout. A timeout here would kill the call whenever the person
        // paused to think, which for this audience is constantly.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // =========================================================================
    // Session lifecycle
    // =========================================================================

    /**
     * Opens the session.
     *
     * Everything that shapes Cara's behaviour travels on [token]: her persona,
     * her tool declarations and her voice are all built server-side. Nothing
     * about who she is lives in this file.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun connect(token: LiveSessionToken) {
        _connectionState.value = ConnectionState.CONNECTING
        sessionStartedAt = System.currentTimeMillis()

        // Ephemeral tokens authenticate as an access_token query parameter. It is
        // single-use and short-lived, so a token in a URL is an acceptable
        // exposure in a way an API key never would be.
        // Path supplied by the server rather than hardcoded here. The Live API
        // is still preview: the version prefix and the method name have both
        // moved, and ephemeral tokens use a different method from raw API keys.
        // A wrong path fails at connect time, and fixing it in the app means
        // shipping a new APK, which is the wrong place for a value that moves.
        val url = "wss://${token.wsHost}${token.wsPath}?access_token=${token.token}"

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Live session open")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                sendSetup(webSocket, token)
                startCapturing(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                // The Live API sends JSON as binary frames as well as text ones.
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // The reason string is the only thing that explains a protocol
                // close. 1007 means the server rejected a frame as malformed,
                // and without the reason there is nothing to act on: the socket
                // opens, closes a second later, and the call silently falls
                // back. Gemini's reason describes the setup frame, not the
                // conversation, so there is no patient data in it.
                Log.d(TAG, "Live session closing: $code ${reason.take(300)}")
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.CLOSED
                stopCapturing()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Deliberately does not log the throwable message: a failure
                // response body from the API can echo back request content.
                Log.w(TAG, "Live session failed (code ${response?.code ?: -1})")
                stopCapturing()
                handleFailure(token)
            }
        })
    }

    /**
     * Reconnects with backoff after an unexpected drop.
     *
     * The Live API can close a session mid-call, and a session also has a hard
     * duration cap. Neither should end a medication check-in silently, so this
     * retries a bounded number of times before giving up and letting the UI say
     * something honest.
     *
     * Conversation context is lost across a reconnect. That is a real limitation,
     * and the UI marks the seam rather than pretending it did not happen.
     */
    private fun handleFailure(token: LiveSessionToken) {
        if (reconnectAttempts >= MAX_RECONNECTS) {
            _connectionState.value = ConnectionState.FAILED
            return
        }
        if (System.currentTimeMillis() > token.expiresAtEpochMillis) {
            // A fresh token is needed; the call screen requests one and retries.
            _connectionState.value = ConnectionState.FAILED
            return
        }

        reconnectAttempts += 1
        _connectionState.value = ConnectionState.RECONNECTING

        scope.launch {
            kotlinx.coroutines.delay(RECONNECT_BACKOFF_MS * reconnectAttempts)
            @SuppressLint("MissingPermission")
            if (scope.isActive) connect(token)
        }
    }

    fun disconnect() {
        stopCapturing()
        webSocket?.close(NORMAL_CLOSURE, "Call ended")
        webSocket = null
        releasePlayback()
        _connectionState.value = ConnectionState.CLOSED
    }

    val sessionDurationSeconds: Int
        // Guarded against being read before connect() sets the start time. The
        // call screen subscribes to the transcript before connecting, so the
        // first emission arrived with sessionStartedAt still zero and rendered
        // the elapsed time as the milliseconds since 1970: "29819383:50".
        get() = if (sessionStartedAt == 0L) {
            0
        } else {
            ((System.currentTimeMillis() - sessionStartedAt) / 1000).toInt()
        }

    // =========================================================================
    // Outgoing
    // =========================================================================

    /**
     * The setup frame.
     *
     * Must be the first message on the socket; the API rejects audio sent before
     * it. Declares the model, Cara's persona, her voice, and the tools she may
     * call.
     */
    private fun sendSetup(socket: WebSocket, token: LiveSessionToken) {
        // Forwarded verbatim. If the server sent nothing usable we fall back to
        // the local declarations, so a persona-less session still has hands.
        val tools = runCatching { JSONArray(token.toolsJson) }
            .getOrNull()
            ?.takeIf { it.length() > 0 }
            ?: buildToolDeclarations()

        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/${token.model}")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))

                    put("speechConfig", JSONObject().apply {
                        put("languageCode", token.languageCode)
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", token.voiceName)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", token.systemInstruction)),
                    )
                })
                put("tools", tools)
                // These belong HERE, directly under setup, and nowhere else.
                //
                // Moving them into generationConfig broke every call outright:
                //   Unknown name "inputAudioTranscription" at
                //   'setup.generation_config'
                // which also settled a question worth recording: the socket DOES
                // reject unknown setup fields. So these names are valid and are
                // being honoured, and the reason no transcription arrives is the
                // model, not the request.
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
            })
        }
        socket.send(setup.toString())
    }

    /**
     * Tool declarations.
     *
     * `check_interaction` carries `behavior: NON_BLOCKING`, which is what allows
     * Cara to keep speaking while the lookup runs. See the class comment.
     */
    private fun buildToolDeclarations(): JSONArray {
        val checkInteraction = JSONObject().apply {
            put("name", "check_interaction")
            put(
                "description",
                "Check a medicine, supplement or food the person just mentioned against " +
                    "everything they already take. Call this immediately when anything new " +
                    "comes up, and keep talking while it runs.",
            )
            put("behavior", "NON_BLOCKING")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("substance", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "What they said, as they said it.")
                    })
                    put("kind", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("drug").put("food"))
                    })
                })
                put("required", JSONArray().put("substance").put("kind"))
            })
        }

        val recordMedication = JSONObject().apply {
            put("name", "record_medication_status")
            put("description", "Record whether a specific medication was taken.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("medicationName", JSONObject().put("type", "STRING"))
                    put("taken", JSONObject().put("type", "BOOLEAN"))
                    put("uncertain", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put(
                            "description",
                            "True when they were not sure. Different from a plain no, " +
                                "and it matters more.",
                        )
                    })
                })
                put("required", JSONArray().put("medicationName").put("taken").put("uncertain"))
            })
        }

        val recordVital = JSONObject().apply {
            put("name", "record_vital")
            put("description", "Record a reading they gave. Never estimate or round.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("type", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray()
                            .put("blood_sugar").put("blood_pressure")
                            .put("heart_rate").put("weight"))
                    })
                    put("value", JSONObject().put("type", "NUMBER"))
                    put("secondaryValue", JSONObject().put("type", "NUMBER"))
                })
                put("required", JSONArray().put("type").put("value"))
            })
        }

        val urgent = JSONObject().apply {
            put("name", "report_urgent_concern")
            put(
                "description",
                "Call immediately if they describe something needing urgent medical " +
                    "attention. Tell them to call emergency services first.",
            )
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("whatTheyDescribed", JSONObject().put("type", "STRING"))
                })
                put("required", JSONArray().put("whatTheyDescribed"))
            })
        }

        return JSONArray().put(
            JSONObject().put(
                "functionDeclarations",
                JSONArray()
                    .put(checkInteraction)
                    .put(recordMedication)
                    .put(recordVital)
                    .put(urgent),
            ),
        )
    }

    // =========================================================================
    // Incoming
    // =========================================================================

    /**
     * Makes Cara speak first.
     *
     * She is the one who rang. Without this the session opens and both sides
     * wait for the other: the model has had no input, so it says nothing, and
     * the person is listening to silence on a call THEY just answered. After
     * about ten seconds the server closes the socket normally and the whole
     * thing looks broken while being, technically, fine.
     *
     * The nudge is a client turn rather than anything in the system
     * instruction, because the instruction describes who she is, not when to
     * talk. It is phrased as stage direction and never spoken aloud.
     */
    private fun openTheCall() {
        val opening = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put(
                            "text",
                            "[The call has just connected and they have picked up. " +
                                "Greet them by name and begin the check-in. Do not read " +
                                "this instruction aloud.]",
                        )
                    }))
                }))
                put("turnComplete", true)
            })
        }
        webSocket?.send(opening.toString())
    }

    private fun handleServerMessage(raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return

        // Structure only, never content.
        //
        // The transcript stayed empty through a call where Cara was audibly
        // talking, which means either the transcription frames are not arriving
        // or they are arriving under different field names. Guessing at names
        // from documentation is what produced three wrong ones already, so this
        // logs the SHAPE of what the server actually sends: top-level keys, and
        // the keys inside serverContent. No text, no audio, no values.
        if (BuildConfig.DEBUG) {
            val top = message.keys().asSequence().toList().joinToString(",")
            val inner = message.optJSONObject("serverContent")
                ?.keys()?.asSequence()?.toList()?.joinToString(",") ?: "-"
            Log.d(TAG, "frame keys=[$top] serverContent=[$inner]")
        }

        // Tool calls come first: they are time-sensitive and must not wait behind
        // audio handling.
        message.optJSONObject("toolCall")?.let { handleToolCall(it); return }

        message.optJSONObject("serverContent")?.let { content ->
            handleModelTurn(content)
            return
        }

        if (message.has("setupComplete")) {
            Log.d(TAG, "Setup acknowledged")
            _activity.value = CaraActivity.SPEAKING
            openTheCall()
        }
    }

    private fun handleModelTurn(content: JSONObject) {
        // Interruption: the person started talking over Cara. Stop playback
        // immediately, otherwise she talks over them, which is exactly the
        // behaviour that makes voice assistants feel rude.
        if (content.optBoolean("interrupted", false)) {
            flushPlayback()
            _activity.value = CaraActivity.LISTENING
            return
        }

        content.optJSONObject("outputTranscription")?.optString("text")?.let { text ->
            if (text.isNotBlank()) appendTranscript(Speaker.CARA, text)
        }
        content.optJSONObject("inputTranscription")?.optString("text")?.let { text ->
            if (text.isNotBlank()) appendTranscript(Speaker.ELDER, text)
        }

        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // A tool call can arrive EITHER as a top-level `toolCall`
                // message or inline as a part of the model's turn. This client
                // only ever looked for the first, so on a model that uses the
                // second the tools simply never fired and Cara talked about
                // checking an interaction without ever checking one.
                part.optJSONObject("functionCall")?.let { call ->
                    handleFunctionCalls(JSONArray().put(call))
                }

                part.optJSONObject("inlineData")?.let { inline ->
                    val data = inline.optString("data")
                    if (data.isNotEmpty()) {
                        _activity.value = CaraActivity.SPEAKING
                        playAudio(Base64.decode(data, Base64.DEFAULT))
                    }
                }
            }
        }

        if (content.optBoolean("turnComplete", false)) {
            turnEnded = true
            _activity.value = CaraActivity.LISTENING
        }
    }

    /**
     * Services a tool call without blocking the socket.
     *
     * Launched on its own coroutine deliberately. The read loop must stay free to
     * keep receiving Cara's audio while the lookup runs; that concurrency is the
     * entire point of the NON_BLOCKING declaration.
     */
    private fun handleToolCall(toolCall: JSONObject) {
        val calls = toolCall.optJSONArray("functionCalls") ?: return
        handleFunctionCalls(calls)
    }

    private fun handleFunctionCalls(calls: JSONArray) {

        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val name = call.optString("name")
            val id = call.optString("id")
            val args = call.optJSONObject("args") ?: JSONObject()

            if (name == "check_interaction") _activity.value = CaraActivity.CHECKING

            scope.launch {
                val result = runCatching { onToolCall(name, args) }
                    .getOrElse { JSONObject().put("error", "lookup_failed") }

                val response = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", JSONArray().put(
                            JSONObject().apply {
                                put("id", id)
                                put("name", name)
                                // `scheduling` belongs INSIDE the response object,
                                // alongside the result, not as a sibling of it.
                                // As a sibling it is an unknown field: at best
                                // ignored, which silently reverts the tool to
                                // blocking behaviour and puts a dead pause in the
                                // middle of a phone call.
                                //
                                // WHEN_IDLE rather than INTERRUPT: the result should
                                // land when Cara finishes her current sentence, not
                                // cut her off mid-word. Interrupting yourself sounds
                                // glitchy; waiting a beat sounds like thinking.
                                put(
                                    "response",
                                    result.put("scheduling", "WHEN_IDLE"),
                                )
                            },
                        ))
                    })
                }

                webSocket?.send(response.toString())
                if (_activity.value == CaraActivity.CHECKING) {
                    _activity.value = CaraActivity.SPEAKING
                }
            }
        }
    }

    /**
     * Appends to the current speaker's line rather than starting a new one.
     *
     * Transcription arrives as a stream of fragments, often a word or two at a
     * time. Treating each fragment as its own line rendered the conversation as
     * a column of single words, which is unreadable and looks broken. Fragments
     * are joined until the other party speaks or the turn ends.
     */
    private fun appendTranscript(speaker: Speaker, text: String) {
        val current = _transcript.value
        val last = current.lastOrNull()

        if (last != null && last.speaker == speaker && !turnEnded) {
            val joined = if (last.text.endsWith(" ") || text.startsWith(" ")) {
                last.text + text
            } else {
                last.text + " " + text
            }
            _transcript.value = current.dropLast(1) + last.copy(text = joined.trim())
            return
        }

        turnEnded = false
        appendNewTranscriptLine(speaker, text)
    }

    /** True once a turn completes, so the next fragment starts a fresh line. */
    private var turnEnded = true

    private fun appendNewTranscriptLine(speaker: Speaker, text: String) {
        val offset = sessionDurationSeconds
        _transcript.value = _transcript.value + TranscriptLine(
            speaker = speaker,
            text = text,
            offsetSeconds = offset,
            flag = if (_activity.value == CaraActivity.CHECKING) {
                TranscriptFlag.INTERACTION_CHECK
            } else {
                null
            },
        )
    }

    // =========================================================================
    // Microphone
    // =========================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startCapturing(socket: WebSocket) {
        if (captureJob != null) return

        captureJob = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = maxOf(minBuffer, INPUT_CHUNK_BYTES * 2)

            val recorder = AudioRecord(
                // VOICE_COMMUNICATION enables the platform's echo cancellation and
                // noise suppression. Without it, Cara's own voice from the speaker
                // is picked up by the microphone and she interrupts herself.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Microphone unavailable")
                recorder.release()
                return@launch
            }

            recorder.startRecording()
            val buffer = ByteArray(INPUT_CHUNK_BYTES)

            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    // Sound is judged by amplitude, not by the read succeeding.
                    // A silent emulator still returns full buffers of zeroes, so
                    // a successful read proves nothing about whether a
                    // microphone exists.
                    if (_microphoneHeardSomething.value != true) {
                        var peak = 0
                        var i = 0
                        while (i + 1 < read) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or
                                (buffer[i].toInt() and 0xFF)).toShort().toInt()
                            val magnitude = if (sample < 0) -sample else sample
                            if (magnitude > peak) peak = magnitude
                            i += 2
                        }
                        if (peak > 350) {
                            _microphoneHeardSomething.value = true
                        } else if (sessionDurationSeconds > 6) {
                            _microphoneHeardSomething.value = false
                        }
                    }

                    // Muting stops the audio leaving the device rather than
                    // muting it at the far end. On a health call, "muted" has to
                    // mean the words never left the room.
                    if (_muted.value) continue

                    // realtimeInput.audio, NOT realtimeInput.mediaChunks.
                    //
                    // mediaChunks is the older shape and is what this client was
                    // written against. The current Live API takes a single `audio`
                    // blob per frame. Sending the wrong one is silent: the socket
                    // stays open, Cara simply never hears anything, which is
                    // indistinguishable from a muted microphone.
                    val payload = JSONObject().apply {
                        put("realtimeInput", JSONObject().apply {
                            put("audio", JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                                put(
                                    "data",
                                    Base64.encodeToString(
                                        buffer.copyOf(read),
                                        Base64.NO_WRAP,
                                    ),
                                )
                            })
                        })
                    }
                    socket.send(payload.toString())
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    private fun stopCapturing() {
        scope.launch { captureJob?.cancelAndJoin(); captureJob = null }
    }

    // =========================================================================
    // Playback
    // =========================================================================

    /**
     * Plays a chunk of Cara's audio.
     *
     * Note the output rate differs from the input rate. Reusing one configuration
     * for both is the classic bug here, and it makes her sound wrong in a way that
     * is instantly obvious but easy to misdiagnose.
     */
    private fun playAudio(pcm: ByteArray) {
        val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
        runCatching { track.write(pcm, 0, pcm.size) }
            .onFailure { Log.w(TAG, "Audio write failed") }
    }

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // VOICE_COMMUNICATION routes through the earpiece and engages
                    // the platform's call audio path, so this sounds like a phone
                    // call rather than a video playing out loud.
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            // Roughly half a second of audio, and never less than four times
            // the platform minimum.
            //
            // The old buffer was a few chunks deep, which underran constantly:
            // the log filled with "track disabled due to previous underrun,
            // restarting" and Cara came out as stuttering syllables rather than
            // speech. Audio is written from the socket thread, so any hesitation
            // in the network or the decoder empties a small buffer immediately.
            //
            // Half a second of buffer costs half a second of extra latency at
            // the very start of a turn, which on a phone call nobody notices,
            // and buys speech that does not break up.
            .setBufferSizeInBytes(
                maxOf(minBuffer * 4, OUTPUT_SAMPLE_RATE /* bytes: 0.5s at 16-bit */),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply { play() }
    }

    /** Drops queued audio so an interruption takes effect immediately. */
    private fun flushPlayback() {
        audioTrack?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.play()
            }
        }
    }

    private fun releasePlayback() {
        audioTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
    }

    private companion object {
        const val TAG = "GeminiLive"

        /** Required by the Live API. Not adjustable. */
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000

        /** ~64ms of audio per frame: small enough to feel responsive, large enough not to flood. */
        const val INPUT_CHUNK_BYTES = 2048
        const val OUTPUT_CHUNK_BYTES = 4096

        const val NORMAL_CLOSURE = 1000
        const val MAX_RECONNECTS = 3
        const val RECONNECT_BACKOFF_MS = 1500L

        /** Warm and mid-range. Age-related hearing loss affects high frequencies first. */
        const val CARA_VOICE = "Aoede"
    }
}
