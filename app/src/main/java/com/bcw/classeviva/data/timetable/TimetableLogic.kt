package com.bcw.classeviva.data.timetable

import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.Lesson
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.normalized
import java.time.LocalDate
import kotlin.math.pow

// MARK: - Gruppi e colonne

/** Eventi uguali di seguito (stesso nome, uno subito dopo l'altro). */
data class EntryBlock(val id: String, val entries: List<TimetableEntry>, val start: Int, val end: Int)

data class PlacedBlock(val block: EntryBlock, val column: Int)

/** Blocchi che si sovrappongono, con le colonne affiancate. */
data class Cluster(val id: String, val blocks: List<PlacedBlock>, val columns: Int, val start: Int, val end: Int)

sealed class ListItem {
    data class Pause(val entry: TimetableEntry) : ListItem()
    data class Gap(val start: Int, val end: Int) : ListItem()
    data class Group(val cluster: Cluster) : ListItem()
}

data class TimetableLayout(
    val clusters: List<Cluster>,
    val breaks: List<TimetableEntry>,
    val listItems: List<ListItem>,
    val span: IntRange?,
    val blocks: List<EntryBlock>,
)

/**
 * Disposizione degli eventi di una giornata. Gli eventi uguali di seguito formano un blocco; i
 * blocchi che si sovrappongono formano un gruppo con colonne affiancate. Con `splitAtBreaks` (vista
 * Lista) un intervallo sul passaggio tra due eventi uguali li separa.
 */
fun timetableLayout(entries: List<TimetableEntry>, splitAtBreaks: Boolean = false): TimetableLayout {
    val lessons = entries.filter { !it.isBreak }.sortedWith(entryOrder)
    val breaks = entries.filter { it.isBreak }.sortedWith(entryOrder)
    fun breakFalls(junction: Int) = splitAtBreaks && breaks.any { it.start <= junction && junction <= it.end }

    val blocks = mutableListOf<EntryBlock>()
    for (entry in lessons) {
        val key = entry.matchKey
        var index = -1
        for (i in blocks.indices.reversed()) {
            if (blocks[i].end == entry.start && blocks[i].entries[0].matchKey == key) { index = i; break }
        }
        if (index >= 0 && key.isNotEmpty() && !breakFalls(entry.start)) {
            val b = blocks[index]
            blocks[index] = b.copy(entries = b.entries + entry, end = entry.end)
        } else {
            blocks += EntryBlock(entry.id, listOf(entry), entry.start, entry.end)
        }
    }
    blocks.sortWith(compareBy<EntryBlock> { it.start }.thenByDescending { it.end })

    val clusters = mutableListOf<Cluster>()
    var current = mutableListOf<EntryBlock>()
    var currentEnd = Int.MIN_VALUE
    fun close() {
        if (current.isEmpty()) return
        val columnEnds = mutableListOf<Int>()
        val placed = mutableListOf<PlacedBlock>()
        for (block in current) {
            val column = columnEnds.indexOfFirst { it <= block.start }
            if (column >= 0) {
                columnEnds[column] = block.end
                placed += PlacedBlock(block, column)
            } else {
                columnEnds += block.end
                placed += PlacedBlock(block, columnEnds.size - 1)
            }
        }
        clusters += Cluster(current[0].id, placed, columnEnds.size, current.minOf { it.start }, current.maxOf { it.end })
        current = mutableListOf()
    }
    for (block in blocks) {
        if (current.isNotEmpty() && block.start >= currentEnd) close()
        current += block
        currentEnd = if (current.size == 1) block.end else maxOf(currentEnd, block.end)
    }
    close()

    // Righe della lista: gruppi, intervalli e tempo libero di almeno mezz'ora.
    val listItems = mutableListOf<ListItem>()
    val pending = ArrayDeque(breaks)
    var lastEnd: Int? = null
    for (cluster in clusters) {
        while (pending.isNotEmpty() && pending.first().start <= cluster.start) listItems += ListItem.Pause(pending.removeFirst())
        val le = lastEnd
        if (le != null && cluster.start - le >= 30) listItems += ListItem.Gap(le, cluster.start)
        listItems += ListItem.Group(cluster)
        lastEnd = maxOf(lastEnd ?: cluster.end, cluster.end)
    }
    for (entry in pending) listItems += ListItem.Pause(entry)

    val starts = clusters.map { it.start } + breaks.map { it.start }
    val ends = clusters.map { it.end } + breaks.map { it.end }
    val span = if (starts.isEmpty()) null else starts.min()..ends.max()
    return TimetableLayout(clusters, breaks, listItems, span, blocks)
}

