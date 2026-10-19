# Couchside

Your phone becomes a remote for the Fire TV app you're watching. It shows up on the phone's lock screen and in the notification shade like any music player: title, a scrubber that moves in real time, skip back or forward 15 seconds, play/pause, and a **What did they say?** button that jumps back 10 seconds and turns subtitles on for that one line. If someone joins partway through, they tap **Catch me up** on the phone. The TV sends the dialogue from the start of the film up to the current moment, and nothing after it, to an AWS Lambda function. Amazon Bedrock (Amazon Nova) writes a spoiler-free recap, and it appears on the phone in about 2–4 seconds.

Built for the Fire TV track (multi-modal UX + AI-enhanced viewing) and the AWS Builder mini challenge. The sample film is *Charade* (1963, Cary Grant and Audrey Hepburn), which is in the public domain in the US.

## Features

- **Lock-screen remote.** The phone app publishes a standard Android media session that mirrors what the TV is doing. You get the title, a live scrubber, ⟲15 / ⟳15, play/pause and a custom **What did they say?** button (speech bubble with a question mark) on the lock screen and in the notification shade, without opening the app.
- **What did they say?** The TV rewinds 10 s, turns subtitles on, shows a `↺ 10s · subtitles on` badge, and turns subtitles off again after 12 s.
- **Catch me up.** Recaps only the dialogue up to the current playback position, so it can't spoil anything. The recap gets longer the further into the film you are: about 40 words at the start, rising to 150 at the end, based on how much of the film has been watched (position ÷ duration). 150 words is what fits on one phone screen. The server trims the text at the last complete sentence.
- **Companion app shell.** The phone app opens to a Fire TV-style home screen with a now-playing banner and a ⚙ setting for the TV's address. Tapping the banner opens a Now Playing screen with large controls and a **Catch me up** button. Catch me up switches to a lyrics-style view: the recap sits on a grey rounded tile (Figtree bold, bundled under the OFL) with the scrubber and transport controls kept at the bottom. Swipe back or tap the round ⌄ button to return. The other home-screen rows (Continue watching, Your apps, …) and the bottom navigation bar are visual placeholders.
- **Fire TV branding.** These assets were supplied for the demo:
  - TV app: the Fire TV icon, and a launcher banner with the Fire TV logo on black.
  - In the player: the logo appears top-left for 5 s at launch and whenever the player controls are showing. The TV badges carry a small Fire TV icon.
  - Phone app: labelled "Fire TV", with the logo in its header and a Fire TV launcher icon.
- **No AWS credentials on either device.** The TV calls a Lambda Function URL, and only the Lambda function talks to Bedrock.

## How it works

```
 ┌──────────────── Phone (Android) ───────────────┐          ┌──────────── Fire TV (Fire OS) ────────────┐
 │ Lock screen / shade media card                 │          │ MainActivity                              │
 │ Home + Now Playing screens (MediaController)   │          │  Media3 ExoPlayer + sideloaded SRT        │
 │        │                                       │   TCP    │  ControlServer :8765                      │
 │ RemoteService (MediaSessionService)            │◄────────►│  Srt.transcriptUpTo(position)             │
 │  TvPlayer (SimpleBasePlayer, mirrors TV state) │  NDJSON  │  Recap.fetch() ── HTTPS POST ──┐          │
 │  TvLink (socket client, auto-reconnect)        │          └────────────────────────────────┼──────────┘
 └────────────────────────────────────────────────┘                                           ▼
                                                     ┌──────────── AWS (us-east-2) ─────────────────────┐
                                                     │ Lambda Function URL ─► couch-recap (Python 3.13) │
                                                     │   └─► Bedrock Converse: Nova 2 Lite → Nova Micro │
                                                     │ CloudWatch Logs (model used, word count)         │
                                                     └──────────────────────────────────────────────────┘
```

### Phone ⇄ TV protocol

The phone connects to the TV on TCP port 8765. Each message is one JSON object per line.

| Direction | Message |
|---|---|
| TV → phone, every 500 ms | `{"type":"state","title":"Charade","pos":1080000,"dur":7010453,"playing":true}` |
| TV → phone, reply to `catchUp` | `{"type":"recap","text":"…"}` or `{"type":"recap","error":"…"}` |
| phone → TV | `{"cmd":"play"}` `{"cmd":"pause"}` `{"cmd":"toggle"}` `{"cmd":"back"}` `{"cmd":"fwd"}` `{"cmd":"whatSay"}` `{"cmd":"catchUp"}` |
| phone → TV | `{"cmd":"seek","ms":60000}` |

Between state messages, the phone moves the scrubber forward on its own clock so it doesn't jump every 500 ms.

### TV → recap service

`POST <Function URL>` with `{"title","position":"m:ss","duration":"m:ss","transcript":"[7:19] don't tell me you didn't know it was\n…"}` → `{"recap":"…"}`, or `{"error":"…"}` with status 400/401/502. See [recap/README.md](recap/README.md).

