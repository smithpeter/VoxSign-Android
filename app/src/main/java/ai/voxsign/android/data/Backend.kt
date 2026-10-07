package ai.voxsign.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Connectivity + backend layer for the UniFusion voice channel.
 *
 * [send] performs a real HTTP POST to the configured machine's
 * `{baseUrl}/api/voice-sign/ingest` endpoint (header `X-VoiceSign-Key`, body
 * `{"text": <turn>, "channel": "VOICE"}`) and renders the messages whose
 * `speakerRole == "AGENT"` as the harness reply.
 *
 * When the selected machine has no real key configured, or the network call fails
 * (unreachable / timeout / non-2xx / unparseable body), we enter the **degraded path**:
 * the user gets an explicit "channel unavailable" notice rather than a canned fake answer.
 * We deliberately do NOT silently impersonate the harness on failure.
 */
class Backend(baseUrl: String, apiKey: String = "") {

    @Volatile private var endpointBase: String = baseUrl.trimEnd('/')
    @Volatile private var endpointKey: String = apiKey

    private val _conn = MutableStateFlow(ConnState.UNKNOWN)
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _mockMode = MutableStateFlow(false)
    /** True while we are on the degraded (local fallback) path rather than a live cloud reply. */
    val mockMode: StateFlow<Boolean> = _mockMode.asStateFlow()

    /** Point the HTTP client at a newly selected machine (base URL + voice-sign key). */
    fun useMachine(machine: Machine) {
        endpointBase = machine.baseUrl.trimEnd('/')
        endpointKey = machine.apiKey
    }

    /** Lightweight connectivity probe: report online so the send button is enabled; the
     *  authoritative success/failure signal comes from [send] itself. */
    suspend fun probe() {
        _conn.value = ConnState.UNKNOWN
        delay(300)
        _conn.value = ConnState.ONLINE
    }

    /**
     * Send a user turn to the UniFusion voice channel and return the harness reply.
     *
     * Real path: POST `{baseUrl}/api/voice-sign/ingest`, parse `messages[]`, concatenate the
     * `speakerRole == "AGENT"` entries' `content`.
     *
     * [DEGRADED PATH] — no real endpoint configured, or network/HTTP/parse failure: return a
     * clearly-labelled local notice instead of pretending the harness answered.
     */
    suspend fun send(turn: String): String = withContext(Dispatchers.IO) {
        // Machines without a real ingest key (e.g. the zero-config default cloud) have no
        // voice-channel backend to call — go straight to the degraded path without a network hop.
        if (endpointKey.isBlank() || endpointBase.isBlank()) {
            return@withContext degradedReply(turn, "no voice-channel key configured for this machine")
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL("$endpointBase/api/voice-sign/ingest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-VoiceSign-Key", endpointKey)
            }

            val payload = JSONObject()
                .put("text", turn)
                .put("channel", "VOICE")
                .toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            if (code !in 200..299) {
                return@withContext degradedReply(turn, "HTTP $code from voice channel")
            }

            val root = JSONObject(body)
            val messages = root.optJSONArray("messages")
                ?: return@withContext degradedReply(turn, "response missing messages[]")

            val agentParts = ArrayList<String>()
            for (i in 0 until messages.length()) {
                val m = messages.optJSONObject(i) ?: continue
                if (m.optString("speakerRole") == "AGENT") {
                    m.optString("content").takeIf { it.isNotBlank() }?.let { agentParts.add(it) }
                }
            }
            val reply = agentParts.joinToString("\n\n")
            if (reply.isBlank()) {
                return@withContext degradedReply(turn, "no AGENT message in response")
            }
            _mockMode.value = false
            return@withContext reply
        } catch (e: Exception) {
            return@withContext degradedReply(turn, "network error: ${e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * [DEGRADED PATH] — the real HTTP call did not produce a usable harness reply.
     * This is intentionally a *local fallback notice*, NOT a simulated answer: it tells the
     * user the voice channel is unavailable rather than masquerading as a cloud response.
     */
    private fun degradedReply(turn: String, reason: String): String {
        _mockMode.value = true
        return "⚠ Voice channel unavailable ($reason). Endpoint: $endpointBase. " +
                "Please check your connection and try again."
    }
}
