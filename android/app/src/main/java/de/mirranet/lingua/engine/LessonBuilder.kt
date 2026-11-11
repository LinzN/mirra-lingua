package de.mirranet.lingua.engine

import kotlin.math.ceil
import kotlin.random.Random

/** One step of a lesson. "Target" is the language being learned, "German" the learner's language. */
sealed class Exercise {
    /** The answer shown when the learner gets it wrong. */
    abstract val solution: String

    /** Assemble a translation from word chips. [toGerman] = prompt is in the target language. */
    data class Bank(val sentence: Sentence, val toGerman: Boolean, val chips: List<String>) : Exercise() {
        val prompt get() = if (toGerman) sentence.target[0] else sentence.german[0]
        val accepted get() = if (toGerman) sentence.german else sentence.target
        override val solution get() = accepted[0]
    }

    /** Type a translation. */
    data class Type(val sentence: Sentence, val toGerman: Boolean) : Exercise() {
        val prompt get() = if (toGerman) sentence.target[0] else sentence.german[0]
        val accepted get() = if (toGerman) sentence.german else sentence.target
        override val solution get() = accepted[0]
    }

    /** Hear a target-language sentence and type it. */
    data class Listen(val sentence: Sentence) : Exercise() {
        override val solution get() = sentence.target[0]
    }

    /** Pick the right German translation of a target sentence. */
    data class Choice(val sentence: Sentence, val options: List<String>) : Exercise() {
        override val solution get() = sentence.german[0]
    }

    /** "Which of these means X?" – pick the target word. */
    data class Word(val word: Pair<String, String>, val options: List<String>) : Exercise() {
        override val solution get() = word.first
    }

    /** Tap matching pairs. */
    data class Match(val pairs: List<Pair<String, String>>) : Exercise() {
        override val solution get() = ""
    }
}

/** Per-course lookup data used to build word banks and hints. */
class CourseIndex(val course: Course) {
    /** Words that keep their capital letter in chips (German nouns, names, English "I"). */
    private val capTarget = HashSet<String>(course.keepCap)
    private val capGerman = HashSet<String>()
    val targetTokens = LinkedHashSet<String>()
    val germanTokens = LinkedHashSet<String>()
    val glossTarget: Map<String, String>
    val glossGerman: Map<String, String>

    init {
        for (s in course.allSentences) {
            s.target.forEach { collect(it, targetTokens, capTarget) }
            s.german.forEach { collect(it, germanTokens, capGerman) }
        }
        val gt = HashMap(course.gloss)
        val gg = HashMap(course.glossDe)
        for (sk in course.skills) for ((t, d) in sk.words) {
            gt.putIfAbsent(t.lowercase(), d)
            gg.putIfAbsent(d.lowercase(), t)
        }
        glossTarget = gt
        glossGerman = gg
    }

    private fun collect(text: String, into: MutableSet<String>, caps: MutableSet<String>) {
        Text.tokens(text).forEachIndexed { i, w ->
            if (i > 0 && w.first().isUpperCase()) caps += w
            into += w
        }
    }

    /** Lower-cases sentence-initial words unless they are always capitalised. */
    fun display(word: String, german: Boolean): String {
        val caps = if (german) capGerman else capTarget
        return if (word in caps) word else word.replaceFirstChar { it.lowercase() }
    }

    fun hint(word: String, german: Boolean): String? {
        val key = Text.norm(word).replace(" ", "")
        return (if (german) glossGerman else glossTarget)[key]
    }
}

/** Builds lessons the classic way: new sentences are first recognised, then produced. */
class LessonBuilder(private val index: CourseIndex, private val random: Random = Random.Default) {

    private val course get() = index.course

    private enum class Kind { BANK_TO, CHOICE, BANK_FROM, TYPE_TO, TYPE_FROM, LISTEN }

    private val passive = listOf(Kind.BANK_TO, Kind.CHOICE)
    private val active = listOf(Kind.BANK_FROM, Kind.TYPE_TO, Kind.TYPE_FROM, Kind.BANK_FROM)

