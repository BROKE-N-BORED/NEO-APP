# Termux RUN_COMMAND research for NEO (Android)

**Date:** 2026-11-17 · **Scope:** RESEARCH ONLY — how a third-party Android app (NEO)
can run a shell command in the phone's real Termux terminal and get output back.
**Verdict up front:** Yes — Termux has an official, documented mechanism for exactly
this: the `RUN_COMMAND` intent to `RunCommandService`, with stdout/stderr/exit code
returned via a `PendingIntent` (Termux ≥ 0.109). It requires the F-Droid/GitHub build
of Termux (not Play Store), a user-granted permission, and a user opt-in property
inside Termux. No root, no Termux:API app, no signature matching with Termux needed.

> Convention: **VERIFIED (primary)** = read on a Termux GitHub page/source file I
> opened. **VERIFIED (secondary)** = read on a non-Termux page I opened, or a search
> snippet. **UNVERIFIED** = could not confirm from a primary source; do not rely on it
> without checking.

---

## 1. The RUN_COMMAND intent (exact mechanism)

**VERIFIED (primary)** — source: Termux wiki, "RUN_COMMAND Intent"
<https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent> (opened 2026-11-17,
full page), plus `TermuxConstants.java`
<https://github.com/termux/termux-app/tree/master/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java>
(opened; the fetched rendering truncated before the `RUN_COMMAND_SERVICE` class, so
some literal string values below are marked UNVERIFIED — see notes).

- **Available since:** Termux `0.95` (Android 7). Result return requires `≥ 0.109`.
- **How it is sent:** NOT a broadcast. It is an **explicit service intent** sent with
  `Context.startService(intent)` to a specific component:
  - Component: `com.termux` / `com.termux.app.RunCommandService`
    (`intent.setClassName("com.termux", "com.termux.app.RunCommandService")`)
  - Action: `com.termux.RUN_COMMAND`
    (`RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND`)
  - It can also be sent from a shell with `am startservice --user 0 -n
    com.termux/com.termux.app.RunCommandService -a com.termux.RUN_COMMAND ...`,
    but results cannot be returned that way (see §2).
- **Required permission:** `com.termux.permission.RUN_COMMAND`. NEO must declare
  `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />` in its
  manifest, and the user must grant it (Settings → Apps → NEO → Permissions →
  Additional permissions → "Run commands in Termux environment"). It is a
  `dangerous`-level custom permission declared by Termux itself; the service's
  manifest entry requires it, so Android rejects the intent before it reaches Termux
  if the caller lacks it. Literal value confirmed in source:
  `PERMISSION_RUN_COMMAND = TERMUX_PACKAGE_NAME + ".permission.RUN_COMMAND"`.
- **Use the constants, not hardcoded strings:** the wiki recommends importing the
  `termux-shared` library (JitPack, `com.termux.termux-app:termux-shared:<version>`,
  published for Termux ≥ 0.116; e.g. `0.118.0`) and using
  `com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE`.
  Source: wiki "Termux Libraries" page
  <https://github.com/termux/termux-app/wiki/Termux-Libraries> (opened).
- **Package visibility (targetSdk ≥ 30 / Android ≥ 11):** NEO must add
  `<queries><package android:name="com.termux" /></queries>` (or request
  `QUERY_ALL_PACKAGES`) in its manifest, otherwise the intent **will not work**
  (`... BLOCKED` errors in logcat). Same source as above.

### Command extras (all under `RUN_COMMAND_SERVICE.*`)

