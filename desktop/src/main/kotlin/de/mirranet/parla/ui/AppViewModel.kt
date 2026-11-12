package de.mirranet.parla.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.mirranet.parla.data.ApiException
import de.mirranet.parla.data.OfflineException
import de.mirranet.parla.data.Repository
import de.mirranet.parla.desktop.Audio
import de.mirranet.parla.engine.Course
import de.mirranet.parla.engine.CourseIndex
import de.mirranet.parla.engine.Exercise
import de.mirranet.parla.engine.LessonBuilder
import de.mirranet.parla.engine.ProgressLogic
import de.mirranet.parla.engine.Skill
import de.mirranet.parla.engine.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data object CoursePicker : Screen()
    data class Lesson(val session: LessonSession) : Screen()
    data class Result(
        val title: String, val detail: String?, val xp: Int, val mistakes: Int, val goalReached: Boolean,
        /** Share of correct answers in percent, null if nothing was graded. */
        val accuracy: Int?,
    ) : Screen()
    data class Failed(val retry: () -> Unit) : Screen()
}

enum class Tab { LEARN, STATS, SETTINGS }

/** State of a running lesson or practice session. */
class LessonSession(
    val course: Course,
    val index: CourseIndex,
    exercises: List<Exercise>,
    /** Skill + lesson for a lesson, or the practised skills for a practice session. */
    val skillId: String?,
    val lessonIndex: Int?,
    val practiceSkills: List<String>,
    /** Difficulty the session was built with (0..1); see ProgressLogic.difficulty. */
    val difficulty: Double,
) {
    var exercises by mutableStateOf(exercises)
    var position by mutableIntStateOf(0)
    var hearts by mutableIntStateOf(ProgressLogic.HEARTS)
    var mistakes by mutableIntStateOf(0)
    /** Null while answering; set after "Überprüfen". */
    var feedback by mutableStateOf<Feedback?>(null)
    var failed = false

    /** Graded answers (match exercises are not graded). */
    var answered = 0
    var correctAnswers = 0
    /** Runs of right/wrong answers, used to adjust the upcoming exercises during the session. */
    var rightInARow = 0
    var wrongInARow = 0

    val current: Exercise get() = exercises[position]
    val isPractice get() = skillId == null

    data class Feedback(val correct: Boolean, val note: String?, val solution: String)
}

