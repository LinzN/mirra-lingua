package de.mirranet.lingua.engine

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Progress rules. [apply] must stay in sync with the server's ProgressService so that the
 * optimistic local update matches what the server computes after upload.
 */
object ProgressLogic {

    const val HEARTS = 3
    const val BASE_XP = 10

    fun apply(p: Progress, e: LearnEvent, course: Course?): Progress {
        val skills = p.courses[e.course].orEmpty().toMutableMap()
        when (e) {
            is LearnEvent.Lesson -> {
                val total = course?.skill(e.skill)?.lessonCount ?: return p
                val old = skills[e.skill] ?: SkillProgress()
                val wasDone = old.lessons >= total
                val lessons = max(old.lessons, e.lesson + 1)
                var reps = old.reps
                var last = old.lastPracticed
                if (!wasDone && lessons >= total) reps = max(1, reps) else if (wasDone) reps++
                if (lessons >= total) last = max(last, e.at)
                skills[e.skill] = SkillProgress(lessons, last, reps)
            }
            is LearnEvent.Practice -> e.skills.forEach { id ->
                skills[id]?.let { skills[id] = it.copy(lastPracticed = max(it.lastPracticed, e.at), reps = it.reps + 1) }
            }
            is LearnEvent.Reset -> return p.copy(courses = p.courses - e.course)
        }
        val xp = if (e.xp > 0) p.xp + (e.day to (p.xp[e.day] ?: 0) + e.xp) else p.xp
        return Progress(p.courses + (e.course to skills), xp)
    }

    fun isDone(p: Progress, course: Course, skill: Skill): Boolean =
        p.skill(course.id, skill.id).lessons >= skill.lessonCount

    fun rowOpen(p: Progress, course: Course, row: Int): Boolean =
        course.skills.filter { it.row < row }.all { isDone(p, course, it) }

    /**
     * Strength 1..5 for a finished skill (0 if unfinished). It decays over an interval that doubles
     * with every review: a simple spaced-repetition scheme.
     */
    fun strength(p: Progress, course: Course, skill: Skill, now: Long = System.currentTimeMillis()): Int {
        val sp = p.skill(course.id, skill.id)
        if (sp.lessons < skill.lessonCount) return 0
        val days = (now - sp.lastPracticed) / 86_400_000.0
        val interval = 2.0.pow(min(sp.reps, 6))
        return max(1, 5 - floor(days / interval * 4).toInt())
    }

    fun streak(xp: Map<String, Int>, today: LocalDate = LocalDate.now()): Int {
        var d = if ((xp[today.toString()] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((xp[d.toString()] ?: 0) > 0) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun lessonXp(heartsLeft: Int) = BASE_XP + heartsLeft
}