| Constant | Literal key | Type | Meaning / notes | Min. version |
|---|---|---|---|---|
| `EXTRA_COMMAND_PATH` | `com.termux.RUN_COMMAND_PATH` ✓ | String | Absolute path of the executable. **Mandatory.** `$PREFIX/` and `~/` prefixes allowed. | 0.95 |
| `EXTRA_ARGUMENTS` | `com.termux.RUN_COMMAND_ARGUMENTS` ✓ | String[] | Arguments to the **executable** (not a stdin script). | 0.95 |
| `EXTRA_WORKDIR` | `com.termux.RUN_COMMAND_WORKDIR` ✓ | String | Working directory. Defaults to Termux home (`/data/data/com.termux/files/home`). `$PREFIX/`/`~/` prefixes allowed. | 0.95 |
| `EXTRA_BACKGROUND` | `com.termux.RUN_COMMAND_BACKGROUND` ✓ | boolean | `true` = run headless in background (no terminal UI); `false` = run in a foreground terminal session. Defaults `false`. | 0.95 |
| `EXTRA_SESSION_ACTION` | `com.termux.RUN_COMMAND_SESSION_ACTION` ✓ | String | What to do with the session for foreground commands. Defaults to `TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY`. Wiki examples pass `"0"`. | ≥ 0.109 |
| `EXTRA_STDIN` | `com.termux.RUN_COMMAND_STDIN` † | String | stdin for the command — can carry a whole script to feed to `bash`/`python`. | ≥ 0.109 |
| `EXTRA_PENDING_INTENT` | `com.termux.RUN_COMMAND_PENDING_INTENT` † | Parcelable (PendingIntent) | Where results are sent back (see §2). Optional; only needed if the caller wants results. | ≥ 0.109 |
| `EXTRA_COMMAND_LABEL` | † | String | Short label for the command (logging / failure popups). | ≥ 0.109 |
| `EXTRA_COMMAND_DESCRIPTION` | † | String (markdown) | Short description (logging / failure popups). | ≥ 0.109 |
| `EXTRA_COMMAND_HELP` | † | String (markdown) | Longer help text, ideally with a URL (logging / failure popups). | ≥ 0.109 |
| `EXTRA_RESULT_DIRECTORY` | † | String | Directory path in which Termux writes the execution result for the caller (file-based result delivery; see §2). | ≥ 0.115 |
| `EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | † | boolean | Replace comma-alternative chars in args with real commas. Default `false`. | ≥ 0.115 |
| `EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS` | † | String | Which chars count as comma alternatives (default U+201A `‚`). | ≥ 0.115 |
| `EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL` | † | String | Custom log level for background commands. | ≥ 0.118 |

✓ = literal string verified from the wiki's own Java/`am` examples.
† = **UNVERIFIED literal string**: the constant *name*, type and meaning are verified
from the wiki, but I could not read the literal `com.termux.RUN_COMMAND_*` string
value from `TermuxConstants.java` because the fetched file rendering truncated before
the `RUN_COMMAND_SERVICE` class. The pattern is near-certainly the same
(`com.termux.RUN_COMMAND_` + suffix), but **use the `termux-shared` constants rather
than hardcoding** — which is what the wiki recommends anyway.

**There is no "session name" extra.** The closest things are `EXTRA_COMMAND_LABEL`
(a display label, ≥ 0.109) and `EXTRA_SESSION_ACTION` (foreground session behaviour).
Session-action literal values (e.g. whether `"0"` =
`SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY`, and the values for the other three
`VALUE_EXTRA_SESSION_ACTION_*` constants) are **UNVERIFIED** — the wiki examples use
`"0"` and the default is the switch-and-open value, but the full mapping was in the
truncated part of the source file.

### Size limits (verified, wiki + Termux:Tasker README)

- Combined size of **all** intent extras must stay under Android's intent limit
  (~500 KB); arguments / full command string limited by `ARG_MAX`, ~128 KB
  (~120–125 KB usable). Exceeding it gives "Argument list too long".
- Returned stdout/stderr are truncated from the start to **100 KB combined**;
  `errmsg` truncated from the end to 25 KB. Original lengths are returned so the
  caller can detect truncation. (Without truncation, a
  `TransactionTooLargeException` would silently fail the result delivery.)

---

## 2. Getting stdout / stderr / exit code back

**YES — directly, via PendingIntent. VERIFIED (primary, wiki).**

- Send a `PendingIntent` (for a Service or BroadcastReceiver in NEO) in
  `EXTRA_PENDING_INTENT`. Termux ≥ 0.109 sends the result back to it in a Bundle
  under the key `TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE`.
  (Literal bundle-key string **UNVERIFIED** for the same truncation reason as above;
  use the constant.)
- Result bundle contents:
  - **Background** (`EXTRA_BACKGROUND=true` — the mode NEO wants):
    `EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT` = stdout,
    `EXTRA_PLUGIN_RESULT_BUNDLE_STDERR` = stderr,
    `EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE` = exit code.
  - **Foreground** (`false`): STDOUT = full session transcript (stdout+stderr
    combined, incl. prompts); STDERR = `null`; EXIT_CODE = session exit code.
    Separate stdout/stderr is only available for background commands.
  - `..._STDOUT_ORIGINAL_LENGTH` / `..._STDERR_ORIGINAL_LENGTH` = pre-truncation
    lengths (note: the wiki's example code reads these with `getString`, i.e. they
    are delivered as strings — worth a runtime check).
  - `..._ERR` (`err`) and `..._ERRMSG` (`errmsg`) = Termux-internal errors
    (command failed to start, user exited the session, Android killed Termux
    mid-run). `err` is `Activity.RESULT_OK` (-1) when no internal error occurred.
- **PendingIntent gotchas (wiki, advanced example):**
  - The `requestCode` (e.g. an incrementing execution id) **must be unique per
    execution**, or only the first result is ever delivered (Android cancels the
    PendingIntent after first use with `FLAG_ONE_SHOT`).
  - Flags: `PendingIntent.FLAG_ONE_SHOT | (SDK ≥ S ? PendingIntent.FLAG_MUTABLE : 0)`.
    `FLAG_MUTABLE` on Android 12+ is required because Termux fills in the result
    extras. (A third-party project, Aether, had a real bug here — fixed by using
    `FLAG_MUTABLE` and a BroadcastReceiver; corroborates the requirement.)
  - Results are **not** possible with `am startservice` — Java/Kotlin only.
- **No TTY on the background path (VERIFIED, secondary):** the background API does
  not connect a TTY, so anything awaiting typed input (`pkg install` without `-y`,
  `ssh` password prompt, `read`) blocks until timeout. Use non-interactive flags.
  (Source: Gotcha docs, a production app using this API —
  <https://github.com/samosa-ai-com/gotcha/blob/HEAD/docs/termux-setup.md>, read via
  search snippet, not opened in full.)

### Script file vs. single binary + args — VERIFIED

`EXTRA_COMMAND_PATH` is "absolute path of command" and may be:

1. **A binary** (e.g. `/data/data/com.termux/files/usr/bin/top`, `.../usr/bin/bash`).
2. **An executable script file** — the Termux:Tasker README (same execution engine)
   states the Executable "can either be set to a file in `~/.termux/tasker/` …
   or to an absolute path", with `$PREFIX/` and `~/` prefixes supported; a Termux
   discussion shows `bash` + a `.sh` path passed as the argument. The script must
   have read+execute permission (`chmod 700`) if outside `~/.termux/tasker/`.
3. **A script passed via stdin** — `EXTRA_STDIN` feeds a script to `bash`/`python`
   ("The `RUN_COMMAND_SERVICE.EXTRA_STDIN` can be used to pass a script or other
   data via `stdin` to the executable, like `bash` or `python`"). Note the Tasker
   README warns: when passing a script via stdin, do not also pass arguments
   (fails, at least for bash). Practical pattern for an agent: executable =
   `$PREFIX/bin/bash`, arguments = `["-c", "<command string>"]`, or executable =
   `bash` + script on stdin.

**Shebang caveat (VERIFIED, Tasker README "Termux Environment"):** Termux does
*not* fully load the environment for external/plugin commands — `LD_PRELOAD`
(libtermux-exec) is not set — so scripts invoked from inside a plugin script that
lack a correct shebang fail with `bad interpreter: No such file or directory`.
Use Termux shebangs: `#!/data/data/com.termux/files/usr/bin/bash`
(not `#!/usr/bin/bash`), or run `termux-fix-shebang` on scripts first.

