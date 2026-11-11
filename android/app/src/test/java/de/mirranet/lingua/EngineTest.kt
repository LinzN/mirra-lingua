package de.mirranet.lingua

import de.mirranet.lingua.data.Codec
import de.mirranet.lingua.engine.CourseIndex
import de.mirranet.lingua.engine.Exercise
import de.mirranet.lingua.engine.LearnEvent
import de.mirranet.lingua.engine.LessonBuilder
import de.mirranet.lingua.engine.Progress
import de.mirranet.lingua.engine.ProgressLogic
import de.mirranet.lingua.engine.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** Runs the lesson engine against the real course files from /content/courses. */
class EngineTest {

    private fun course(id: String) = Codec.parseCourse(File("../../content/courses/$id.json").readText(), "test")

    @Test
    fun grading() {
        assertTrue(Text.grade("le garcon mange une pomme", listOf("Le garçon mange une pomme.")).correct)
        assertTrue(Text.grade("I dont eat meat", listOf("I don't eat meat.")).correct)
        assertFalse(Text.grade("Je mange du pomme", listOf("Je mange une pomme.")).correct)
        assertTrue(Text.grade("nous mangons du pain", listOf("Nous mangeons du pain.")).note!!.startsWith("Kleiner"))
        assertFalse(Text.grade("she eat rice", listOf("She eats rice.")).correct)
        assertEquals(null, Text.grade("  Hello ,  how are you ? ", listOf("Hello, how are you?")).note)
    }

    @Test
    fun everyGeneratedExerciseIsSolvable() {
        for (id in listOf("en", "fr")) {
            val c = course(id)
            val index = CourseIndex(c)
            repeat(10) { seed ->
                val builder = LessonBuilder(index, Random(seed))
                for (skill in c.skills) for (l in 0 until skill.lessonCount) {
                    for (e in builder.lesson(skill, l, listening = true) + builder.practice(listOf(skill), true)) {
                        when (e) {
                            is Exercise.Bank -> {
                                val chips = e.chips.map { it.lowercase() }.toMutableList()
                                Text.tokens(e.solution).forEach { assertTrue("chip $it for ${e.solution}", chips.remove(it.lowercase())) }
                            }
                            is Exercise.Type -> assertTrue(Text.grade(e.solution, e.accepted).correct)
                            is Exercise.Listen -> assertTrue(Text.grade(e.solution, e.sentence.target).correct)
                            is Exercise.Choice -> assertTrue(e.solution in e.options && e.options.distinct().size == 3)
                            is Exercise.Word -> assertTrue(e.solution in e.options)
                            is Exercise.Match -> assertTrue(e.pairs.size in 3..5)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun progressMatchesServerRules() {
        val c = course("en")
        val g1 = c.skill("g1")!!
        var p = Progress()
        for (l in 0 until g1.lessonCount) {
            p = ProgressLogic.apply(p, LearnEvent.Lesson("e$l", "en", "g1", l, 13, "2026-11-11", 1_000), c)
        }
        assertTrue(ProgressLogic.isDone(p, c, g1))
        assertEquals(1, p.skill("en", "g1").reps)
        assertEquals(39, p.xp["2026-11-11"])
        assertEquals(5, ProgressLogic.strength(p, c, g1, 1_000))
        assertEquals(1, ProgressLogic.strength(p, c, g1, 1_000 + 3 * 86_400_000L))
        p = ProgressLogic.apply(p, LearnEvent.Practice("p1", "en", listOf("g1"), 10, "2026-11-11", 2_000), c)
        assertEquals(2, p.skill("en", "g1").reps)
        assertTrue(ProgressLogic.rowOpen(p, c, 1))
        p = ProgressLogic.apply(p, LearnEvent.Reset("r1", "en", "2026-11-11", 3_000), c)
        assertFalse(ProgressLogic.isDone(p, c, g1))
        assertEquals(49, p.xp["2026-11-11"])
    }

    @Test
    fun streak() {
        val today = LocalDate.parse("2026-11-11")
        assertEquals(2, ProgressLogic.streak(mapOf("2026-11-10" to 5, "2026-11-09" to 3), today))
        assertEquals(3, ProgressLogic.streak(mapOf("2026-11-11" to 1, "2026-11-10" to 5, "2026-11-09" to 3), today))
        assertEquals(0, ProgressLogic.streak(mapOf("2026-11-08" to 5), today))
    }

    @Test
    fun eventsRoundTrip() {
        val events = listOf(
            LearnEvent.Lesson("a1b2c3d4", "fr", "g1", 2, 13, "2026-11-11", 5),
            LearnEvent.Practice("e5f6g7h8", "fr", listOf("g1", "g2"), 10, "2026-11-11", 6),
            LearnEvent.Reset("i9j0k1l2", "fr", "2026-11-11", 7),
        )
        assertEquals(events, Codec.parseEvents(Codec.eventsJson(events)))
        val hint = CourseIndex(course("fr")).hint("C'est", german = false)
        assertNotNull(hint)
    }
}
