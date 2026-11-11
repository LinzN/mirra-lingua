# MirraLingua

A language learning app in the spirit of the *early* Duolingo: a skill tree, short lessons with three hearts,
strength bars that fade and ask for review – and nothing else. No ads, no gems, no leagues.

The first courses are **English** and **French** for German speakers.

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
`reset` (clears one course). XP per event is capped at 30.

## Adding or changing content

Course files live in the server's `contentDir` (default `content/`). The server checks for changes every few
seconds; an invalid file is logged and ignored while the previous version stays online. See
[`content/README.md`](content/README.md) for the format.

## Android app

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