### Documented workaround for large output — VERIFIED (primary)

- **`EXTRA_RESULT_DIRECTORY` (≥ 0.115):** Termux writes the execution result into a
  caller-specified directory. Exact file layout/sub-extras are documented only via
  a linked commit (`2aafcf84`) which I did not open — **details UNVERIFIED**.
- **Physical files (Tasker README, and stated to apply to RUN_COMMAND too):** "If
  you want to exchange an even larger data between tasker and termux, use physical
  files instead." I.e. a wrapper script redirecting output to a file (+ completion
  marker) is an explicitly endorsed pattern for >100 KB results. Note Termux's
  home (`/data/data/com.termux/files/home`, mode 0700) is private to Termux, so
  for NEO to read the file directly the wrapper must write somewhere NEO can
  reach (e.g. shared storage, with Termux granted Storage permission — see §3 —
  or via Termux's Storage Access Framework provider for `~/`, mentioned in the
  Tasker README). The exact mechanics of NEO reading Termux-private files are
  **UNVERIFIED** beyond the SAF mention; the PendingIntent path should be the
  primary channel, files the overflow channel.

---

## 3. Requirements on the phone

### Termux source: F-Droid or GitHub — NOT Play Store. VERIFIED (primary)

Source: termux-app README <https://github.com/termux/termux-app> (opened).
Latest stable at time of research: **v0.118.3**. Android ≥ 7 for full app+package
support.

