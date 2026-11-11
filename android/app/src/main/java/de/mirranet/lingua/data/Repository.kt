package de.mirranet.lingua.data

import android.content.Context
import android.os.Build
import android.util.Log
import de.mirranet.lingua.engine.Course
import de.mirranet.lingua.engine.LearnEvent
import de.mirranet.lingua.engine.Progress
import de.mirranet.lingua.engine.ProgressLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Everything the UI shows. */
data class AppState(
    val loggedIn: Boolean,
    val username: String?,
    val serverUrl: String,
    /** Downloaded courses in the server's order. */
    val courses: List<Course>,
    /** Server progress with not-yet-uploaded events applied on top. */
    val progress: Progress,
    val settings: Codec.Settings,
    val pendingEvents: Int,
    val syncing: Boolean = false,
    val offline: Boolean = false,
    /** One-off message such as "new lessons available"; cleared by the UI. */
    val message: String? = null,
) {
    val course: Course? get() = courses.firstOrNull { it.id == settings.course }
}

/**
 * Single source of truth for the app. Content always comes from the server and is cached for offline
 * use; on every sync the app compares content hashes and downloads only courses that changed, so new
 * lessons appear without an app update. Learning results are recorded locally first and uploaded as
 * idempotent events.
 */
class Repository(context: Context) {

    private val store = LocalStore(context)
    private val api = ApiClient({ store.serverUrl }, { store.token })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private val pendingLock = Any()
    private val device = "${Build.MANUFACTURER} ${Build.MODEL}".take(100)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AppState> = _state

    private fun initialState(): AppState {
        val courses = loadCachedCourses()
        val pending = store.pending()
        return AppState(
            loggedIn = store.token != null,
            username = store.username,
            serverUrl = store.serverUrl,
            courses = courses,
            progress = fold(store.serverProgress(), pending, courses),
            settings = store.settings(),
            pendingEvents = pending.size,
        )
    }

    private fun loadCachedCourses(): List<Course> {
        val (_, list) = store.courseList() ?: return emptyList()
        return list.mapNotNull { info ->
            val text = store.courseText(info.id) ?: return@mapNotNull null
            runCatching { Codec.parseCourse(text, info.hash) }
                .onFailure { Log.w(TAG, "Cached course ${info.id} unreadable", it) }
                .getOrNull()
        }
    }

