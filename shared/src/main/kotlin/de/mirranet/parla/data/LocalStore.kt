package de.mirranet.parla.data

import de.mirranet.parla.engine.LearnEvent
import de.mirranet.parla.engine.Progress
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small key-value store for settings (SharedPreferences on Android, a properties file on desktop). */
interface Prefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/**
 * On-device cache: downloaded courses, last known progress, settings and the queue of events
 * not yet uploaded. Files are written atomically (temp file + rename). Shared by Android and desktop;
 * the platform provides [prefs], the data folder [root] and the default server address.
 */
class LocalStore(private val prefs: Prefs, root: File, private val defaultServerUrl: String) {

    private val root = root.apply { mkdirs() }
    private val coursesDir = File(root, "courses").apply { mkdirs() }

    var serverUrl: String
        get() = prefs.getString("server_url") ?: defaultServerUrl
        set(v) = prefs.putString("server_url", v.trim().trimEnd('/'))

    var token: String?
        get() = prefs.getString("token")
        set(v) = prefs.putString("token", v)

    var username: String?
        get() = prefs.getString("username")
        set(v) = prefs.putString("username", v)

    /** Device-only accessibility option: 20 % larger text. */
    var largeText: Boolean
        get() = prefs.getBoolean("large_text", false)
        set(v) = prefs.putBoolean("large_text", v)

    /** Settings changed while offline and not yet sent to the server. */
    var settingsDirty: Boolean
        get() = prefs.getBoolean("settings_dirty", false)
        set(v) = prefs.putBoolean("settings_dirty", v)

    // ------------------------------------------------------------------ courses

    fun courseList(): Pair<String, List<Codec.CourseInfo>>? =
        read("courses.json")?.let { runCatching { Codec.parseCourseList(JSONObject(it)) }.getOrNull() }

    fun saveCourseList(hash: String, list: List<Codec.CourseInfo>) =
        write(File(root, "courses.json"), Codec.courseListJson(hash, list).toString())

    fun courseText(id: String): String? = File(coursesDir, "$id.json").takeIf { it.exists() }?.readText()

    fun saveCourse(id: String, text: String) = write(File(coursesDir, "$id.json"), text)

    fun deleteCourse(id: String) {
        File(coursesDir, "$id.json").delete()
    }

    // ------------------------------------------------------------------ progress + queue

    fun serverProgress(): Progress =
        read("progress.json")?.let { runCatching { Codec.parseProgress(JSONObject(it)) }.getOrNull() } ?: Progress()

    fun saveServerProgress(p: Progress) = write(File(root, "progress.json"), Codec.progressJson(p).toString())

    fun pending(): List<LearnEvent> =
        read("pending.json")?.let { runCatching { Codec.parseEvents(JSONArray(it)) }.getOrNull() }.orEmpty()

    fun savePending(list: List<LearnEvent>) = write(File(root, "pending.json"), Codec.eventsJson(list).toString())

    fun settings(): Codec.Settings =
        read("settings.json")?.let { runCatching { Codec.parseSettings(JSONObject(it)) }.getOrNull() } ?: Codec.Settings()

    fun saveSettings(s: Codec.Settings) = write(File(root, "settings.json"), Codec.settingsJson(s).toString())

    /** Removes everything tied to the account (courses stay cached – they are not personal). */
    fun clearAccount() {
        prefs.putString("token", null)
        prefs.putString("username", null)
        prefs.putBoolean("settings_dirty", false)
        listOf("progress.json", "pending.json", "settings.json").forEach { File(root, it).delete() }
    }

    private fun read(name: String): String? = File(root, name).takeIf { it.exists() }?.readText()

    @Synchronized
    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
