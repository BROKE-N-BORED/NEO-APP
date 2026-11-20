# NEO — your own AI on your own phone

NEO is a native Android app (Kotlin + Jetpack Compose, no WebView) that
talks to a model running on the phone itself — and, when a task needs the
terminal, runs the command in the phone's real Termux terminal, with you
approving it first.

It is the phone version of the local Muse clone in
`~/workspace/local-muse-clone/` (the PC/web version). Same providers, same
plain-words errors, same rule: a missing key or an unreachable Ollama is an
honest message, never a fake reply.

## What it does

- **Chat** with streaming replies, markdown rendering (code blocks,
  headings, inline code), a Stop button, and a context meter (estimated
  tokens, labeled as an estimate).
- **Per-chat provider + model switching.** The model list for Ollama and
  llama-server is fetched live from the server, so whatever you have
  installed just shows up.
- **Local history** in Room, on the phone only. Delete a chat and it is
  gone.
- **Terminal agent.** When a task needs the terminal, the model proposes
  the exact command and NEO shows it on an approval card:
  **Allow once / Always allow / Deny**.
  - "Always allow" remembers that exact command only. A lookalike with one
    word changed asks again. The list lives in Settings and any entry can
    be taken back.
  - Dangerous commands (deleting files, sudo, installing software, running
    a downloaded script, …) always ask, even under "always allow".
  - A few commands never run at all: wiping the whole system or home
    directory, fork bombs, formatting a filesystem, raw writes to a device.
  - There is a master agent switch in Settings, and a "run safe commands
    without asking" default. Off means NEO only chats.
- **Settings**: Ollama and llama-server addresses, cloud keys (encrypted
  on the phone), default provider/model, agent switches, always-allow list.
- **Help screen**: Termux + Ollama setup with a live status check and a
  real connection test.

## Providers

| Provider | Default model | Notes |
|---|---|---|
| Ollama (local) | qwen2.5:1.5b | Default. Runs in Termux on the phone, or point it at the PC's Ollama over LAN/Tailscale. |
| llama-server | phone-model | llama.cpp server fallback on :8080. |
| Groq | qwen/qwen3.8-27b | Free tier. Key in Settings. |
| Gemini | gemini-3.8-flash | Free tier. Key in Settings. |
| EXAONE (LG) | k-exaone_v2.5 | Key in Settings. |

No xAI/Grok — Joshua does not use it. Nothing is selected by default
except Ollama-local as the default provider, because local-first is the
point of the app.

## The terminal backend

NEO runs commands through Termux's official RUN_COMMAND intent
(`com.termux.app.RunCommandService`), background mode, with stdout/stderr/
exit code returned via a PendingIntent. No root, no Termux:API app, no
fake shell inside NEO. Full research: `TERMUX-RESEARCH.md` in this folder.

Every literal in `terminal/TermuxConstants.kt` was read from Termux's own
source (termux-app master, `TermuxConstants.java`, 2026-11-18) — including
two that do not follow the naive pattern (`..._COMMAND_LABEL`, and the
result bundle key, which is just `"result"`).

Two gates, both the user's own doing, must both be passed before anything
runs:

1. The `com.termux.permission.RUN_COMMAND` permission, granted in the
   phone Settings.
2. `allow-external-apps=true` in `~/.termux/termux.properties`, set inside
   Termux. NEO cannot set it itself — Termux keeps that file untouchable
   by other apps, by design.

Termux must come from **F-Droid or GitHub**, not the Play Store — the Play
build strips the whole RUN_COMMAND API. NEO detects this and says so
plainly. Android 12+ may kill long or heavy Termux work (the phantom
process killer); short commands are fine, and a killed run is reported
plainly, not hidden.

## Setup (on the phone)

1. Install Termux from F-Droid.
2. In phone Settings → Apps → NEO → Permissions → Additional permissions,
   turn on "Run commands in Termux".
3. In Termux: `echo 'allow-external-apps=true' >> ~/.termux/termux.properties`
   then `termux-reload-settings` (or restart Termux).
4. In Termux: `pkg install ollama`, then `ollama serve`, then
   `ollama pull qwen2.5:1.5b` (or a bigger model if the phone has the RAM).
5. In NEO's Help screen, tap "Test the Termux connection".
6. Cloud keys, if wanted, go in Settings — never in code, never in a file.

## Building

Local build (the first build has not happened yet — see Verification):

```sh
export JAVA_HOME=<path to JDK 17>
export ANDROID_HOME=<path to Android SDK>
./gradlew testDebugUnitTest assembleDebug
```

Requirements: JDK 17, Android SDK with platform-tools, platform
android-34 or 35, and build-tools 34.0.0 or 35.0.0. The Gradle wrapper
downloads Gradle 8.7 on first run.

GitHub Actions (`.github/workflows/build-apk.yml`) builds the same debug
APK on every push to main and uploads it as an artifact. Note: Actions on
this account was blocked by a billing lock as of 2026-11-13 — clear the
billing lock in GitHub billing settings before expecting a run. The local
build does not depend on Actions.

## Installing the APK

After the build, the APK is at `app/build/outputs/apk/debug/app-debug.apk`
(from GitHub Actions, download the NEO-debug artifact and unzip it).
Copy it to the phone, open it, and allow install from that source when
Android asks. It is a debug build (signed with the debug key), so Android
will show the usual "unknown developer" wording. Then follow the Setup
steps above. Full step-by-step: `BUILD-AND-INSTALL.md`.

## Project layout

```
app/src/main/java/com/brokenbored/neo/
  MainActivity.kt          single activity, screen state, theme
  NeoApplication.kt        manual dependency container
  data/                    Room history, settings, encrypted keys
  providers/               Ollama, llama-server, Groq, Gemini, EXAONE
  terminal/                Termux RUN_COMMAND runner + approval rules
  ui/                      chat, conversations, settings, help, markdown
app/src/test/              JVM unit tests (approval rules, result
                           parsing, provider line parsing)
.github/workflows/         CI build for the debug APK
TERMUX-RESEARCH.md         the RUN_COMMAND research behind the backend
```

## Verification

- `./gradlew testDebugUnitTest assembleDebug`: **not yet run green.**
  Three local build attempts in the sandbox (2026-11-18) all failed
  before compiling anything: Gradle's daemon process cannot talk to
  its own client here ("Could not receive a message from the daemon",
  "Unexpected type tag 109" — the bytes on the daemon's local socket
  are not a Gradle protocol message). A sandbox localhost problem,
  not a code problem. The code has never been compiled; the first
  real build is GitHub Actions or a local PC build (see
  BUILD-AND-INSTALL.md).
- `aapt2 dump badging` on the APK: pending first build.
- `zipalign` check: pending first build.
- Wrapper files (`gradlew`, `gradle-wrapper.jar`) were fetched from
  the official Gradle repo (gradle/gradle master) because the
  `gradle wrapper` task could not run in the sandbox either;
  verified: `gradlew` passes `bash -n`, the jar contains
  `GradleWrapperMain` with the right Main-Class, and
  `gradle-wrapper.properties` points at Gradle 8.7.

### Not verified (needs the phone)

- Installing the APK on Joshua's phone.
- A real Termux install answering RUN_COMMAND on his phone.
- A real Ollama reply on the phone.
- Real cloud keys (Groq / Gemini / EXAONE).
- His Ollama model name (he mentioned one; the image never came through —
  the model is user-selectable in-app, so the build does not wait on it).