    private fun fold(server: Progress, pending: List<LearnEvent>, courses: List<Course>): Progress =
        pending.fold(server) { p, e -> ProgressLogic.apply(p, e, courses.firstOrNull { it.id == e.course }) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ account

    /** Logs in or registers, then downloads content and progress. Throws [ApiException]/[OfflineException]. */
    suspend fun authenticate(serverUrl: String, username: String, password: String, register: Boolean) {
        store.serverUrl = serverUrl
        val session = if (register) api.register(username, password, device) else api.login(username, password, device)
        // A different account on this phone must not inherit the previous one's queue.
        if (store.username != null && !store.username.equals(session.username, ignoreCase = true)) {
            store.clearAccount()
        }
        store.token = session.token
        store.username = session.username
        _state.update {
            it.copy(loggedIn = true, username = session.username, serverUrl = store.serverUrl,
                settings = store.settings(), progress = fold(store.serverProgress(), store.pending(), it.courses))
        }
        sync()
    }

    /** Uploads what is left, then signs out. Returns false if unsent progress had to be discarded. */
    suspend fun logout(): Boolean {
        sync()
        val lost = store.pending().isNotEmpty()
        runCatching { api.logout() }
        store.clearAccount()
        _state.update {
            it.copy(loggedIn = false, username = null, progress = Progress(), settings = Codec.Settings(), pendingEvents = 0)
        }
        return !lost
    }

    suspend fun changePassword(old: String, new: String) = api.changePassword(old, new)

    suspend fun deleteAccount(password: String) {
        api.deleteAccount(password)
        store.clearAccount()
        _state.update { it.copy(loggedIn = false, username = null, progress = Progress(), settings = Codec.Settings(), pendingEvents = 0) }
    }

    private fun sessionExpired() {
        store.token = null
        _state.update { it.copy(loggedIn = false, message = "Bitte melde dich erneut an.") }
    }

    // ------------------------------------------------------------------ sync

    fun syncInBackground() {
        scope.launch { sync() }
    }

    /** Content + progress + settings. Safe to call often; concurrent calls are serialised. */
    suspend fun sync() {
        if (store.token == null) return
        syncMutex.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                syncContent()
                syncProgress()
                syncSettings()
                _state.update { it.copy(offline = false) }
            } catch (e: OfflineException) {
                _state.update { it.copy(offline = true) }
            } catch (e: ApiException) {
                if (e.status == 401) sessionExpired() else Log.w(TAG, "Sync failed: ${e.code} ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    private suspend fun syncContent() {
        val cached = store.courseList()
        val filesComplete = cached != null && cached.second.all { store.courseText(it.id) != null }
        val res = api.courseList(if (filesComplete) cached?.first else null)
        val body = res.body ?: return // 304: nothing changed on the server
        val (hash, list) = Codec.parseCourseList(JSONObject(body))
        var changed = 0
        for (info in list) {
            val old = cached?.second?.firstOrNull { it.id == info.id }
            if (old != null && old.hash == info.hash && store.courseText(info.id) != null) continue
            val doc = api.course(info.id, null).body ?: continue
            Codec.parseCourse(doc, info.hash) // validate before replacing the cached copy
            store.saveCourse(info.id, doc)
            changed++
        }
        cached?.second?.filter { c -> list.none { it.id == c.id } }?.forEach { store.deleteCourse(it.id) }
        store.saveCourseList(hash, list)
        val courses = loadCachedCourses()
        _state.update {
            it.copy(
                courses = courses,
                progress = fold(store.serverProgress(), store.pending(), courses),
                message = if (changed > 0 && cached != null) "Neue Lerninhalte wurden geladen." else it.message,
            )
        }
    }

    private suspend fun syncProgress() {
        val sending = synchronized(pendingLock) { store.pending() }
        var snapshot: JSONObject? = null
        for (chunk in sending.chunked(200)) {
            val r = api.postEvents(Codec.eventsJson(chunk))
            snapshot = r.getJSONObject("progress")
            val rejected = r.optJSONArray("rejected")
            if (rejected != null && rejected.length() > 0) Log.w(TAG, "Server rejected events: $rejected")
        }
        val server = Codec.parseProgress(snapshot ?: api.progress())
        store.saveServerProgress(server)
        val sentIds = sending.map { it.id }.toSet()
        val remaining = synchronized(pendingLock) {
            store.pending().filter { it.id !in sentIds }.also { store.savePending(it) }
        }
        _state.update { it.copy(progress = fold(server, remaining, it.courses), pendingEvents = remaining.size) }
    }

    private suspend fun syncSettings() {
        val s = if (store.settingsDirty) {
            api.putSettings(store.settings()).also { store.settingsDirty = false }
        } else {
            val me = api.me()
            store.username = me.username
            me.settings
        }
        store.saveSettings(s)
        _state.update { st ->
            // Fall back to the first course if the chosen one no longer exists.
            val fixed = if (s.course != null && st.courses.none { it.id == s.course }) s.copy(course = null) else s
            st.copy(settings = fixed, username = store.username)
        }
    }

    // ------------------------------------------------------------------ learning

    private fun record(e: LearnEvent) {
        val count = synchronized(pendingLock) {
            val list = store.pending() + e
            store.savePending(list)
            list.size
        }
        _state.update { st ->
            st.copy(progress = ProgressLogic.apply(st.progress, e, st.courses.firstOrNull { it.id == e.course }), pendingEvents = count)
        }
        syncInBackground()
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun today() = LocalDate.now().toString()

    /** @return the XP awarded. */
    /** [correct]/[total] are the session's graded answers; they drive the adaptive difficulty. */
    fun completeLesson(course: String, skill: String, lesson: Int, heartsLeft: Int, correct: Int, total: Int): Int {
        val xp = ProgressLogic.lessonXp(heartsLeft)
        record(LearnEvent.Lesson(newId(), course, skill, lesson, xp, today(), System.currentTimeMillis(), correct, total))
        return xp
    }

    fun completePractice(course: String, skills: List<String>, correct: Int, total: Int): Int {
        val xp = ProgressLogic.BASE_XP
        record(LearnEvent.Practice(newId(), course, skills, xp, today(), System.currentTimeMillis(), correct, total))
        return xp
    }

    fun resetCourse(course: String) = record(LearnEvent.Reset(newId(), course, today(), System.currentTimeMillis()))

    fun updateSettings(transform: (Codec.Settings) -> Codec.Settings) {
        val s = transform(_state.value.settings)
        store.saveSettings(s)
        store.settingsDirty = true
        _state.update { it.copy(settings = s) }
        syncInBackground()
    }

    companion object {
        private const val TAG = "MirraLingua"
    }
}
