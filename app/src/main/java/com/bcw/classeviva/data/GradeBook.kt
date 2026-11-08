package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.util.byName
import java.time.LocalDate

enum class AverageMode(val title: String) {
    allGrades("Media di tutti i voti"),
    subjectAverages("Media delle medie per materia"),
}

data class SubjectSummary(val subjectId: Long, val name: String, val grades: List<Grade>, val average: Double?)

data class TrendPoint(val date: LocalDate, val value: Double)

/** Un voto ipotetico di "E se prendessi…" (id negativo e unico). */
data class HypotheticalGrade(val id: Long, val subjectId: Long, val subjectName: String, val value: Double, val weight: Double = 1.0) {
    companion object {
        private var counter = 0L
        fun make(subjectId: Long, subjectName: String, value: Double, weight: Double = 1.0): HypotheticalGrade {
            counter += 1
            return HypotheticalGrade(-counter, subjectId, subjectName, value, weight)
        }
    }
}

/** Raccolta di voti e periodi con tutti i calcoli sulle medie. */
class GradeBook(
    val grades: List<Grade>,
    val periods: List<Period>,
    val mode: AverageMode = AverageMode.allGrades,
    val weighted: Boolean = false,
) {
    /** Periodi che hanno almeno un voto, ordinati (ricavati dai voti se l'API non li dà). */
    val activePeriods: List<Period> by lazy {
        val used = grades.map { it.periodPosition }.toSet()
        val result = periods.filter { it.position in used }.sortedBy { it.position }
        result.ifEmpty {
            grades.groupBy { it.periodPosition }.toSortedMap().map { (pos, list) ->
                syntheticPeriod(pos, list.firstOrNull()?.periodName?.ifEmpty { null } ?: "Periodo $pos")
            }
        }
    }

    fun gradesIn(period: Int?): List<Grade> = if (period == null) grades else grades.filter { it.periodPosition == period }

    fun average(period: Int? = null): Double? {
        if (mode == AverageMode.subjectAverages) {
            val averages = subjects(period).mapNotNull { it.average }
            if (averages.isEmpty()) return null
            return averages.sum() / averages.size
        }
        return average(gradesIn(period), weighted)
    }

    fun subjects(period: Int? = null): List<SubjectSummary> =
        gradesIn(period).groupBy { it.subjectId }.map { (id, list) ->
            SubjectSummary(id, list.firstOrNull()?.subjectName ?: "Materia", list.sortedByDescending { it.date }, average(list, weighted))
        }.sortedWith { a, b -> byName(a.name, b.name) }

    /** Andamento della media nel tempo: un punto per giorno (la media a fine giornata). */
    fun runningAverage(subjectId: Long? = null, period: Int? = null): List<TrendPoint> {
        var list = gradesIn(period).filter { it.countsTowardAverage }
        if (subjectId != null) list = list.filter { it.subjectId == subjectId }
        list = list.sortedBy { it.date }
        val points = mutableListOf<TrendPoint>()
        val running = mutableListOf<Grade>()
        for (g in list) {
            running += g
            val avg = average(running, weighted) ?: continue
            if (points.isNotEmpty() && points.last().date == g.date) points[points.size - 1] = TrendPoint(g.date, avg)
            else points += TrendPoint(g.date, avg)
        }
        return points
    }

    /** Lo stesso libretto con in più dei voti ipotetici, datati oggi e nel periodo indicato. */
    fun adding(hypothetical: List<HypotheticalGrade>, position: Int, periodName: String): GradeBook {
        val extra = hypothetical.map { h ->
            Grade(
                id = if (h.id < 0) h.id else -kotlin.math.abs(h.id) - 1, subjectId = h.subjectId, subjectName = h.subjectName,
                code = "GRV0", date = Dates.today(), value = h.value, displayValue = GradeFormat.short(h.value), notes = null,
                color = "", canceled = false, underlined = false, periodPosition = position, periodName = periodName,
                componentName = null, weight = h.weight, noAverage = false, teacherName = null, skillDescription = null,
            )
        }
        return GradeBook(grades + extra, periods, mode, weighted)
    }

    companion object {
        fun average(grades: List<Grade>, weighted: Boolean): Double? {
            val valid = grades.filter { it.countsTowardAverage }
            if (valid.isEmpty()) return null
            if (weighted) {
                var sum = 0.0
                var weights = 0.0
                for (g in valid) {
                    val raw = g.weight ?: 1.0
                    val w = if (raw > 0) raw else 1.0
                    sum += (g.value ?: 0.0) * w
                    weights += w
                }
                return if (weights > 0) sum / weights else null
            }
            return valid.sumOf { it.value ?: 0.0 } / valid.size
        }

        /** Voto necessario nelle prossime `count` prove per raggiungere `target`. */
        fun neededGrade(target: Double, current: List<Grade>, count: Int = 1, weighted: Boolean = false): Double {
            val valid = current.filter { it.countsTowardAverage }
            if (weighted) {
                val sum = valid.sumOf { (it.value ?: 0.0) * maxOf(it.weight ?: 1.0, 0.01) }
                val weights = valid.sumOf { maxOf(it.weight ?: 1.0, 0.01) }
                return (target * (weights + count) - sum) / count
            }
            val sum = valid.sumOf { it.value ?: 0.0 }
            return (target * (valid.size + count) - sum) / count
        }

        /** Di quanto questo voto ha spostato la media della materia. */
        fun impact(grade: Grade, book: GradeBook): Double? {
            val same = book.grades.filter { it.subjectId == grade.subjectId && it.periodPosition == grade.periodPosition }
            val before = same.filter { it.date < grade.date || (it.date == grade.date && it.id < grade.id) }
            val avgBefore = average(before, book.weighted) ?: return null
            val avgAfter = average(before + grade, book.weighted) ?: return null
            return avgAfter - avgBefore
        }
    }
}
