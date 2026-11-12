package de.mirranet.parla.desktop

import de.mirranet.parla.data.LocalStore
import de.mirranet.parla.data.Logger
import de.mirranet.parla.data.Prefs
import de.mirranet.parla.data.Repository
import java.io.File
import java.net.InetAddress
import java.util.Properties

/** Build information written by Gradle into parla.properties (version, default server). */
object BuildInfo {
    private val props = Properties().apply {
        BuildInfo::class.java.getResourceAsStream("/parla.properties")?.use { load(it) }
    }
    val version: String = props.getProperty("version", "dev")
    val defaultServer: String = props.getProperty("server", "https://parla.mirranet.de")
}

/** Desktop bindings for the shared data layer. */
object DesktopPlatform {

    val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

    /** %APPDATA%\Parla on Windows, $XDG_DATA_HOME/parla (~/.local/share/parla) elsewhere. */
    val dataDir: File by lazy {
        val home = System.getProperty("user.home")
        val dir = if (isWindows) {
            File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Parla")
        } else {
            File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "parla")
        }
        dir.apply { mkdirs() }
    }

    fun createRepository(): Repository {
        val store = LocalStore(PropertiesPrefs(File(dataDir, "prefs.properties")), File(dataDir, "data"), BuildInfo.defaultServer)
        return Repository(store, deviceName(), StderrLogger)
    }

    private fun deviceName(): String {
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull() ?: "PC"
        return "$host (${System.getProperty("os.name")})"
    }
}

/** Preferences in a small properties file, saved atomically on every change. */
class PropertiesPrefs(private val file: File) : Prefs {
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    override fun getString(key: String): String? = synchronized(props) { props.getProperty(key) }

    override fun putString(key: String, value: String?) = synchronized(props) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        save()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { props.store(it, "Parla settings") }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        // The file holds the session token: keep it private to the user where the OS allows.
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }
}

object StderrLogger : Logger {
    override fun warn(message: String, error: Throwable?) {
        System.err.println("[Parla] WARN $message" + (error?.let { ": $it" } ?: ""))
    }

    override fun error(message: String, error: Throwable?) {
        System.err.println("[Parla] ERROR $message")
        error?.printStackTrace()
    }
}
