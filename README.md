# Parla

A calm, simple language learning app for Android: units grouped by level, short lessons with three hearts, and strength
bars that fade and ask for review – and nothing else. No ads, no gems, no leagues.

The first courses are **English** and **French** for German speakers, each with 21 units from A1 to B1
(numbers, colours, time, weather, city, questions, restaurant, free time, past and future tense, travel, work …).

![Parla – Lernen, Übung und Fortschritt](docs/screenshots/overview.png)

| Anmelden | Lernen | Übung |
|:---:|:---:|:---:|
| <img src="docs/screenshots/login.png" width="240" alt="Login"> | <img src="docs/screenshots/learn.png" width="240" alt="Lernen"> | <img src="docs/screenshots/lesson.png" width="240" alt="Übung mit Wortbausteinen"> |
| **Rückmeldung** | **Fortschritt** | **Einstellungen** |
| <img src="docs/screenshots/lesson-correct.png" width="240" alt="Richtige Antwort"> | <img src="docs/screenshots/progress.png" width="240" alt="Fortschritt"> | <img src="docs/screenshots/settings.png" width="240" alt="Einstellungen"> |

<sub>Design previews rendered from the app's colours, type scale and layout; screenshots from a real device may differ in small details.</sub>

```
parla/
├── android/           Android app (Kotlin + Jetpack Compose)
├── docs/screenshots/  Images for this README
└── Jenkinsfile        Builds and tests the APK
```

The backend (accounts, course files, progress sync) lives in
**[LinzN/parla-server](https://github.com/LinzN/parla-server)** – it also holds the course content and its format.

## How it fits together

* **Content comes from the server.** The app ships without any lessons. On every start and whenever it returns to
  the foreground it asks the server for the course list hash; only courses whose hash changed are downloaded and
  cached for offline use. Editing a file in the server's content folder is enough – no app update, no server restart.
* **Accounts.** Username + password; the app can change the password and delete the account.
* **Adaptive difficulty.** Every finished session reports how many answers were right. The server keeps a running
  accuracy per skill (an exponential moving average). From it the app derives a difficulty between 0 and 1. A new
  skill also takes the learner's results in the rest of the course into account. Low difficulty means more
  recognition: word banks into German, multiple choice with clearly wrong options, few chips, little review.
  High difficulty means new sentences often skip the recognition step, with more free typing and listening, chips
  with look-alike words from the same unit, confusable options and more review. Inside a session, two mistakes
  in a row make the next exercises easier, and four correct answers in a row make them harder.
* **Progress sync.** Finishing a lesson creates an event with a random id. Events are applied locally right away
  (works offline) and uploaded in the background. The server applies each id at most once, so retries are safe,
  and returns the authoritative progress snapshot.

## Android app

The interface follows an iOS-like look that is meant to be easy for people who rarely use a phone: grouped
lists on a light grey background, large text and 56 dp touch targets, a bottom tab bar (Lernen · Fortschritt ·
Einstellungen), one big "Lektion starten" button for the next lesson, plain-language messages, and an optional
"Große Schrift" switch that enlarges all text by 20 % on top of the system setting.

Open `android/` in Android Studio, or build from the command line (needs a JDK 17+, `unzip` and `curl`/`wget`):

```bash
cd android
scripts/setup-android-sdk.sh > .build-env && . ./.build-env   # JDK + Android SDK, installed on first run
./gradlew testDebugUnitTest assembleRelease
```

`setup-android-sdk.sh` reuses an existing SDK if `ANDROID_HOME` is set, accepts the SDK licences and installs
platform 35 and build tools 35.0.0. Gradle 8 cannot run on Java 25+, so if the machine's Java is outside 17–24
the script downloads a Temurin JDK 21 into `~/jdks/temurin-21` and uses it for the Android build only. Jenkins runs it automatically. The APK ends up in
`android/app/build/outputs/apk/release/Parla-<version>-release.apk`.

The default server address comes from `parlaServer` in `android/gradle.properties`
(`https://parla.mirranet.de`) and can be changed on the login screen. Debug builds also allow plain HTTP,
e.g. `http://10.0.2.2:8085` for a server on the emulator's host.

The release build is signed with the debug key so it installs out of the box. For a real release, add your own
`signingConfig` in `android/app/build.gradle.kts`.

## Tests

`EngineTest` runs the lesson engine against the real course files (test copies in
`android/app/src/test/resources/courses`, taken from parla-server) and checks that every generated exercise is
solvable, that difficulty follows accuracy and that local progress rules match the server.
