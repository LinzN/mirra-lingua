package de.mirranet.lingua.engine

/*
 * Plain Kotlin model of course content and progress. No Android dependencies, so the learning
 * logic can be unit-tested on the JVM.
 */

/** One sentence with accepted translations; index 0 is the canonical form shown to the learner. */
data class Sentence(val target: List<String>, val german: List<String>, val skillId: String)

data class Skill(
    val id: String,
    val title: String,
    val icon: String,
    val row: Int,
    val color: Long,
    /** Pairs of (target word, German word). */
    val words: List<Pair<String, String>>,
    val sentences: List<Sentence>,
    val lessonSize: Int,
) {
    val lessonCount: Int get() = (sentences.size + lessonSize - 1) / lessonSize
    fun lessonSentences(index: Int): List<Sentence> =
        sentences.subList(index * lessonSize, minOf(sentences.size, (index + 1) * lessonSize))
}

data class Course(
    val id: String,
    val name: String,
    val flag: String,
    val version: Long,
    val hash: String,
    /** BCP-47 language tag for text-to-speech, e.g. "en-GB". */
    val tts: String,
    /** Extra characters offered as buttons when typing in the target language. */
    val chars: List<String>,
    val keepCap: Set<String>,
    val skills: List<Skill>,
    /** Word hints: target word -> German, and German word -> target. */
    val gloss: Map<String, String>,
    val glossDe: Map<String, String>,
) {
    val rows: List<Int> by lazy { skills.map { it.row }.distinct().sorted() }
    fun skill(id: String): Skill? = skills.firstOrNull { it.id == id }
    val allSentences: List<Sentence> by lazy { skills.flatMap { it.sentences } }
}

data class SkillProgress(val lessons: Int = 0, val lastPracticed: Long = 0, val reps: Int = 0)

/** Learner state: course -> skill -> progress, plus XP per local day ("yyyy-MM-dd"). */
data class Progress(
    val courses: Map<String, Map<String, SkillProgress>> = emptyMap(),
    val xp: Map<String, Int> = emptyMap(),
) {
    fun skill(courseId: String, skillId: String): SkillProgress =
        courses[courseId]?.get(skillId) ?: SkillProgress()
}

/** A finished lesson/practice/reset, queued locally and uploaded to the server. */
sealed class LearnEvent {
    abstract val id: String
    abstract val course: String
    abstract val xp: Int
    abstract val day: String
    abstract val at: Long

    data class Lesson(
        override val id: String, override val course: String, val skill: String, val lesson: Int,
        override val xp: Int, override val day: String, override val at: Long,
    ) : LearnEvent()

    data class Practice(
        override val id: String, override val course: String, val skills: List<String>,
        override val xp: Int, override val day: String, override val at: Long,
    ) : LearnEvent()

    data class Reset(
        override val id: String, override val course: String,
        override val day: String, override val at: Long,
    ) : LearnEvent() {
        override val xp: Int get() = 0
    }
}