// MARK: - Verifiche di Classeviva

/** La materia dell'evento dell'orario corrisponde a quella della verifica (mai il docente). */
fun subjectMatches(entry: TimetableEntry, event: AgendaEvent): Boolean {
    if (entry.subjectId != null && event.subjectId != null) return entry.subjectId == event.subjectId
    val entryName = entry.matchKey
    val eventName = normalized(event.subjectName ?: "")
    if (entryName.isEmpty() || eventName.isEmpty()) return false
    if (entryName == eventName) return true
    return (entryName.length >= 4 && eventName.contains(entryName)) || (eventName.length >= 4 && entryName.contains(eventName))
}

data class TestMatch(val matched: Map<String, List<AgendaEvent>>, val unmatched: List<AgendaEvent>)

/** Associa le verifiche alla lezione di quell'ora o, altrimenti, alla prima della giornata. */
fun matchTests(tests: List<AgendaEvent>, entries: List<TimetableEntry>): TestMatch {
    val lessons = entries.filter { !it.isBreak }.sortedWith(entryOrder)
    val matched = LinkedHashMap<String, MutableList<AgendaEvent>>()
    val unmatched = mutableListOf<AgendaEvent>()
    for (test in tests) {
        val candidates = lessons.filter { subjectMatches(it, test) }
        if (candidates.isEmpty()) {
            unmatched += test
            continue
        }
        val minute = Dates.minuteOfDay(test.begin)
        val timed = if (!test.isFullDay && minute > 0) candidates.firstOrNull { it.start <= minute && minute < it.end } else null
        matched.getOrPut((timed ?: candidates[0]).id) { mutableListOf() } += test
    }
    return TestMatch(matched, unmatched)
}

// MARK: - Generatore

data class GeneratedDraft(val entries: List<TimetableEntry>, val analyzedDays: Int)

fun generationRange(settings: GenerationSettings, today: LocalDate = Dates.today()): Pair<LocalDate, LocalDate> {
    val schoolStart = Dates.schoolYear(today).start
    val from = if (settings.weeks == 0) schoolStart else maxOf(schoolStart, today.minusDays(7L * settings.weeks))
    return from to today
}

/**
 * Bozza dell'orario dalle lezioni registrate: per ogni giorno della settimana e ora tiene le
 * materie che si ripetono (le settimane recenti contano di più).
 */
