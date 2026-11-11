# MirraLingua

A language learning app in the spirit of the *early* Duolingo: a skill tree, short lessons with three hearts,
strength bars that fade and ask for review – and nothing else. No ads, no gems, no leagues.

The first courses are **English** and **French** for German speakers, each with 21 units from A1 to B1
(numbers, colours, time, weather, city, questions, restaurant, free time, past and future tense, travel, work …).

```
mirra-lingua/
├── content/courses/   Course files (en.json, fr.json) – the single source of lesson content
├── server/            Java 21 backend: accounts, course delivery, progress sync (SQLite)
├── android/           Android app (Kotlin + Jetpack Compose)
├── deploy/            systemd unit and nginx example
└── Jenkinsfile        Builds the server JAR and the APK
```

## How it fits together

* **Content comes from the server.** The app ships without any lessons. On every start and whenever it returns to
  the foreground it asks the server for the course list hash; only courses whose hash changed are downloaded and
  cached for offline use. Editing a file in the server's content folder is enough – no app update, no server restart.
* **Accounts.** Username + password (PBKDF2-SHA256), random bearer tokens that are stored only as SHA-256 hashes,
  rate-limited login, password change (signs out other devices) and account deletion.
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

## Server

Requirements: Java 21, Maven.

```bash
cd server
mvn -B package
java -jar target/mirra-lingua-server.jar            # reads ./mirra-lingua.properties
```

On first start the server writes a commented `mirra-lingua.properties`, creates the SQLite database
(`data/mirra-lingua.db`) and copies the bundled courses into `content/`. Defaults: listens on `127.0.0.1:8085`,
registration open, sessions expire after 180 days without use.

Run it behind a TLS reverse proxy – see [`deploy/nginx-lingua.conf`](deploy/nginx-lingua.conf) and
[`deploy/mirra-lingua.service`](deploy/mirra-lingua.service). Back up the `data/` folder; that is all the state.

### API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | – | Version check |
| POST | `/api/auth/register` | – | `{username, password, device?}` → `{token, user}` |
| POST | `/api/auth/login` | – | `{username, password, device?}` → `{token, user}` |
| POST | `/api/auth/logout` | ✓ | Ends this session |
| POST | `/api/auth/password` | ✓ | `{oldPassword, newPassword}`; signs out other devices |
| DELETE | `/api/account` | ✓ | `{password}`; deletes the account and all progress |
| GET | `/api/me` | ✓ | User and settings |
| PUT | `/api/settings` | ✓ | `{goal?, sound?, listen?, course?}` |
| GET | `/api/courses` | – | `{hash, courses: [{id, name, flag, version, hash, skills}]}`, supports `If-None-Match` |
| GET | `/api/courses/{id}` | – | Course document, `ETag` = content hash |
| GET | `/api/progress` | ✓ | `{courses: {course: {skill: {lessons, lastPracticed, reps}}}, xp: {day: xp}}` |
| POST | `/api/progress/events` | ✓ | `{events: [...]}` → `{applied, duplicates, rejected, progress}` |

Event types: `lesson` (`skill`, `lesson`, `xp`, `day`, `at`), `practice` (`skills`, `xp`, `day`, `at`) and
`reset` (clears one course). Lessons and practice sessions may carry `correct` and `total` (graded answers,
at most 100), which update the per-skill `accuracy` (0..1) and `answers` in the progress snapshot. XP per event
is capped at 30.

## Adding or changing content

Course files live in the server's `contentDir` (default `content/`). The server checks for changes every few
seconds; an invalid file is logged and ignored while the previous version stays online. The bundled courses are
only copied into an *empty* content folder on first start – after updating the repository, copy the new
`content/courses/*.json` to the server (or let the Jenkins "Publish content" stage do it). See
[`content/README.md`](content/README.md) for the format.

## Android app

The interface follows an iOS-like look that is meant to be easy for people who rarely use a phone: grouped
lists on a light grey background, large text and 56 dp touch targets, a bottom tab bar (Lernen · Fortschritt ·
Einstellungen), one big "Lektion starten" button for the next lesson, plain-language messages, and an optional
"Große Schrift" switch that enlarges all text by 20 % on top of the system setting.

Open `android/` in Android Studio, or build from the command line (needs the Android SDK, `ANDROID_HOME` set):

```bash
cd android
./gradlew testDebugUnitTest assembleRelease
```

The default server address comes from `mirraLinguaServer` in `android/gradle.properties`
(`https://lingua.mirranet.de`) and can be changed on the login screen. Debug builds also allow plain HTTP,
e.g. `http://10.0.2.2:8085` for a server on the emulator's host.

The release build is signed with the debug key so it installs out of the box. For a real release, add your own
`signingConfig` in `android/app/build.gradle.kts`.

## Tests

* `server`: JUnit tests start a real server on a random port against a temporary database
  (`mvn verify`): accounts, rate limiting, ETags, hot reload of course files, idempotent events, settings,
  password change and account deletion.
* `android`: `EngineTest` runs the lesson engine against the real course files and checks that every generated
  exercise is solvable and that local progress rules match the server.
