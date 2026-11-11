package de.mirranet.parla.engine

import java.text.Normalizer

/** Text normalisation and answer grading, mirroring the classic "accept small slips" behaviour. */
object Text {

    private val PUNCT = Regex("[¿¡.,!?;:\"«»]")
    private val SPACES = Regex("\\s+")
    private val MARKS = Regex("\\p{M}+")

    fun tokens(s: String): List<String> =
        s.replace(PUNCT, " ").trim().split(SPACES).filter { it.isNotEmpty() }

    fun norm(s: String): String =
        s.lowercase().replace('’', '\'').replace('`', '\'').replace('´', '\'')
            .replace(PUNCT, " ").replace(SPACES, " ").trim()

    /** Removes accents, umlauts, ligatures and apostrophes for a lenient second comparison. */
    fun loose(s: String): String =
        Normalizer.normalize(s.replace("'", ""), Normalizer.Form.NFD).replace(MARKS, "")
            .replace("ß", "ss").replace("œ", "oe").replace("æ", "ae")

    fun levenshtein(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    data class Grade(val correct: Boolean, val note: String? = null)

    /** Grades free text against the accepted answers. */
    fun grade(input: String, accepted: List<String>): Grade {
        val n = norm(input)
        if (n.isEmpty()) return Grade(false)
        if (accepted.any { norm(it) == n }) return Grade(true)
        accepted.firstOrNull { loose(norm(it)) == loose(n) }?.let {
            return Grade(true, "Achte auf Akzente und Apostrophe: $it")
        }
        for (a in accepted) {
            val want = norm(a).split(' ')
            val got = n.split(' ')
            if (want.size != got.size) continue
            var diff = 0
            var typo = true
            for (i in want.indices) {
                if (want[i] == got[i]) continue
                diff++
                val x = loose(want[i])
                val y = loose(got[i])
                // A missing/extra ending is usually grammar, not a typo.
                val endings = listOf("s", "n", "e", "t")
                val grammar = endings.any { x + it == y || y + it == x }
                if (want[i].length < 6 || levenshtein(x, y) > 1 || grammar) typo = false
            }
            if (diff == 1 && typo) return Grade(true, "Kleiner Tippfehler – richtig: $a")
        }
        return Grade(false)
    }
}
