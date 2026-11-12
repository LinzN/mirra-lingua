package de.mirranet.parla.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error reported by the server (status + machine code + German message for the user). */
class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/** The server could not be reached (no network, DNS, timeout, TLS ...). [reason] says why, in German. */
class OfflineException(cause: Throwable) : Exception("Keine Verbindung zum Server.", cause) {
    val reason: String = describe(cause)

    private companion object {
        fun describe(e: Throwable): String {
            val all = generateSequence(e) { it.cause }.toList()
            val text = all.joinToString(" ") { "${it::class.java.simpleName} ${it.message.orEmpty()}" }
            return when {
                all.any { it is java.net.UnknownHostException } ->
                    "Die Serveradresse wurde nicht gefunden. Prüfe die Adresse und die Internetverbindung."
                text.contains("CLEARTEXT", ignoreCase = true) ->
                    "Unverschlüsselte Verbindungen (http://) sind auf diesem Gerät gesperrt. Nutze eine https://-Adresse."
                all.any { it is javax.net.ssl.SSLException } || text.contains("Certificate", ignoreCase = true) ->
                    "Sichere Verbindung fehlgeschlagen: Das Zertifikat des Servers wird nicht akzeptiert."
                all.any { it is java.net.SocketTimeoutException } ->
                    "Der Server antwortet nicht (Zeitüberschreitung)."
                all.any { it is java.net.ConnectException } ->
                    "Der Server ist unter dieser Adresse nicht erreichbar (Verbindung abgelehnt)."
                else -> "Keine Verbindung zum Server (${e::class.java.simpleName}: ${e.message.orEmpty()})."
            }
        }
    }
}

/** Thin client for the Parla HTTP API. All calls run on the IO dispatcher. */
class ApiClient(private val baseUrl: () -> String, private val token: () -> String?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Session(val token: String, val username: String)
    data class Me(val username: String, val settings: Codec.Settings)

    /** Result of a conditional GET: [body] is null when the server answered 304 Not Modified. */
    data class Conditional(val body: String?, val etag: String?)

    suspend fun register(username: String, password: String, device: String) =
        session(post("/api/auth/register", JSONObject().put("username", username).put("password", password).put("device", device), auth = false))

    suspend fun login(username: String, password: String, device: String) =
        session(post("/api/auth/login", JSONObject().put("username", username).put("password", password).put("device", device), auth = false))

    private fun session(o: JSONObject) = Session(o.getString("token"), o.getJSONObject("user").getString("username"))

    suspend fun logout() {
        post("/api/auth/logout", JSONObject())
    }

    suspend fun changePassword(old: String, new: String) {
        post("/api/auth/password", JSONObject().put("oldPassword", old).put("newPassword", new))
    }

    suspend fun deleteAccount(password: String) {
        request("DELETE", "/api/account", JSONObject().put("password", password), auth = true)
    }

    suspend fun me(): Me {
        val o = JSONObject(request("GET", "/api/me", null, auth = true).body!!)
        return Me(o.getJSONObject("user").getString("username"), Codec.parseSettings(o.getJSONObject("settings")))
    }

    suspend fun putSettings(s: Codec.Settings): Codec.Settings =
        Codec.parseSettings(JSONObject(request("PUT", "/api/settings", Codec.settingsJson(s), auth = true).body!!))

    suspend fun courseList(etag: String?): Conditional = request("GET", "/api/courses", null, auth = false, etag = etag)

    suspend fun course(id: String, etag: String?): Conditional = request("GET", "/api/courses/$id", null, auth = false, etag = etag)

    suspend fun progress(): JSONObject = JSONObject(request("GET", "/api/progress", null, auth = true).body!!)

    /** Uploads events and returns the server's progress snapshot plus the ids it accepted or already had. */
    suspend fun postEvents(events: JSONArray): JSONObject =
        post("/api/progress/events", JSONObject().put("events", events))

    private suspend fun post(path: String, body: JSONObject, auth: Boolean = true): JSONObject {
        val text = request("POST", path, body, auth).body
        return if (text.isNullOrBlank()) JSONObject() else JSONObject(text)
    }

    private suspend fun request(method: String, path: String, body: JSONObject?, auth: Boolean, etag: String? = null): Conditional =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(baseUrl().trimEnd('/') + path)
            val reqBody = body?.toString()?.toRequestBody(jsonType)
            when (method) {
                "GET" -> b.get()
                "POST" -> b.post(reqBody ?: "{}".toRequestBody(jsonType))
                "PUT" -> b.put(reqBody ?: "{}".toRequestBody(jsonType))
                "DELETE" -> b.delete(reqBody)
            }
            if (auth) token()?.let { b.header("Authorization", "Bearer $it") }
            if (etag != null) b.header("If-None-Match", "\"$etag\"")
            try {
                http.newCall(b.build()).execute().use { r ->
                    val text = r.body?.string()
                    when {
                        r.code == 304 -> Conditional(null, etag)
                        r.isSuccessful -> Conditional(text ?: "", r.header("ETag")?.trim('"'))
                        else -> {
                            val (code, msg) = try {
                                val o = JSONObject(text ?: "")
                                o.optString("error", "http_${r.code}") to o.optString("message", "Fehler ${r.code}")
                            } catch (e: JSONException) {
                                "http_${r.code}" to "Serverfehler (${r.code})."
                            }
                            throw ApiException(r.code, code, msg)
                        }
                    }
                }
            } catch (e: IOException) {
                throw OfflineException(e)
            }
        }
}