fun generateEntries(lessons: List<Lesson>, settings: GenerationSettings, today: LocalDate = Dates.today()): GeneratedDraft {
    val threshold = 0.34
    val parallelThreshold = 0.6
    val halfLifeWeeks = 4.0
    fun keyOf(l: Lesson) = if (l.subjectId != null) "id:${l.subjectId}" else "name:${normalized(l.subjectName)}"
    val byDay = LinkedHashMap<LocalDate, MutableList<Lesson>>()
    for (lesson in lessons) {
        if (lesson.hour <= 0) continue
        byDay.getOrPut(lesson.date) { mutableListOf() } += lesson
    }
    val observed = HashMap<Int, Double>()
    val scores = LinkedHashMap<Int, LinkedHashMap<Int, LinkedHashMap<String, Double>>>()
    val names = HashMap<String, LinkedHashMap<String, Double>>()
    val teachers = HashMap<String, LinkedHashMap<String, Double>>()
    val keyInfo = HashMap<String, Long?>()
    fun bump(map: MutableMap<String, LinkedHashMap<String, Double>>, key: String, value: String, amount: Double = 1.0) {
        val inner = map.getOrPut(key) { LinkedHashMap() }
        inner[value] = (inner[value] ?: 0.0) + amount
    }
    for ((day, dayLessons) in byDay) {
        val weekday = Weekday.of(day)
        val weeksAgo = maxOf(0L, Dates.daysBetween(day, today)) / 7.0
        val weight = 0.5.pow(weeksAgo / halfLifeWeeks)
        observed[weekday] = (observed[weekday] ?: 0.0) + weight
        val counted = HashSet<String>()
        for (lesson in dayLessons) {
            val key = keyOf(lesson)
            keyInfo[key] = lesson.subjectId
            bump(names, key, lesson.subjectName)
            if (lesson.authorName.isNotEmpty()) bump(teachers, key, lesson.authorName)
            val length = maxOf(1L, roundHalfAway(lesson.duration)).toInt()
            for (hour in lesson.hour until lesson.hour + length) {
                val mark = "$hour|$key"
                if (!counted.add(mark)) continue
                val hours = scores.getOrPut(weekday) { LinkedHashMap() }
                val inner = hours.getOrPut(hour) { LinkedHashMap() }
                inner[key] = (inner[key] ?: 0.0) + weight
            }
        }
    }
    fun mostCommon(counts: Map<String, Double>?): String {
        if (counts == null) return ""
        var best = ""
        var bestCount = -1.0
        for ((value, count) in counts) {
            if (count > bestCount || (count == bestCount && value < best)) {
                best = value
                bestCount = count
            }
        }
        return best
    }
    val entries = mutableListOf<TimetableEntry>()
    for ((weekday, hours) in scores) {
        val total = observed[weekday] ?: 0.0
        if (total <= 0) continue
        for ((hour, candidates) in hours) {
            val ranked = candidates.entries.sortedWith { a, b ->
                if (a.value != b.value) b.value.compareTo(a.value)
                else mostCommon(names[a.key]).compareTo(mostCommon(names[b.key]))
            }
            val best = ranked.firstOrNull() ?: continue
            if (best.value < threshold * total) continue
            val chosen = listOf(best.key) + ranked.drop(1).filter { it.value >= parallelThreshold * total }.map { it.key }
            val start = settings.firstHourStart + (hour - 1) * settings.hourLength
            val end = start + settings.hourLength
            if (start < 0 || end > 1440) continue
            for (key in chosen) {
                entries += TimetableEntry(
                    weekday = weekday, start = start, end = end, kind = EntryKind.lesson, title = mostCommon(names[key]),
                    subjectId = keyInfo[key], teacher = mostCommon(teachers[key]),
                )
            }
        }
    }
    for (weekday in entries.map { it.weekday }.toSortedSet()) {
        val dayEntries = entries.filter { it.weekday == weekday }
        for (rule in settings.breaks) {
            if (rule.duration <= 0) continue
            val end = minOf(1440, rule.start + rule.duration)
            if (dayEntries.none { it.start < end && rule.start < it.end }) continue
            entries += TimetableEntry(weekday = weekday, start = rule.start, end = end, kind = EntryKind.breakTime, title = "Intervallo")
        }
    }
    entries.sortWith(compareBy<TimetableEntry>({ it.weekday }, { it.start }).thenComparator { a, b -> a.title.compareTo(b.title) })
    return GeneratedDraft(entries, byDay.size)
}

// MARK: - Giorno predefinito

/**
 * Giorno che l'Orario mostra da solo (stessa regola dell'app iOS, dei widget e del web): oggi, se
 * c'è ancora un evento non finito; altrimenti il primo dei 7 giorni successivi con eventi; se no
 * oggi. Contano solo gli eventi dell'orario salvato, non intervalli, bozza o verifiche.
 */
fun defaultTimetableDay(entriesOf: (Int) -> List<TimetableEntry>, now: java.time.LocalDateTime = Dates.now()): LocalDate {
    val today = now.toLocalDate()
    fun events(day: LocalDate) = entriesOf(Weekday.of(day)).filter { !it.isBreak }
    val minute = Dates.minuteOfDay(now)
    if (events(today).any { it.end > minute }) return today
    for (i in 1..7) {
        val day = today.plusDays(i.toLong())
        if (events(day).isNotEmpty()) return day
    }
    return today
}

/** Come si chiama il giorno predefinito sul pulsante accanto al giorno in grande. */
fun jumpTitle(day: LocalDate): String = when {
    Dates.isToday(day) -> "Oggi"
    Dates.isTomorrow(day) -> "Domani"
    else -> Weekday.name(Weekday.of(day))
}

// MARK: - Backup

const val BACKUP_FORMAT = "com.bcw.timetable-backup"
const val BACKUP_VERSION = 1

object BackupError {
    const val notABackup = "Il file scelto non è un backup dell'orario di BCW."
    const val newerVersion = "Il backup è stato creato da una versione più recente di BCW. Aggiorna l'app per importarlo."
    const val noSession = "Accedi a un account per importare l'orario."
}

data class BackupFile(val data: String, val storedName: String)
data class Backup(val appVersion: String, val exportedAt: java.time.Instant, val files: List<BackupFile>, val timetable: Timetable)

/** Un nome di file semplice: niente cartelle e niente nomi nascosti. */
fun isPlainFileName(name: String) = name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.startsWith(".")
