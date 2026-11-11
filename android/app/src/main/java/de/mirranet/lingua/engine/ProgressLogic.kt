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
                val (acc, answers) = blend(old.accuracy, old.answers, e.correct, e.total)
                skills[e.skill] = SkillProgress(lessons, last, reps, acc, answers)
            }
            is LearnEvent.Practice -> e.skills.forEach { id ->
                skills[id]?.let {
                    val (acc, answers) = blend(it.accuracy, it.answers, e.correct, e.total)
                    skills[id] = it.copy(lastPracticed = max(it.lastPracticed, e.at), reps = it.reps + 1, accuracy = acc, answers = answers)
                }
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

    // ------------------------------------------------------------------ adaptive difficulty

    /** Weight of the newest session in the running accuracy. Must match the server's ProgressService. */
    const val ACCURACY_WEIGHT = 0.35

    fun blend(accuracy: Double, answers: Int, correct: Int, total: Int): Pair<Double, Int> {
        if (total <= 0) return accuracy to answers
        val session = correct.toDouble() / total
        val next = if (answers == 0) session else accuracy * (1 - ACCURACY_WEIGHT) + session * ACCURACY_WEIGHT
        return next to min(1_000_000, answers + total)
    }

    /** Assumed accuracy before we know anything about the learner. */
    private const val PRIOR_ACCURACY = 0.75
    /** How many answers the prior is "worth" when mixing it with real data. */
    private const val PRIOR_WEIGHT = 10.0

    /**
     * Estimated accuracy for [skill]: its own history, backed by the learner's results in the rest of
     * the course, so a confident learner also gets harder exercises in a brand-new unit.
     */
    fun estimatedAccuracy(p: Progress, course: Course, skill: Skill): Double {
        val all = p.courses[course.id].orEmpty().values.filter { it.answers > 0 }
        val n = all.sumOf { it.answers }.toDouble()
        val courseEstimate = (all.sumOf { it.accuracy * it.answers } + PRIOR_ACCURACY * PRIOR_WEIGHT) / (n + PRIOR_WEIGHT)
        val own = p.skill(course.id, skill.id)
        return (own.accuracy * own.answers + courseEstimate * PRIOR_WEIGHT) / (own.answers + PRIOR_WEIGHT)
    }

    /**
     * Difficulty 0..1 for building exercises: 0 = lots of recognition and help, 1 = mostly free typing
     * and listening with tricky distractors. Learners above ~95 % accuracy get the hardest mix,
     * below ~65 % the gentlest.
     */
    fun difficulty(p: Progress, course: Course, skill: Skill): Double =
        ((estimatedAccuracy(p, course, skill) - 0.65) / 0.30).coerceIn(0.0, 1.0)

    fun difficultyLabel(d: Double) = when {
        d < 0.34 -> "Leicht"
        d < 0.67 -> "Mittel"
        else -> "Schwer"
    }
}
