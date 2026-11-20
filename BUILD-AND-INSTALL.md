# NEO v0.1 — build it and install it

This zip is the full NEO source code. It is NOT built yet — there is no
APK inside. You build the APK yourself, one of two ways:

- **A. GitHub Actions** (easiest — GitHub's machines do the building).
- **B. Your Mint PC** (needs JDK 17 + the Android SDK, one-time setup).

Then install the APK on your phone and set up Termux + Ollama.

> Tip: open the email this zip came in ON YOUR PC and copy-paste the
> commands from it. Do not hand-type the long download links.

## A. Build on GitHub Actions

1. Create a new repo on GitHub (you create repos) — call it NEO.
   Leave it empty (no README, no .gitignore — the zip has its own).
2. Unzip this file on your PC, then push it:
   ```sh
   cd neo-android
   git init
   git add .
   git commit -m "NEO v0.1"
   git branch -M main
   git remote add origin https://github.com/<your-username>/NEO.git
   git push -u origin main
   ```
   (No git on the PC? Use the repo page's "uploading an existing file"
   link and drag the unzipped files and folders in.)
3. Open the repo on GitHub → **Actions** tab. The "Build debug APK"
   run should start on its own. It takes a few minutes the first time.
4. If the run fails in seconds: your Actions is still billing-locked
   (it was locked on 2026-11-13). GitHub → Settings → Billing →
   clear it, then Actions tab → Re-run jobs.
5. When the run is green, click it → **Artifacts** → **NEO-debug** →
   download the zip → unzip it. Inside is `app-debug.apk`. That is
   the app.

## B. Build on your Mint PC

One-time setup (copy-paste from the email on your PC):

```sh
sudo apt update
sudo apt install -y openjdk-17-jdk unzip curl
mkdir -p ~/Android/Sdk/cmdline-tools
curl -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q /tmp/cmdtools.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Then build (from the unzipped folder):

```sh
cd ~/neo-android
export ANDROID_HOME=~/Android/Sdk
export ANDROID_SDK_ROOT=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew testDebugUnitTest assembleDebug
```

First build takes several minutes (it downloads Gradle 8.7 and the
libraries). The APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

If `./gradlew` says permission denied: `chmod +x gradlew`, then run it
again.

## Install the APK on your phone

1. Get `app-debug.apk` onto the phone — USB cable, or download the
   GitHub artifact zip on the phone and unzip it in the Files app.
2. Tap the APK. Android asks about installing from an unknown source —
   allow it for the Files app (or browser) and tap Install.
3. It is a debug build, so Android shows "unknown developer" wording.
   That is expected — it is your own build.

## Set up Termux + Ollama (on the phone)

1. Install **Termux from F-Droid** — NOT the Play Store. The Play
   build strips the feature NEO uses to run commands.
2. Phone Settings → Apps → NEO → Permissions → Additional permissions
   → turn on "Run commands in Termux".
3. In Termux, one line at a time:
   ```sh
   echo 'allow-external-apps=true' >> ~/.termux/termux.properties
   termux-reload-settings
   pkg install ollama
   ollama pull qwen2.5:1.5b
   ollama serve
   ```
   (`ollama serve` keeps running — leave that Termux session open, or
   run it in a second session. Pick any model you like in NEO's model
   field — the list shows what Ollama has installed.)
4. In NEO, open the Help screen and tap "Test the Termux connection".
   Green means NEO can run commands. Then just chat.

## If something breaks

- Actions run fails in seconds → billing lock (see step A4).
- `./gradlew` permission denied → `chmod +x gradlew`.
- "SDK location not found" → check the three `export` lines above,
  or create a file called `local.properties` in the project folder
  with one line: `sdk.dir=/home/<you>/Android/Sdk`.
- NEO says Termux is missing → it is the Play build, or Termux is
  not installed. F-Droid only.
- NEO says permission denied → step 2 above, plus the
  `allow-external-apps` line in step 3. Both gates must pass.

## What is NOT verified yet

The code has never been compiled — the build in my sandbox failed
three times on Gradle's own helper process before compiling anything
(a sandbox problem, not a code problem). So the first build (yours)
is also the first compile: if it hits a code error, send me the error
text and I will fix it. The unit tests have never run either.
