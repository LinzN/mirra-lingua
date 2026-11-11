package de.mirranet.lingua.engine

import kotlin.math.roundToInt
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
    /** Tokens per skill, so hard word banks can offer look-alike distractors from the same unit. */
    val targetTokensBySkill = HashMap<String, LinkedHashSet<String>>()
    val germanTokensBySkill = HashMap<String, LinkedHashSet<String>>()
    val glossTarget: Map<String, String>
    val glossGerman: Map<String, String>

    init {
        for (s in course.allSentences) {
            val t = targetTokensBySkill.getOrPut(s.skillId) { LinkedHashSet() }
            val g = germanTokensBySkill.getOrPut(s.skillId) { LinkedHashSet() }
            s.target.forEach { collect(it, targetTokens, capTarget, t) }
            s.german.forEach { collect(it, germanTokens, capGerman, g) }
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

    private fun collect(text: String, into: MutableSet<String>, caps: MutableSet<String>, perSkill: MutableSet<String>) {
        Text.tokens(text).forEachIndexed { i, w ->
            if (i > 0 && w.first().isUpperCase()) caps += w
            into += w
            perSkill += w
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

/**
 * Builds lessons the classic way: new sentences are first recognised, then produced.
 *
 * The mix adapts to a difficulty value 0..1 (see [ProgressLogic.difficulty]):
 * * low: more recognition (word banks into German, multiple choice), few and obviously wrong distractors,
 *   no review of older sentences, rare listening
 * * high: new sentences often skip straight to production, more free typing and listening,
 *   more chips with look-alike words from the same unit, confusable answer options, more review
 */
class LessonBuilder(private val index: CourseIndex, private val random: Random = Random.Default) {

    private val course get() = index.course

    enum class Kind { BANK_TO, CHOICE, BANK_FROM, TYPE_TO, TYPE_FROM, LISTEN }

    fun lesson(skill: Skill, lessonIndex: Int, listening: Boolean, difficulty: Double = 0.33): List<Exercise> {
        val d = difficulty.coerceIn(0.0, 1.0)
        val fresh = skill.lessonSentences(lessonIndex)
        val reviewCount = (1 + 2 * d).roundToInt()
        val earlier = skill.sentences.take(lessonIndex * skill.lessonSize).shuffled(random).take(reviewCount)
        val words = skill.words.take(minOf(skill.words.size, 3 + (lessonIndex + 1) * 2))

        // Strong learners skip the recognition step for some new sentences.
        val first = fresh.filter { d < 0.67 || random.nextDouble() > (d - 0.6) }.map { make(passiveKind(d), it, d) }
        val second = fresh.map { make(activeKind(d, listening), it, d) } +
                earlier.map { make(activeKind(d, listening), it, d) }
        val middle = buildList {
            if (words.size >= 3) add(word(words, d))
            if (words.size >= 4) add(match(words))
        }
        return first.shuffled(random) + middle + second.shuffled(random)
    }

    fun practice(skills: List<Skill>, listening: Boolean, difficulty: Double = 0.33): List<Exercise> {
        val d = difficulty.coerceIn(0.0, 1.0)
        val sentences = skills.flatMap { it.sentences }.shuffled(random).take(10)
        val words = skills.flatMap { it.words }
        val list = sentences.mapIndexed { i, s ->
            // Practice starts gently and leans on production in the second half.
            make(if (i < 3 && d < 0.5) passiveKind(d) else activeKind(d, listening), s, d)
        }.toMutableList()
        if (words.size >= 4) list.add(minOf(3, list.size), match(words))
        if (words.size >= 3) list.add(minOf(7, list.size), word(words.shuffled(random).take(6), d))
        return list
    }

    private fun passiveKind(d: Double): Kind = weighted(
        Kind.BANK_TO to 1.0 + d,
        Kind.CHOICE to 1.6 - d,
    )

    private fun activeKind(d: Double, listening: Boolean): Kind = weighted(
        Kind.BANK_FROM to 3.0 - 2.0 * d,
        Kind.BANK_TO to 1.2 * (1 - d),
        Kind.TYPE_TO to 0.6 + 1.4 * d,
        Kind.TYPE_FROM to 0.2 + 2.3 * d,
        Kind.LISTEN to if (listening) 0.3 + 1.2 * d else 0.0,
    )

    private fun weighted(vararg options: Pair<Kind, Double>): Kind {
        val total = options.sumOf { it.second.coerceAtLeast(0.0) }
        var r = random.nextDouble() * total
        for ((k, w) in options) {
            r -= w.coerceAtLeast(0.0)
            if (r <= 0) return k
        }
        return options.last { it.second > 0 }.first
    }

    private fun make(kind: Kind, s: Sentence, d: Double): Exercise = when (kind) {
        Kind.BANK_TO -> Exercise.Bank(s, true, chips(s, german = true, d))
        Kind.BANK_FROM -> Exercise.Bank(s, false, chips(s, german = false, d))
        Kind.CHOICE -> Exercise.Choice(s, (listOf(s.german[0]) + distractors(s, 2, d)).shuffled(random))
        Kind.TYPE_TO -> Exercise.Type(s, true)
        Kind.TYPE_FROM -> Exercise.Type(s, false)
        Kind.LISTEN -> Exercise.Listen(s)
    }

    // ------------------------------------------------------------------ in-session adjustment

    /** Converts listening exercises into word-bank exercises (when the learner can't listen right now). */
    fun withoutListening(list: List<Exercise>): List<Exercise> = list.map {
        if (it is Exercise.Listen) Exercise.Bank(it.sentence, false, chips(it.sentence, german = false, 0.5)) else it
    }

    /**
     * Makes the next [count] exercises after [from] easier (after repeated mistakes) or harder (after a run
     * of correct answers). Exercises before [from] are untouched.
     */
    fun adjust(list: List<Exercise>, from: Int, harder: Boolean, count: Int = 3): List<Exercise> {
        var changed = 0
        return list.mapIndexed { i, e ->
            if (i < from || changed >= count) return@mapIndexed e
            val next = if (harder) harder(e) else easier(e)
            if (next !== e) changed++
            next
        }
    }

    private fun easier(e: Exercise): Exercise = when (e) {
        is Exercise.Type -> Exercise.Bank(e.sentence, e.toGerman, chips(e.sentence, e.toGerman, 0.0))
        is Exercise.Listen -> Exercise.Bank(e.sentence, false, chips(e.sentence, german = false, 0.0))
        is Exercise.Bank -> if (!e.toGerman) Exercise.Bank(e.sentence, true, chips(e.sentence, german = true, 0.0)) else e
        else -> e
    }

    private fun harder(e: Exercise): Exercise = when (e) {
        is Exercise.Bank -> Exercise.Type(e.sentence, e.toGerman)
        is Exercise.Choice -> Exercise.Bank(e.sentence, false, chips(e.sentence, german = false, 1.0))
        else -> e
    }

    // ------------------------------------------------------------------ distractors

    private fun chips(s: Sentence, german: Boolean, d: Double): List<String> {
        val answer = if (german) s.german[0] else s.target[0]
        val words = Text.tokens(answer).map { index.display(it, german) }
        val have = words.map { it.lowercase() }.toSet()
        val sameSkill = (if (german) index.germanTokensBySkill else index.targetTokensBySkill)[s.skillId].orEmpty()
        val all = if (german) index.germanTokens else index.targetTokens
        // Hard: look-alikes from the same unit first. Easy: anything, shuffled.
        val ordered = if (d >= 0.5) sameSkill.shuffled(random) + all.shuffled(random) else all.shuffled(random)
        val pool = ordered.map { index.display(it, german) }.distinct().filter { it.lowercase() !in have }
        val extra = (2 + 4 * d).roundToInt().coerceIn(2, 6)
        return (words + pool.take(extra)).shuffled(random)
    }

    private fun distractors(s: Sentence, n: Int, d: Double): List<String> {
        val same = course.skill(s.skillId)?.sentences.orEmpty().filter { it != s }.shuffled(random)
        val rest = course.allSentences.filter { it.skillId != s.skillId }.shuffled(random)
        val accepted = s.german.map { Text.norm(it) }.toSet()
        // Hard: options from the same unit are easy to confuse. Easy: options from elsewhere.
        val ordered = if (d >= 0.5) same + rest else rest + same
        return ordered.map { it.german[0] }.filter { Text.norm(it) !in accepted }.distinct().take(n)
    }

    private fun word(words: List<Pair<String, String>>, d: Double): Exercise.Word {
        val target = words.random(random)
        val optionCount = if (d >= 0.5) 3 else 2
        val others = words.filter { it.first != target.first && it.second != target.second }.shuffled(random).take(optionCount)
        return Exercise.Word(target, (others + target).map { it.first }.shuffled(random))
    }

    private fun match(words: List<Pair<String, String>>): Exercise.Match {
        val seen = HashSet<String>()
        val pairs = words.shuffled(random).filter { seen.add(it.second) && seen.add("t:" + it.first) }.take(5)
        return Exercise.Match(pairs)
    }
}