## Repo layout

```
settings.gradle.kts, build.gradle.kts   Gradle 9.8.1 wrapper, AGP 9.4.1 (built-in Kotlin)
tv/      Fire TV app "Couchside" (minSdk 28 = Fire OS 7, targetSdk 34)
  src/main/java/.../tv/MainActivity.kt   player (starts at 3:43), subtitles, commands, logo + badges
  src/main/java/.../tv/ControlServer.kt  TCP JSON server on :8765
  src/main/java/.../tv/Srt.kt            SRT parser + spoiler-safe transcript slice
  src/main/java/.../tv/Recap.kt          HTTPS call to the recap Lambda
  src/main/assets/charade-en.srt         subtitles bundled into the APK (generated, see below)
  src/main/res/                          Fire TV icon, logo, launcher banner
  src/test/.../SrtTest.kt                unit tests
phone/   Android companion app (minSdk 28, targetSdk 36)
  RemoteService.kt  MediaSessionService + lock-screen buttons
  TvPlayer.kt       SimpleBasePlayer that mirrors the TV
  TvLink.kt         socket client
  MainActivity.kt, NowPlayingActivity.kt, Ui.kt   companion UI
  res/              Fire TV logo, adaptive launcher icon
recap/   AWS Lambda: handler.py, test_handler.py, deploy.ps1
media/   Charade_1963.en.vtt (archive.org captions), vtt_to_srt.py (VTT → charade-en.srt)
```

## Prerequisites

- Android Studio, or another JDK 17+. On Windows the bundled JDK is at `C:\Program Files\Android\Android Studio\jbr`.
- An Android SDK with platform 37 installed. Android Studio writes `local.properties` (`sdk.dir=...`) when you open the project; otherwise set `ANDROID_HOME`.
- `adb` on your PATH (it's in the SDK's `platform-tools`).
- For the recap backend: AWS CLI v2 on your PATH, an AWS account with Amazon Nova enabled in Bedrock (us-east-2), and Python 3.13 if you want to run the tests.
- Hardware, either:
  - **Emulators:** an Android TV emulator (API 30 x86 stands in for Fire OS 8, which is based on Android 11) and a phone emulator. Amazon has no Fire OS simulator (the official Vega Virtual Device runs Vega OS apps only), so this follows the same Fire OS workflow as Amazon's [react-native-multi-tv-app-sample](https://github.com/AmazonAppDev/react-native-multi-tv-app-sample), whose "Android TV and Fire TV FOS" target is built and run with the Android SDK.
  - **Real devices:** a Fire TV with ADB debugging enabled and an Android phone on the same Wi-Fi network.

Commands below are for Windows PowerShell. On macOS/Linux, use `./gradlew` in place of `.\gradlew.bat` and `export VAR=...` in place of `$env:VAR = ...`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

## Run it

### 1. Deploy the recap backend (optional; everything except Catch me up works without it)

```powershell
aws login
powershell -File recap\deploy.ps1
# prints: Function URL: https://xxxx.lambda-url.us-east-2.on.aws/
```

The script creates or updates the following (it is safe to run more than once):
- IAM role `couch-recap-role` with `AWSLambdaBasicExecutionRole` and `bedrock:InvokeModel*`
- Lambda function `couch-recap` (python3.13, 30 s timeout, 256 MB)
- a public Function URL (`AuthType NONE`)

### 2a. Two emulators on one PC

```powershell
# Start the Android TV emulator (emulator-5554) and the phone emulator (emulator-5556) from Android Studio's Device Manager.

.\gradlew.bat :tv:assembleDebug        # reads RECAP_URL / RECAP_TOKEN from .env (written by recap\deploy.ps1)
.\gradlew.bat :phone:assembleDebug     # real phone + Fire TV: add -PtvHost=<fire-tv-ip>

adb -s emulator-5554 install -r tv\build\outputs\apk\debug\tv-debug.apk
adb -s emulator-5556 install -r phone\build\outputs\apk\debug\phone-debug.apk

# Forward the PC's port 8765 to the TV emulator; the phone emulator reaches the PC at 10.0.2.2
adb -s emulator-5554 forward tcp:8765 tcp:8765
```

1. On the TV emulator, open **Fire TV** from the Apps row. Pick *Charade* on the movie list, then **Start over** (or **Resume**). *Charade* starts at 3:43, which skips the presenter's intro at the start of the archive.org copy.
2. On the phone emulator, open the companion app. It connects to `10.0.2.2` by default, so no setup is needed.
3. Pull down the notification shade or lock the phone to see the media card. Open the Now Playing screen to try **Catch me up**.

### 2b. Real Fire TV + Android phone

