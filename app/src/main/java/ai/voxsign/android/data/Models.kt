package ai.voxsign.android.data

import ai.voxsign.android.BuildConfig

/**
 * Core value types for VoxSign-Android. Mirrors the iOS client's
 * `Core/Models.swift` (ChatSession / StoredMessage) closely enough that behaviour
 * (session auto-naming, bubble list, machine status) is aligned.
 */

/** A chat message (user bubble or harness reply). */
data class StoredMessage(
    val id: String,
    val role: String,          // "user" | "harness"
    val text: String,
    val fromVoice: Boolean = false,
    val voiceSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** A local conversation. Title starts as "New Chat" and is auto-named from the first message. */
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<StoredMessage> = emptyList()
)

/** A selectable backend machine. Default is the zero-config cloud. */
data class Machine(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isCloud: Boolean = false,
    /** Voice-channel ingest key sent as `X-VoiceSign-Key`. Empty = no real endpoint (local fallback). */
    val apiKey: String = ""
)

/** Connection state for the top-bar status dot. */
enum class ConnState {
    ONLINE,      // green
    OFFLINE,     // red
    UNKNOWN      // gray (checking)
}

object SessionDefaults {
    const val NEW_CHAT_TITLE = "New Chat"

    /** Default machine: zero-config VoxSign Cloud. Kept as the out-of-the-box default. */
    val DEFAULT_MACHINE = Machine(
        id = "cloud",
        name = "VoxSign Cloud",
        baseUrl = "https://cloud.voxsign.ai",
        isCloud = true
    )

    /**
     * UniFusion voice channel. The app POSTs the user's turn to
     * `{baseUrl}/api/voice-sign/ingest` with header `X-VoiceSign-Key: {apiKey}` and
     * renders the `AGENT` messages from the response. The key is read from
     * [BuildConfig.VOICE_SIGN_API_KEY] — a placeholder in VCS; production should supply it
     * via SecureStorage / remote config rather than baking it into the APK.
     */
    val UNIFUSION_MACHINE = Machine(
        id = "unifusion",
        name = "UniFusion",
        baseUrl = "https://unifusion.peterzou.com",
        isCloud = true,
        apiKey = BuildConfig.VOICE_SIGN_API_KEY
    )
}
