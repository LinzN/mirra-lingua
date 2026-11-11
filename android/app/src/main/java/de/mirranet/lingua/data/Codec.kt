package de.mirranet.lingua.data

import de.mirranet.lingua.engine.Course
import de.mirranet.lingua.engine.LearnEvent
import de.mirranet.lingua.engine.Progress
import de.mirranet.lingua.engine.Sentence
import de.mirranet.lingua.engine.Skill
import de.mirranet.lingua.engine.SkillProgress
import org.json.JSONArray
import org.json.JSONObject

/** JSON conversion between the server/cache format and the engine model (uses Android's org.json). */
object Codec {

    // ------------------------------------------------------------------ course content

    fun parseCourse(text: String, hash: String): Course {
        val o = JSONObject(text)
        val lessonSize = o.optInt("lessonSize", 4).coerceIn(1, 20)
        val skillsJson = o.getJSONArray("skills")
        val skills = (0 until skillsJson.length()).map { i ->
            val s = skillsJson.getJSONObject(i)
            val id = s.getString("id")
            val words = s.optJSONArray("words")?.let { arr ->
                (0 until arr.length()).map { j -> arr.getJSONArray(j).let { it.getString(0) to it.getString(1) } }
            }.orEmpty()
            val sentencesJson = s.getJSONArray("sentences")
            val sentences = (0 until sentencesJson.length()).map { j ->
                val sj = sentencesJson.getJSONObject(j)
                Sentence(strings(sj.getJSONArray("t")), strings(sj.getJSONArray("de")), id)
            }
            Skill(
                id = id,
                title = s.getString("title"),
                icon = s.optString("icon", "⭐"),
                row = s.getInt("row"),
                color = parseColor(s.optString("color", "#1cb0f6")),
                words = words,
                sentences = sentences,
                lessonSize = lessonSize,
                level = s.optString("level", "").ifEmpty { null },
            )
        }
        return Course(
            id = o.getString("id"),
            name = o.getString("name"),
            flag = o.optString("flag", ""),
            version = o.optLong("version", 1),
            hash = hash,
            tts = o.getString("tts"),
            chars = o.optJSONArray("chars")?.let { strings(it) }.orEmpty(),
            keepCap = o.optJSONArray("keepCap")?.let { strings(it) }.orEmpty().toSet(),
            skills = skills,
            gloss = stringMap(o.optJSONObject("gloss")),
            glossDe = stringMap(o.optJSONObject("glossDe")),
        )
    }

    private fun parseColor(hex: String): Long {
        val h = hex.removePrefix("#")
        return if (h.length == 6) (0xFF000000 or h.toLong(16)) else 0xFF1CB0F6
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val m = HashMap<String, String>()
        o.keys().forEach { k -> m[k.lowercase()] = o.getString(k) }
        return m
    }

    // ------------------------------------------------------------------ course list

    data class CourseInfo(val id: String, val name: String, val flag: String, val version: Long, val hash: String)

    fun parseCourseList(o: JSONObject): Pair<String, List<CourseInfo>> {
        val arr = o.getJSONArray("courses")
        val list = (0 until arr.length()).map {
            val c = arr.getJSONObject(it)
            CourseInfo(c.getString("id"), c.getString("name"), c.optString("flag"), c.optLong("version"), c.getString("hash"))
        }
        return o.getString("hash") to list
    }

    fun courseListJson(hash: String, list: List<CourseInfo>): JSONObject = JSONObject()
        .put("hash", hash)
        .put("courses", JSONArray().apply {
            list.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("flag", it.flag)
                    .put("version", it.version).put("hash", it.hash))
            }
        })

    // ------------------------------------------------------------------ progress

    fun parseProgress(o: JSONObject): Progress {
        val courses = HashMap<String, Map<String, SkillProgress>>()
        o.optJSONObject("courses")?.let { cs ->
            cs.keys().forEach { cid ->
                val skills = HashMap<String, SkillProgress>()
                val sj = cs.getJSONObject(cid)
                sj.keys().forEach { sid ->
                    val p = sj.getJSONObject(sid)
                    skills[sid] = SkillProgress(
                        p.optInt("lessons"), p.optLong("lastPracticed"), p.optInt("reps"),
                        p.optDouble("accuracy", 0.0), p.optInt("answers"),
                    )
                }
                courses[cid] = skills
            }
        }
        val xp = HashMap<String, Int>()
        o.optJSONObject("xp")?.let { x -> x.keys().forEach { xp[it] = x.getInt(it) } }
        return Progress(courses, xp)
    }

    fun progressJson(p: Progress): JSONObject {
        val courses = JSONObject()
        p.courses.forEach { (cid, skills) ->
            val sj = JSONObject()
            skills.forEach { (sid, s) ->
                sj.put(sid, JSONObject().put("lessons", s.lessons).put("lastPracticed", s.lastPracticed).put("reps", s.reps)
                    .put("accuracy", s.accuracy).put("answers", s.answers))
            }
            courses.put(cid, sj)
        }
        val xp = JSONObject()
        p.xp.forEach { (d, v) -> xp.put(d, v) }
        return JSONObject().put("courses", courses).put("xp", xp)
    }

    // ------------------------------------------------------------------ events

    fun eventJson(e: LearnEvent): JSONObject {
        val o = JSONObject().put("id", e.id).put("course", e.course).put("at", e.at)
        when (e) {
            is LearnEvent.Lesson -> o.put("type", "lesson").put("skill", e.skill).put("lesson", e.lesson)
                .put("xp", e.xp).put("day", e.day).put("correct", e.correct).put("total", e.total)
            is LearnEvent.Practice -> o.put("type", "practice").put("skills", JSONArray(e.skills))
                .put("xp", e.xp).put("day", e.day).put("correct", e.correct).put("total", e.total)
            is LearnEvent.Reset -> o.put("type", "reset").put("day", e.day)
        }
        return o
    }

    fun parseEvent(o: JSONObject): LearnEvent? {
        val id = o.getString("id")
        val course = o.getString("course")
        val at = o.optLong("at")
        val day = o.optString("day")
        return when (o.optString("type")) {
            "lesson" -> LearnEvent.Lesson(id, course, o.getString("skill"), o.getInt("lesson"), o.getInt("xp"), day, at,
                o.optInt("correct"), o.optInt("total"))
            "practice" -> LearnEvent.Practice(id, course, strings(o.getJSONArray("skills")), o.getInt("xp"), day, at,
                o.optInt("correct"), o.optInt("total"))
            "reset" -> LearnEvent.Reset(id, course, day, at)
            else -> null
        }
    }

    fun eventsJson(list: List<LearnEvent>) = JSONArray().apply { list.forEach { put(eventJson(it)) } }

    fun parseEvents(a: JSONArray): List<LearnEvent> = (0 until a.length()).mapNotNull { parseEvent(a.getJSONObject(it)) }

    // ------------------------------------------------------------------ settings

    data class Settings(val goal: Int = 20, val sound: Boolean = true, val listen: Boolean = true, val course: String? = null)

    fun parseSettings(o: JSONObject) = Settings(
        goal = o.optInt("goal", 20),
        sound = o.optBoolean("sound", true),
        listen = o.optBoolean("listen", true),
        course = if (o.isNull("course")) null else o.optString("course").ifEmpty { null },
    )

    fun settingsJson(s: Settings): JSONObject = JSONObject()
        .put("goal", s.goal).put("sound", s.sound).put("listen", s.listen)
        .put("course", s.course ?: JSONObject.NULL)
}