```powershell
# On the Fire TV: Settings → My Fire TV → Developer options → ADB debugging ON.
# Find its IP under Settings → My Fire TV → About → Network.
adb connect <fire-tv-ip>:5555
adb -s <fire-tv-ip>:5555 install -r tv\build\outputs\apk\debug\tv-debug.apk
adb -s <phone-serial> install -r phone\build\outputs\apk\debug\phone-debug.apk   # or copy the APK to the phone
```

1. Launch **Couchside** on the Fire TV.
2. On the phone, tap ⚙ on the home screen, enter the Fire TV's IP address and tap **Connect**. Both devices must be on the same Wi-Fi network, and port 8765 must be reachable between them. No `adb forward` is needed.

### Using a local copy of the video

By default the TV streams `https://archive.org/download/batvwi-Charade_1963/Charade_1963.mp4` (677 MB). To play it from your PC instead, download that file into `media/` and run:

```powershell
cd media; python -m http.server 8000
.\gradlew.bat :tv:assembleDebug -PvideoUrl=http://10.0.2.2:8000/Charade_1963.mp4
```

For a real Fire TV, use `http://<pc-ip>:8000/...`. The TV app allows plain HTTP (cleartext) traffic. The app always starts at 3:43 (`startMs` for *Charade* in `tv/.../Catalog.kt`), and the subtitle timings assume the archive.org copy, so a different copy of the film needs both adjusted.

### Regenerating the subtitles

`charade-en.srt` is generated from archive.org's caption file. The converter removes the repeated lines that rolling auto-captions produce, strips the word-timing tags, and drops everything before 223 s (the intro):

```powershell
cd media; python vtt_to_srt.py Charade_1963.en.vtt ..\tv\src\main\assets\charade-en.srt 223
```

## Tests

```powershell
.\gradlew.bat :tv:testDebugUnitTest      # 4 tests: SRT parsing, transcript stops at the playback position
cd recap; python -m unittest             # 9 tests incl. recap length vs. share watched; Bedrock is faked, no AWS needed
```

## Configuration

| Knob | Where | Default |
|---|---|---|
| `videoUrl` | `-PvideoUrl=` Gradle property (`tv`) | *Charade* (1963) on archive.org |
| Recap URL + token | `.env` in the repo root (`RECAP_URL`, `RECAP_TOKEN`; git-ignored, see `.env.example`), written by `deploy.ps1`; `-PrecapUrl=` / `-PrecapToken=` override | no `.env` → Catch me up replies "Recap service isn't set up yet." |
| Model order | `deploy.ps1 -ModelId "<id1>,<id2>"` → Lambda env `MODEL_ID` | `us.amazon.nova-2-lite-v1:0,us.amazon.nova-micro-v1:0` |
| Region | `deploy.ps1 -Region` | `us-east-2`. Nova there needs the `us.*` cross-region inference profile IDs |
| Shared secret | `deploy.ps1` generates `RECAP_TOKEN` once (kept in `.env`), sets it on the Lambda, and the TV sends it as `x-recap-token`; requests without it get 401 | on. `deploy.ps1 -NoToken` turns it off |
| TV address | `-PtvHost=` Gradle property (`phone`) | `10.0.2.2` (emulator host); the ⚙ panel only shows "Home TV · Connected" |

## Known limitations

- **What did they say?** only works with our own player, because it needs control of the subtitle track. With other apps (Netflix etc.), only rewind, scrub and play/pause could be done, through Android's media session APIs. That would need a `NotificationListenerService` + `MediaSessionManager`, with the permission granted via adb on the Fire TV. Future work.
- The recap only sees subtitle dialogue. The *Charade* captions are lowercase auto-captions with no speaker names, so the recap can sometimes attribute a line to the wrong character or repeat a misheard word.
- The recap token is compiled into the TV APK, so anyone with the APK could extract it. Fine for a demo; production would use per-device credentials (e.g. Cognito) instead of a shared secret.
- The phone ⇄ TV link is plain TCP on the local network, with no pairing or encryption.
- Development and the demo use the Android TV emulator; Fire OS has no official simulator. The app uses only standard Android APIs, but it hasn't yet been run on a physical Fire TV.

## Credits

- *Charade* (Universal Pictures, 1963) is in the public domain in the US because it was published without a valid copyright notice. The video and captions are from the archive.org item [batvwi-Charade_1963](https://archive.org/details/batvwi-Charade_1963).
- The Fire TV branding assets were supplied for the demo.
- Figtree font © 2022 The Figtree Project Authors, SIL Open Font License 1.1 (`phone/FIGTREE-OFL.txt`).
- [AndroidX Media3](https://developer.android.com/media/media3) (ExoPlayer, UI, Session), Apache 2.0.
- AWS: Amazon Bedrock (Amazon Nova), AWS Lambda, IAM, CloudWatch Logs.
- The Gradle wrapper was taken from [AmazonAppDev/hello-world-fire-tv](https://github.com/AmazonAppDev/hello-world-fire-tv).