    fun lesson(skill: Skill, lessonIndex: Int, listening: Boolean): List<Exercise> {
        val fresh = skill.lessonSentences(lessonIndex)
        val earlier = skill.sentences.take(lessonIndex * skill.lessonSize).shuffled(random).take(3)
        val words = skill.words.take(minOf(skill.words.size, 3 + (lessonIndex + 1) * 2))

        val first = fresh.map { make(passive.random(random), it) }
        val second = fresh.map { make(maybeListen(active.random(random), listening, 0.3), it) } +
                earlier.map { make(maybeListen((active + passive).random(random), listening, 0.35), it) }
        val middle = buildList {
            if (words.size >= 3) add(word(words))
            if (words.size >= 4) add(match(words))
        }
        return first.shuffled(random) + middle + second.shuffled(random)
    }

    fun practice(skills: List<Skill>, listening: Boolean): List<Exercise> {
        val sentences = skills.flatMap { it.sentences }.shuffled(random).take(10)
        val words = skills.flatMap { it.words }
        val list = sentences.mapIndexed { i, s ->
            val k = if (i % 2 == 1) active.random(random) else (passive + active).random(random)
            make(maybeListen(k, listening, 0.25), s)
        }.toMutableList()
        if (words.size >= 4) list.add(minOf(3, list.size), match(words))
        if (words.size >= 3) list.add(minOf(7, list.size), word(words.shuffled(random).take(6)))
        return list
    }

    private fun maybeListen(k: Kind, listening: Boolean, chance: Double) =
        if (listening && random.nextDouble() < chance) Kind.LISTEN else k

    private fun make(kind: Kind, s: Sentence): Exercise = when (kind) {
        Kind.BANK_TO -> Exercise.Bank(s, true, chips(s.german[0], german = true))
        Kind.BANK_FROM -> Exercise.Bank(s, false, chips(s.target[0], german = false))
        Kind.CHOICE -> Exercise.Choice(s, (listOf(s.german[0]) + distractors(s, 2)).shuffled(random))
        Kind.TYPE_TO -> Exercise.Type(s, true)
        Kind.TYPE_FROM -> Exercise.Type(s, false)
        Kind.LISTEN -> Exercise.Listen(s)
    }

    /** Converts a listening exercise into a typing one (when the learner can't listen right now). */
    fun withoutListening(list: List<Exercise>): List<Exercise> = list.map {
        if (it is Exercise.Listen) Exercise.Bank(it.sentence, false, chips(it.sentence.target[0], german = false)) else it
    }

    private fun chips(answer: String, german: Boolean): List<String> {
        val words = Text.tokens(answer).map { index.display(it, german) }
        val have = words.map { it.lowercase() }.toSet()
        val pool = (if (german) index.germanTokens else index.targetTokens)
            .map { index.display(it, german) }.distinct()
            .filter { it.lowercase() !in have }.shuffled(random)
        val extra = pool.take(minOf(5, maxOf(3, ceil(words.size * 0.7).toInt())))
        return (words + extra).shuffled(random)
    }

    private fun distractors(s: Sentence, n: Int): List<String> {
        val same = course.skill(s.skillId)?.sentences.orEmpty().filter { it != s }.shuffled(random)
        val rest = course.allSentences.filter { it.skillId != s.skillId }.shuffled(random)
        val accepted = s.german.map { Text.norm(it) }.toSet()
        return (same + rest).map { it.german[0] }
            .filter { Text.norm(it) !in accepted }.distinct().take(n)
    }

    private fun word(words: List<Pair<String, String>>): Exercise.Word {
        val target = words.random(random)
        val others = words.filter { it.first != target.first && it.second != target.second }.shuffled(random).take(2)
        return Exercise.Word(target, (others + target).map { it.first }.shuffled(random))
    }

    private fun match(words: List<Pair<String, String>>): Exercise.Match {
        val seen = HashSet<String>()
        val pairs = words.shuffled(random).filter { seen.add(it.second) && seen.add("t:" + it.first) }.take(5)
        return Exercise.Match(pairs)
    }
}
