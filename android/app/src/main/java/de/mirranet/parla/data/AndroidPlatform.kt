package de.mirranet.parla.data

import android.content.Context
import android.os.Build
import android.util.Log
import de.mirranet.parla.BuildConfig
import java.io.File

/** Android bindings for the shared data layer. */
object AndroidPlatform {

    private const val TAG = "Parla"

    fun createRepository(context: Context): Repository {
        val sp = context.getSharedPreferences("parla", Context.MODE_PRIVATE)
        val prefs = object : Prefs {
            override fun getString(key: String): String? = sp.getString(key, null)
            override fun putString(key: String, value: String?) {
                sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
            override fun getBoolean(key: String, default: Boolean) = sp.getBoolean(key, default)
            override fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
        }
        val logger = object : Logger {
            override fun warn(message: String, error: Throwable?) {
                Log.w(TAG, message, error)
            }
            override fun error(message: String, error: Throwable?) {
                Log.e(TAG, message, error)
            }
        }
        val store = LocalStore(prefs, File(context.filesDir, "parla"), BuildConfig.DEFAULT_SERVER_URL)
        return Repository(store, "${Build.MANUFACTURER} ${Build.MODEL}", logger)
    }
}
