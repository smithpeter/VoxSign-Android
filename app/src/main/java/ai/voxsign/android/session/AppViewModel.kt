package ai.voxsign.android.session

import ai.voxsign.android.data.Backend
import ai.voxsign.android.data.ChatSession
import ai.voxsign.android.data.ConnState
import ai.voxsign.android.data.Machine
import ai.voxsign.android.data.SessionDefaults
import ai.voxsign.android.data.StoredMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * State orchestration for the whole app. Mirrors the iOS `AppModel` + `SessionStore`:
 *  - session list sorted by updatedAt desc
 *  - "New Chat" creation + auto-title from the first user message (first 12 chars + "…")
 *  - send only allowed while the selected machine is green (online)
 */
class AppViewModel(private val backend: Backend) : ViewModel() {

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _machines = MutableStateFlow(
        listOf(SessionDefaults.DEFAULT_MACHINE, SessionDefaults.UNIFUSION_MACHINE)
    )
    val machines: StateFlow<List<Machine>> = _machines.asStateFlow()

    private val _currentMachineId = MutableStateFlow(SessionDefaults.DEFAULT_MACHINE.id)
    val currentMachineId: StateFlow<String> = _currentMachineId.asStateFlow()

    val conn: StateFlow<ConnState> = backend.conn

    /** Only allow sending when the currently selected machine reports online (green). */
    val canSend: Boolean
        get() = backend.conn.value == ConnState.ONLINE

    init {
        ensureInitialSession()
        backend.useMachine(currentMachine())
        viewModelScope.launch { backend.probe() }
    }

    // ---- sessions ----------------------------------------------------------

    private fun now() = System.currentTimeMillis()

    private fun makeSession(title: String): ChatSession {
        val ts = now()
        return ChatSession(id = UUID.randomUUID().toString(), title = title, createdAt = ts, updatedAt = ts)
    }

    fun ensureInitialSession() {
        if (_sessions.value.isNotEmpty()) return
        val s = makeSession(SessionDefaults.NEW_CHAT_TITLE)
        _sessions.value = listOf(s)
        _currentSessionId.value = s.id
    }

    fun newSession() {
        val s = makeSession(SessionDefaults.NEW_CHAT_TITLE)
        _sessions.value = _sessions.value + s
        _currentSessionId.value = s.id
    }

    fun switchSession(id: String) {
        if (_sessions.value.none { it.id == id }) return
        _currentSessionId.value = id
    }

    /** Delete a session; rejected when only one remains (mirrors iOS silently ignoring the last). */
    fun deleteSession(id: String) {
        if (_sessions.value.size <= 1) return
        val next = _sessions.value.filterNot { it.id == id }
        _sessions.value = next
        if (_currentSessionId.value == id) {
            _currentSessionId.value = next.first().id
        }
    }

    // ---- input + send ------------------------------------------------------

    fun onInputChange(text: String) { _inputText.value = text }

    fun sendText() {
        val t = _inputText.value.trim()
        if (t.isEmpty() || !canSend) return
        _inputText.value = ""
        appendUserMessage(t, fromVoice = false, voiceSeconds = null)
        submitTurn(t)
    }

    /** Voice path: the hold-to-talk button releases with a recognized transcript. */
    fun sendVoice(transcript: String, heldSeconds: Int) {
        val t = transcript.trim()
        if (t.isEmpty() || !canSend) return
        appendUserMessage(t, fromVoice = true, voiceSeconds = heldSeconds)
        submitTurn(t)
    }

    private fun appendUserMessage(text: String, fromVoice: Boolean, voiceSeconds: Int?) {
        val sid = _currentSessionId.value
        autoNameIfNeeded(sid, text)
        val msg = StoredMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = text,
            fromVoice = fromVoice,
            voiceSeconds = voiceSeconds
        )
        updateSession(sid) { it.copy(messages = it.messages + msg, updatedAt = now()) }
    }

    private fun submitTurn(turn: String) {
        val sid = _currentSessionId.value
        viewModelScope.launch {
            val reply = backend.send(turn)
            val bot = StoredMessage(id = UUID.randomUUID().toString(), role = "harness", text = reply)
            updateSession(sid) { it.copy(messages = it.messages + bot, updatedAt = now()) }
        }
    }

    /** Auto-name: if the current session still has the default title, derive a title from the
     *  first user message — first 12 chars + "…" (mirrors VSLogic.autoTitle). */
    private fun autoNameIfNeeded(sessionId: String, text: String) {
        val s = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        if (s.title != SessionDefaults.NEW_CHAT_TITLE && s.title.isNotEmpty()) return
        val name = autoTitle(text)
        updateSession(sessionId) { it.copy(title = name, updatedAt = now()) }
    }

    private fun autoTitle(from: String): String {
        val t = from.trim().replace("\n", " ")
        if (t.isEmpty()) return SessionDefaults.NEW_CHAT_TITLE
        val maxLen = 12
        return if (t.length <= maxLen) t else t.take(maxLen) + "…"
    }

    private fun updateSession(id: String, transform: (ChatSession) -> ChatSession) {
        _sessions.value = _sessions.value.map { if (it.id == id) transform(it) else it }
    }

    // ---- machine switching ------------------------------------------------

    fun selectMachine(id: String) {
        val m = _machines.value.firstOrNull { it.id == id } ?: return
        _currentMachineId.value = id
        backend.useMachine(m)
        viewModelScope.launch { backend.probe() }
    }

    fun currentMachine(): Machine =
        _machines.value.firstOrNull { it.id == _currentMachineId.value } ?: SessionDefaults.DEFAULT_MACHINE

    fun currentSession(): ChatSession? =
        _sessions.value.firstOrNull { it.id == _currentSessionId.value }
            ?: _sessions.value.firstOrNull()
}
