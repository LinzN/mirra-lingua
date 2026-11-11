package de.mirranet.lingua.data

import android.content.Context
import de.mirranet.lingua.BuildConfig
import de.mirranet.lingua.engine.LearnEvent
import de.mirranet.lingua.engine.Progress
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device cache: downloaded courses, last known progress, settings and the queue of events
 * not yet uploaded. Files are written atomically (temp file + rename).
 */
class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("mirra_lingua", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "lingua").apply { mkdirs() }
    private val coursesDir = File(root, "courses").apply { mkdirs() }

    var serverUrl: String
        get() = prefs.getString("server_url", null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(v) = prefs.edit().putString("server_url", v.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(v) = prefs.edit().putString("username", v).apply()

    /** Device-only accessibility option: 20 % larger text. */
    var largeText: Boolean
        get() = prefs.getBoolean("large_text", false)
        set(v) = prefs.edit().putBoolean("large_text", v).apply()

    /** Settings changed while offline and not yet sent to the server. */
    var settingsDirty: Boolean
        get() = prefs.getBoolean("settings_dirty", false)
        set(v) = prefs.edit().putBoolean("settings_dirty", v).apply()

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
        prefs.edit().remove("token").remove("username").remove("settings_dirty").apply()
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
