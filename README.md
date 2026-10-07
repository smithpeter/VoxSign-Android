# VoxSign-Android

The Android client for [VoxSign](https://voxsign.ai) — a voice-first, agentic assistant.
This repository is the Kotlin + Jetpack Compose port of the iOS client
([VoxSign-IOS](https://github.com/Voxsign/VoxSign-IOS)).

## Features (MVP)

- **Session list** — slide-out drawer; "New" creates a session titled *New Chat*;
  the title is auto-named from your first message (first 12 characters + `…`), mirroring iOS.
- **Chat page** — message bubble list and a large bottom **🎤 Hold to talk** button:
  press to show an animated red-waveform overlay with a semi-transparent scrim, release to
  send, slide up to cancel. Tap the keyboard icon to switch to text input.
- **Machine / cloud status** — a top-bar dot (green = connected, red = offline) and machine
  name. Tap the name to open the machine picker; the default machine is **VoxSign Cloud**.
  Sending is only allowed while the selected machine is green.
- **Local mock mode** — the backend base URL is configuration-driven
  (`BuildConfig.BACKEND_BASE_URL`). With no server reachable the app falls back to local
  mock replies so the whole UI compiles and runs offline.
- **UniFusion voice channel** — pick the **UniFusion** machine in the machine picker to talk
  straight to the UniFusion cloud: each turn is POSTed to `/api/voice-sign/ingest` (header
  `X-VoiceSign-Key`, body `{"text": "<turn>", "channel": "VOICE"}`) and the response's
  `AGENT` messages are rendered as the harness reply. On network/HTTP failure the app shows
  an explicit "channel unavailable" notice instead of a fake answer.

## Tech stack

- Kotlin 2.x + Jetpack Compose
- Gradle (AGP 8.5+), single `:app` module
- `minSdk 26`, `targetSdk`/`compileSdk 35`, JDK 17

## Build

```
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lint
```

## Project layout

```
app/
  src/main/java/ai/voxsign/android/
    data/        # models + mock/connectivity backend
    session/     # AppViewModel: sessions, auto-naming, send, machine switching
    ui/          # Compose screens (app scaffold, session drawer, chat, input bar)
  src/main/res/  # English (values) + zh (values-zh) resources
.github/workflows/ci.yml   # build + lint + secret scan + DCO
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All commits must be signed off (`git commit -s`).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