/** UI state holder; lives as long as the application window. */
class AppViewModel(val repo: Repository, val audio: Audio) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val state = repo.state

    var screen by mutableStateOf<Screen>(Screen.Home)
    var tab by mutableStateOf(Tab.LEARN)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private val indexes = HashMap<String, CourseIndex>()

    init {
        // Speaker pressed but no voice for the course language: say so instead of staying silent.
        audio.onMissingVoice = {
            val name = state.value.course?.name ?: "diese Sprache"
            repo.showMessage("Für $name ist auf diesem Computer keine Stimme installiert – mehr dazu in den Einstellungen.")
        }
    }

    /** Soft click sound when a word or an answer is picked. */
    fun tap() = audio.tap(state.value.settings.sound)

    private fun indexFor(c: Course): CourseIndex =
        indexes[c.id]?.takeIf { it.course.hash == c.hash } ?: CourseIndex(c).also { indexes[c.id] = it }

    // ------------------------------------------------------------------ account

    fun authenticate(server: String, username: String, password: String, register: Boolean) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                repo.authenticate(server, username.trim(), password, register)
                screen = Screen.Home
            } catch (e: ApiException) {
                error = e.message
            } catch (e: OfflineException) {
                error = e.reason
            } catch (e: Exception) {
                error = "Unerwarteter Fehler: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun logout(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        busy = true
        val clean = repo.logout()
        busy = false
        screen = Screen.Home
        tab = Tab.LEARN
        onDone(clean)
    }

    fun changePassword(old: String, new: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(
            try {
                repo.changePassword(old, new); null
            } catch (e: ApiException) {
                e.message
            } catch (e: OfflineException) {
                "Keine Verbindung zum Server."
            }
        )
    }

    fun deleteAccount(password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(
            try {
                repo.deleteAccount(password); screen = Screen.Home; null
            } catch (e: ApiException) {
                e.message
            } catch (e: OfflineException) {
                "Keine Verbindung zum Server."
            }
        )
    }

    fun sync() = repo.syncInBackground()

    fun selectCourse(id: String) {
        repo.updateSettings { it.copy(course = id) }
        screen = Screen.Home
        tab = Tab.LEARN
    }

    // ------------------------------------------------------------------ lessons

    private fun builder(c: Course) = LessonBuilder(indexFor(c))

    private fun listening(c: Course): Boolean {
        audio.setLanguage(c.tts)
        return state.value.settings.listen && audio.available
    }

    fun difficulty(c: Course, skill: Skill) = ProgressLogic.difficulty(state.value.progress, c, skill)

    /** The lesson the learner should do next: first unlocked unit that isn't finished. */
    fun nextLesson(c: Course): Pair<Skill, Int>? {
        val p = state.value.progress
        val skill = c.skills.firstOrNull { ProgressLogic.rowOpen(p, c, it.row) && !ProgressLogic.isDone(p, c, it) } ?: return null
        return skill to p.skill(c.id, skill.id).lessons
    }

    /** Finished units that are fading and should be reviewed. */
    fun weakSkills(c: Course): List<Skill> {
        val p = state.value.progress
        return c.skills.filter { ProgressLogic.isDone(p, c, it) && ProgressLogic.strength(p, c, it) < 4 }
    }

    /** [ease] lowers the difficulty, e.g. for a retry after running out of hearts. */
    fun startLesson(skill: Skill, lesson: Int, ease: Double = 0.0) {
        val c = state.value.course ?: return
        audio.setLanguage(c.tts)
        val d = (difficulty(c, skill) - ease).coerceAtLeast(0.0)
        val list = builder(c).lesson(skill, lesson, listening(c), d)
        screen = Screen.Lesson(LessonSession(c, indexFor(c), list, skill.id, lesson, emptyList(), d))
    }

    /** Practice one skill, or the weakest finished ones when [skill] is null. */
    fun startPractice(skill: Skill?) {
        val c = state.value.course ?: return
        val p = state.value.progress
        val skills = skill?.let { listOf(it) } ?: c.skills.filter { ProgressLogic.isDone(p, c, it) }
            .sortedWith(compareBy({ ProgressLogic.strength(p, c, it) }, { p.skill(c.id, it.id).lastPracticed }))
            .take(3)
        if (skills.isEmpty()) return
        audio.setLanguage(c.tts)
        startPracticeSkills(c, skills.map { it.id })
    }

    /** Grades the current exercise. [answer] is typed text or the assembled chips; [choice] for options. */
    fun check(s: LessonSession, answer: String?, choice: String?, forceWrong: Boolean = false) {
        if (s.feedback != null) return
        val ex = s.current
        val grade = when {
            forceWrong -> Text.Grade(false)
            ex is Exercise.Bank -> Text.grade(answer.orEmpty(), ex.accepted)
            ex is Exercise.Type -> Text.grade(answer.orEmpty(), ex.accepted)
            ex is Exercise.Listen -> Text.grade(answer.orEmpty(), ex.sentence.target)
            ex is Exercise.Choice || ex is Exercise.Word -> Text.Grade(choice == ex.solution)
            ex is Exercise.Match -> Text.Grade(true)
            else -> Text.Grade(false)
        }
        if (!grade.correct) {
            s.mistakes++
            if (s.hearts == 0) s.failed = true else s.hearts--
        }
        if (ex !is Exercise.Match) {
            s.answered++
            if (grade.correct) s.correctAnswers++
            adapt(s, grade.correct)
        }
        audio.feedback(grade.correct, state.value.settings.sound)
        val spokenAnswer = when (ex) {
            is Exercise.Bank -> if (!ex.toGerman) ex.sentence.target[0] else null
            is Exercise.Type -> if (!ex.toGerman) ex.sentence.target[0] else null
            is Exercise.Listen -> ex.sentence.target[0]
            else -> null
        }
        spokenAnswer?.let { audio.speak(it) }
        s.feedback = LessonSession.Feedback(grade.correct, grade.note, ex.solution)
    }

    fun next(s: LessonSession) {
        if (s.failed) {
            screen = Screen.Failed {
                // The retry is a bit easier than the attempt that failed.
                if (s.isPractice) {
                    startPracticeSkills(s.course, s.practiceSkills, ease = 0.3)
                } else {
                    s.course.skill(s.skillId!!)?.let { startLesson(it, s.lessonIndex!!, ease = 0.3) }
                }
            }
            return
        }
        if (s.position + 1 >= s.exercises.size) {
            finish(s)
            return
        }
        s.feedback = null
        s.position++
    }

    private fun startPracticeSkills(c: Course, ids: List<String>, ease: Double = 0.0) {
        val skills = ids.mapNotNull { c.skill(it) }
        if (skills.isEmpty()) return
        val d = (skills.map { difficulty(c, it) }.average() - ease).coerceAtLeast(0.0)
        val list = builder(c).practice(skills, listening(c), d)
        screen = Screen.Lesson(LessonSession(c, indexFor(c), list, null, null, ids, d))
    }

    /**
     * In-session adaptation: two mistakes in a row make the next exercises easier, four correct answers
     * in a row make the next ones harder. The long-term level comes from the accuracy stored per skill.
     */
    private fun adapt(s: LessonSession, correct: Boolean) {
        if (correct) {
            s.wrongInARow = 0
            if (++s.rightInARow >= 4) {
                s.exercises = builder(s.course).adjust(s.exercises, s.position + 1, harder = true, count = 2)
                s.rightInARow = 0
            }
        } else {
            s.rightInARow = 0
            if (++s.wrongInARow >= 2) {
                s.exercises = builder(s.course).adjust(s.exercises, s.position + 1, harder = false, count = 3)
                s.wrongInARow = 0
            }
        }
    }

    /** "Can't listen now": turn this session's listening exercises into word-bank exercises. */
    fun skipListening(s: LessonSession) {
        s.exercises = builder(s.course).withoutListening(s.exercises)
    }

    private fun finish(s: LessonSession) {
        val before = state.value.progress.xp[java.time.LocalDate.now().toString()] ?: 0
        val c = s.course
        val title: String
        var detail: String? = null
        val xp: Int
        if (s.isPractice) {
            xp = repo.completePractice(c.id, s.practiceSkills, s.correctAnswers, s.answered)
            title = "Training abgeschlossen!"
            detail = "Deine Fertigkeiten sind wieder voll aufgeladen."
        } else {
            val skill = c.skill(s.skillId!!)!!
            val wasDone = ProgressLogic.isDone(state.value.progress, c, skill)
            xp = repo.completeLesson(c.id, skill.id, s.lessonIndex!!, s.hearts, s.correctAnswers, s.answered)
            val nowDone = ProgressLogic.isDone(state.value.progress, c, skill)
            if (!wasDone && nowDone) {
                title = "Fertigkeit gemeistert!"
                detail = "${skill.icon} ${skill.title} ist jetzt golden."
            } else {
                title = "Lektion abgeschlossen!"
            }
        }
        val goal = state.value.settings.goal
        val accuracy = if (s.answered > 0) (100 * s.correctAnswers / s.answered) else null
        screen = Screen.Result(title, detail, xp, s.mistakes, before < goal && before + xp >= goal, accuracy)
    }

    fun quitLesson() {
        audio.stop()
        screen = Screen.Home
    }

    fun close() {
        viewModelScope.cancel()
        audio.release()
    }
}