- **F-Droid** (recommended) or **GitHub Releases** (`termux-app_v0.118.3+github-debug_*`)
  are the official sources. GitHub APKs are debuggable and signed with a public
  test key (README warns never to install GitHub builds obtained from anywhere
  except the official repo).
- **Google Play Store = "Experimental branch":** "There is currently a build of
  Termux available on Google Play for Android 11+ devices, with extensive
  adjustments in order to pass policy requirements there. This is under development
  and has missing functionality and bugs … compared to the stable F-Droid build,
  which is why most users who can should still use F-Droid or GitHub build."
  It is built from a **separate repository** (<https://github.com/termux-play-store/>),
  has `sharedUserId` removed, and issues must be reported there.
- Whether the Play build specifically lacks the RUN_COMMAND API: **VERIFIED
  (secondary, multiple independent sources)** — Gotcha's docs ("The Google Play
  build … strips the entire RUN_COMMAND API, so it can never work"), plus
  openlinuxlab and termai.sh summaries of the core team's position ("functionally
  equivalent to v0.108", "no `RUN_COMMAND` intent"). The README itself only says
  "missing functionality" without enumerating, so treat the specific claim as
  strongly corroborated but not primary-verified. **Practical consequence is the
  same: NEO must require/detect the F-Droid or GitHub build.** (Gotcha detects
  this by checking whether the RUN_COMMAND permission exists on the device.)

### Termux:API app required? NO. VERIFIED (primary, by absence + architecture)

- `RunCommandService` lives in the **termux-app** itself; the wiki's setup
  instructions list only the permission + `allow-external-apps` (+ optional items
  below). Termux:API is a separate, optional plugin (README "Termux App and
  Plugins") that exposes Android device functions to shell scripts
  (`termux-battery-status`, `termux-notification`, …). It is only needed if NEO
  wants those device APIs or `termux-job-scheduler` (§5).
- The `termux-api` CLI package inside Termux (`pkg install termux-api`) is the
  other half of that plugin — also not needed for RUN_COMMAND.

### Signing-key matching: NOT required for NEO. VERIFIED (primary)

- The signing-key rule applies **only between Termux and its plugins**: "The
  `Termux` app and all its plugins use the same `sharedUserId` `com.termux` and so
  all their APKs … must have been signed with the same signature key … Do not
  attempt to mix them together, i.e do not try to install an app or plugin from
  `F-Droid` and another one from a different source like `GitHub`." Switching
  sources requires uninstalling **all** Termux apps first (which deletes the
  Termux home directory).
- NEO is a third-party app, not a plugin: it talks to Termux through a
  permission-protected service intent and shares no UID. No signature relationship
  is required. (If the user also installs Termux:API/Tasker/Boot, those must all
  come from the same source as Termux itself.)

### `allow-external-apps` — VERIFIED (primary, exact name and value)

- Property name: **`allow-external-apps`**, value **`true`**, in
  **`~/.termux/termux.properties`** (absolute:
  `/data/data/com.termux/files/home/.termux/termux.properties`).
  Confirmed in three primary places: the RUN_COMMAND wiki ("The
  `allow-external-apps` property must be set to `true` in
  `~/.termux/termux.properties` in Termux app, regardless of if the executable
  path is inside or outside the `~/.termux/tasker/` directory"), the Tasker README
  (same, with a one-liner to set it), and source:
  `PROP_ALLOW_EXTERNAL_APPS = "allow-external-apps"`, default `"false"`.
- It is **mandatory** for RUN_COMMAND (for Termux:Tasker it is only "Optional"
  because scripts inside `~/.termux/tasker/` are exempt — that exemption does
  **not** apply to the RUN_COMMAND intent).
- Setting it (from the Tasker README; simple form also used by Gotcha):
  `echo 'allow-external-apps=true' >> ~/.termux/termux.properties`
  (the README gives a longer idempotent one-liner using grep/sed). NEO cannot
  write this file itself — it lives in Termux's private storage — so setup must
  guide the user to run the line inside Termux (or paste it).
- Whether Termux must be restarted / `termux-reload-settings` run after editing:
  **UNVERIFIED** from primary sources (third-party guides run
  `termux-reload-settings`; test on device).

### Other setup items (wiki)

- **Draw Over Apps (optional, Termux ≥ 0.100):** only matters for *foreground*
  terminal-session commands on Android ≥ 10 (background activity-start
  restriction). Background commands — NEO's mode — are unaffected. Grant at
  Settings → Apps → Termux → Advanced → Draw over other apps.
- **Storage permission (optional):** needed only if the command or workdir touches
  shared storage (`~/storage`, `/sdcard`, `/storage/emulated/0`). Android ≥ 11:
  Settings → Apps → Termux → Permissions → Files and media → "Allowed management
  of all files". (Relevant if NEO uses the file-overflow pattern in §2.)
- **Battery optimization (may be mandatory depending on device):** exempt Termux
  from battery optimization / OEM app-killers; also reduces the chance Android
  kills Termux for failing to call `startForeground()` within ~5 s of service
  start (Android ≥ 8). See dontkillmyapp.com for device-specific steps.

### Install-order caveat (VERIFIED, secondary)

Gotcha's docs report: if Termux was installed *after* the calling app, Android
may be unable to grant the custom `RUN_COMMAND` permission until the calling app
is updated or reinstalled (custom permissions are only known once the defining app
is installed). Not verified in Termux primary docs — flag as a setup edge case.

---

## 4. Android version limits

### Phantom process killer (Android 12+) — VERIFIED (primary)

Termux README (opened): "**NOTICE: Termux may be unstable on Android 12+.**
Android OS will kill any (phantom) processes greater than 32 (limit is for all
apps combined) and also kill any processes using excessive CPU. You may get
`[Process completed (signal 9) - press Enter]` message in the terminal without
actually exiting the shell process yourself." Tracked in
termux-app issue #2366. The README (older text) says an option to disable the
killing "should be available in Android 12L or 13".

Workarounds — **VERIFIED (secondary only)**, from Andronix docs and Cosyra guides
that cite Termux issue #2366 (not re-verified against a Termux primary page):

- **Android 14+:** Settings → Developer options → **"Disable child process
  restrictions"** (toggle; stays on across reboot on stock Android, but turning
  Developer options off again disables it).
- **Android 12/12L/13:** no toggle; ADB from a computer (order matters — disable
  Play Services re-sync first):
  `adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"`,
  `adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"`,
  `adb shell "settings put global settings_enable_monitor_phantom_procs false"`
  (the third only works on 12L/13).
- `termux-wake-lock` and battery-optimization exemption help against Doze/sleep
  kills but are **not** protection against the phantom killer.

Implication for NEO: short, bounded commands are low-risk; long-running or
CPU-heavy work (e.g. `ollama serve` + model load, parallel builds) is exactly the
workload the killer targets. Design for commands that can be killed mid-run
(`err`/`errmsg` will report it) and re-run; prefer short commands and/or
tmux-persisted sessions with recovery.

### Foreground vs. background caller — VERIFIED (secondary)

- **NEO in the foreground:** RUN_COMMAND works — this is the normal, documented
  path (the wiki's examples are all `startService()` from an activity).
- **NEO in the background:** Android 12+ forbids starting foreground services
  from the background except in special cases (Android Developers docs,
  "Restrictions on starting a foreground service from the background", read via
  search snippet). Gotcha (production RUN_COMMAND user) reports exactly this
  failure mode: "If Gotcha is in the background … `run_termux_command` fails with
  *Not allowed to start service*. Bring Gotcha to the foreground…" No
  Termux-primary statement on this specific interaction was found — **the
  Termux-side behaviour is UNVERIFIED**, but the Android platform rule + a
  production report agree. Design assumption: NEO should issue RUN_COMMAND while
  it is foregrounded (or holds a visible foreground service / exemption); do not
  rely on background dispatch.

### Android 13 / 14 / 15 specific breakage — NOT FOUND

No Termux-primary (or credible secondary) source found describing RUN_COMMAND
breakage specific to Android 13, 14 or 15 beyond the items above (phantom killer
12+; background-start restrictions 12+; background activity-start restriction
10+; package visibility 11+; foreground-service type rules on 14+ per Android
Developers docs, which concern services needing while-in-use permissions and are
not Termux-specific). The wiki and README make no version-specific RUN_COMMAND
exceptions for 13/14/15. **Mark as: no known version-specific breakage found;
the 12+ items above are the operative limits.**

### Other version notes (verified, primary)

- Termux full support: Android ≥ 7. Android 5/6: app only, no package updates.
- Android 10+ W^X / `exec` restrictions are a Termux-internal concern (why the
  Play build diverged); they do not change the RUN_COMMAND API surface.
- Android 8+: Termux service must call `startForeground()` within ~5 s of start
  or Android may kill it (wiki, battery-optimization section).

---

## 5. Alternatives if RUN_COMMAND is unsuitable

| Bridge | What it is | Verdict for NEO |
|---|---|---|
| **RUN_COMMAND intent** (this doc) | Direct intent to Termux; background execution; stdout/stderr/exit code back via PendingIntent. | **Best fit.** No extra app, no user-visible terminal, direct results. |
| **Termux:Tasker plugin** (plugin-host model) | NEO implements the twofortyfouram/Locale plugin-host protocol; Termux:Tasker app (v0.9.0, F-Droid/GitHub, same signing source as Termux) forwards commands to Termux and returns `%stdout`/`%stderr`/`%result`(exit)/`%err`/`%errmsg` variables. Scripts normally live in `~/.termux/tasker/` (no `allow-external-apps` needed there); stdin scripts up to ~45K chars supported (≥ 0.6.0); default 10 s timeout (per secondary summary; timeout value not re-verified in the README text I read). Same `RUN_COMMAND` permission still mandatory (≥ 0.5), same 100 KB result truncation, same ARG_MAX limits. Source: termux-tasker README <https://github.com/termux/termux-tasker> (opened, full). | Viable fallback; strictly more moving parts (extra app install + plugin protocol) for the same engine and same limits. Only advantage: the `~/.termux/tasker/` exemption avoids `allow-external-apps` for pre-placed scripts. |
| **termux-job-scheduler** (Termux:API) | `termux-job-scheduler --script <path> [--period-ms …] [--persisted true]` — schedules a script via Android JobScheduler; survives reboot; battery/network constraints. Requires Termux:API app (same signing source) + `pkg install termux-api`. Source: termux-api usage docs (secondary, search snippets) + termux-api releases (v0.53.0, 2025-09-01, seen in search results, page not opened). | Scheduling only, not on-demand execution, and **no result channel back to NEO** (results would need the file pattern). Useful as a complement (e.g. keep-alive / periodic jobs), not a replacement. |
| **termux-url-opener / termux-file-editor** | User-share-target scripts: `~/bin/termux-url-opener` is invoked with `$1` = shared URL when the user shares a URL to Termux; `~/bin/termux-file-editor` similarly for files. Source: Termux v0.31 changelog + community docs (secondary). | Not an app-to-Termux API: requires manual user sharing per invocation, no result return. Not suitable for an agent. |
| **Termux:Boot** | Runs scripts in `~/.termux/boot/` at device boot. | Boot-time only; complement for starting long-lived services (e.g. `ollama serve`), not on-demand execution. |
| **Termux:Widget** | Home-screen widget runs scripts in `~/.shortcuts/` (foreground) or `~/.shortcuts/tasks/` (background) on tap. | Manual taps only; not programmatic. |

No other documented app-to-Termux execution bridge was found. (Unofficial
alternatives seen in passing — running an SSH server inside Termux and having
NEO connect over localhost, or a local HTTP server in Termux — are **not**
Termux-documented bridges and were not evaluated here; they would sidestep
RUN_COMMAND's limits at the cost of managing a server lifecycle inside Termux.)

---

## 6. Security model

**VERIFIED (primary — wiki "Privacy" section + Tasker README + source):**

- **No, not any app can send RUN_COMMAND.** Two independent gates, both mandatory:
  1. **Android permission gate:** the caller must hold
     `com.termux.permission.RUN_COMMAND` (dangerous custom permission, user-granted
     in Settings). Android enforces it at the service boundary.
  2. **Termux opt-in gate:** `allow-external-apps=true` in the user's
     `~/.termux/termux.properties`. Default is `false`; without it Termux rejects
     the command even if the caller holds the permission.
- Termux's own warning (Tasker README): "DO NOT set it to true if you have given
  a relatively untrusted app `com.termux.permission.RUN_COMMAND` permission, since
  it will be able to run arbitrary commands in termux or even root context
  (assuming termux has been granted root permissions) in the background without
  user intervention."
- **Privacy caveat (wiki):** even with both gates, a third-party app that runs a
  command can get the session transcript / stdout / stderr back, which "could give
  the 3rd party app access to private user data. So use this wisely." No
  per-app whitelist/blacklist or per-command approval prompt exists yet (the wiki
  says one "may be implemented" in future); a third-party app could also
  exfiltrate via files/intents inside the command itself.
- `termux.properties` is deliberately protected from silent modification via
  Termux's ContentProvider (source comment in `TermuxConstants.java`): the user
  must set the property themselves; NEO cannot flip it programmatically.
- Note the asymmetry that matters for NEO's UX: granting NEO the permission +
  setting the property is effectively "NEO may run arbitrary commands as the
  Termux user, in the background, without further prompts." The setup flow should
  say so plainly.

---

## 7. Ollama in Termux — VERIFIED (primary)

**Yes, Termux's own package repo ships a native `ollama` package as of
2026-11-17.**

- Primary source, opened: `packages/ollama/build.sh` in termux/termux-packages —
  <https://github.com/termux/termux-packages/blob/master/packages/ollama/build.sh>
  - `TERMUX_PKG_VERSION="0.40.16"`, `TERMUX_PKG_HOMEPAGE=https://ollama.com/`,
    `TERMUX_PKG_LICENSE="MIT"`, maintainer `@termux`,
    `TERMUX_PKG_SRCURL=git+https://github.com/ollama/ollama`,
    `TERMUX_PKG_AUTO_UPDATE=true` (tracks upstream releases automatically),
    `TERMUX_PKG_EXCLUDED_ARCHES="arm, i686"` (64-bit only: aarch64/x86_64).
  - Builds the Go server + llama.cpp backends (Vulkan backend included;
    `ollama-backend-vulkan` subpackage exists per secondary sources).
- **Install command (inside Termux):**
  ```
  pkg update && pkg upgrade
  pkg install ollama
  ollama serve &
  ollama run gemma3:1b   # or any small model, e.g. qwen3:4b
  ```
  Server API on `http://127.0.0.1:11434`, same as desktop. Models land in Termux
  home by default (`OLLAMA_MODELS` can move them). No root needed.
- Caveats: CPU-only in practice for most phones (Vulkan GPU backend exists but
  community reports are mixed/slow); Android 12+ phantom killer can SIGKILL long
  model loads (§4); keep models under ~half the phone's RAM.
- Historical note: the native package exists since ~April 2025; older guides
  telling users to compile Ollama from source inside Termux are obsolete.

---

## 8. Recommended design for NEO (synthesis of the above)

1. **Transport:** RUN_COMMAND intent, background mode (`EXTRA_BACKGROUND=true`),
   `PendingIntent` result receiver (BroadcastReceiver or Service), unique
   execution id per call, `FLAG_ONE_SHOT | FLAG_MUTABLE` (Android 12+).
2. **Command shape:** executable `$PREFIX/bin/bash`, args `["-c", "<cmd>"]` for
   ad-hoc shell; or executable = script file / stdin script for multi-line work.
   Always non-interactive (`-y`, `--non-interactive`, `DEBIAN_FRONTEND`-style
   flags where applicable); no TTY.
3. **Results:** primary = PendingIntent bundle (stdout/stderr/exit code, 100 KB
   combined cap — check `*_ORIGINAL_LENGTH` for truncation); overflow = wrapper
   script writing to a file in shared storage (+ marker file), or
   `EXTRA_RESULT_DIRECTORY` (≥ 0.115, details to verify on device).
4. **Setup checklist (guided, in-app):** (a) Termux installed from F-Droid/GitHub
   (detect: does the `com.termux.permission.RUN_COMMAND` permission exist /
   is Termux ≥ 0.109 installed; Play build → explain and link F-Droid);
   (b) grant RUN_COMMAND permission; (c) user runs
   `echo 'allow-external-apps=true' >> ~/.termux/termux.properties` in Termux
   (provide copy-paste + probe with `echo` to confirm); (d) recommend battery
   exemption; (e) Android 12+: surface the phantom-killer fix for the user's
   version.
5. **Manifest (NEO):** `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />`
   + `<queries><package android:name="com.termux" /></queries>`; import
   `termux-shared` from JitPack and use `TermuxConstants` (do not hardcode keys).
6. **Runtime assumptions:** issue commands while NEO is foregrounded (or holds a
   foreground service); treat every call as killable (check `err`/`errmsg`);
   keep commands short; use tmux + Termux:Boot for anything long-lived
   (e.g. `ollama serve`).

## 9. Things NOT verified (do not rely on without a device test)

- Literal string values of `EXTRA_STDIN`, `EXTRA_PENDING_INTENT`,
  `EXTRA_COMMAND_LABEL/DESCRIPTION/HELP`, `EXTRA_RESULT_DIRECTORY`, the comma
  extras, `EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL`, the result-bundle keys, and the
  `VALUE_EXTRA_SESSION_ACTION_*` values — constant names/types/meanings verified,
  literals not (source fetch truncated). Mitigation: use `termux-shared`.
- `EXTRA_RESULT_DIRECTORY` file layout and semantics (only a commit link in wiki).
- Whether editing `termux.properties` needs `termux-reload-settings`/restart.
- Play-Store build lacking RUN_COMMAND (strong secondary corroboration only).
- Exact background-dispatch behaviour of RUN_COMMAND when NEO is backgrounded
  (platform rule + one production report; no Termux-primary statement).
- Android 14+ "Disable child process restrictions" toggle wording/behaviour
  (secondary sources only).
- Whether `*_ORIGINAL_LENGTH` result extras are Strings or ints at runtime
  (wiki example reads them as String).
- Termux:Tasker default 10 s timeout (secondary summary only).
- No on-device test was performed at all — everything above is documentary.

## Sources opened (primary unless noted)

1. RUN_COMMAND Intent wiki — https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
2. Termux Libraries wiki (incl. Target SDK 30 Package Visibility) — https://github.com/termux/termux-app/wiki/Termux-Libraries
3. termux-app README — https://github.com/termux/termux-app
4. TermuxConstants.java (partial — rendering truncated) — https://github.com/termux/termux-app/tree/master/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java
5. termux-tasker README — https://github.com/termux/termux-tasker
6. ollama build.sh — https://github.com/termux/termux-packages/blob/master/packages/ollama/build.sh
7. Ollama Android guide (secondary) — https://refusalrate.com/ollama-android
8. Secondary, via search snippets (not opened in full): Gotcha termux-setup.md; Andronix signal-9 docs; Cosyra Termux guides; Android Developers foreground-service restrictions; DeepWiki termux-app/termux-packages pages; termux-api-usage repo; Aether RUN_COMMAND fix commit.
