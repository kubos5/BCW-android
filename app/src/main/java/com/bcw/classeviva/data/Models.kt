package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.StableID
import com.bcw.classeviva.util.bool
import com.bcw.classeviva.util.child
import com.bcw.classeviva.util.dbl
import com.bcw.classeviva.util.int
import com.bcw.classeviva.util.list
import com.bcw.classeviva.util.nameCased
import com.bcw.classeviva.util.nilIfEmpty
import com.bcw.classeviva.util.sentenceCased
import com.bcw.classeviva.util.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime

private val distantPast: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

// MARK: - Accesso e profilo

data class LoginChoice(val ident: String, val name: String, val school: String)

data class LoginResponse(
    val ident: String?, val firstName: String?, val lastName: String?, val token: String?,
    val expire: LocalDateTime?, val choices: List<LoginChoice>,
)

fun parseLoginChoice(c: JsonObject) = LoginChoice(c.str("ident") ?: "", c.str("name") ?: "", c.str("school") ?: "")

fun parseLoginResponse(c: JsonObject) = LoginResponse(
    ident = c.str("ident"), firstName = c.str("firstName"), lastName = c.str("lastName"), token = c.str("token"),
    expire = Dates.parseDateTime(c.str("expire")), choices = c.list("choices", ::parseLoginChoice),
)

data class Card(
    val ident: String, val usrType: String, val firstName: String, val lastName: String, val birthDate: LocalDate?,
    val fiscalCode: String?, val schoolCode: String?, val schoolName: String?, val schoolDedication: String?,
    val schoolCity: String?, val schoolProvince: String?, val miurSchoolCode: String?,
) {
    val fullName: String get() = "$firstName $lastName".nameCased()
    val initials: String get() = ((firstName.take(1)) + (lastName.take(1))).uppercase()
    val schoolDescription: String?
        get() {
            val parts = listOf(schoolName, schoolDedication).mapNotNull { it.nilIfEmpty() }
            return if (parts.isEmpty()) null else parts.joinToString(" ").nameCased()
        }
    val userTypeDescription: String get() = when (usrType) { "G" -> "Genitore"; "S" -> "Studente"; else -> "Utente" }
}

fun parseCard(c: JsonObject) = Card(
    ident = c.str("ident") ?: "", usrType = c.str("usrType") ?: "S", firstName = c.str("firstName") ?: "",
    lastName = c.str("lastName") ?: "", birthDate = Dates.parseDate(c.str("birthDate")), fiscalCode = c.str("fiscalCode"),
    schoolCode = c.str("schCode"), schoolName = c.str("schName"), schoolDedication = c.str("schDedication"),
    schoolCity = c.str("schCity"), schoolProvince = c.str("schProv"), miurSchoolCode = c.str("miurSchoolCode"),
)

fun parseCardResponse(c: JsonElement): Card? {
    val o = c as? JsonObject ?: return null
    o.child("card")?.let { return parseCard(it) }
    return o.list("cards", ::parseCard).firstOrNull()
}

// MARK: - Materie, periodi, calendario

data class Subject(val id: Long, val name: String, val order: Long, val teachers: List<String>)

fun parseSubject(c: JsonObject) = Subject(
    id = c.int("id") ?: 0, name = c.str("description").sentenceCased(), order = c.int("order") ?: 0,
    teachers = c.list("teachers") { it.str("teacherName").nameCased() },
)

fun parseSubjectsResponse(c: JsonElement) = (c as? JsonObject).list("subjects", ::parseSubject)

data class Period(
    val position: Int, val code: String, val name: String, val label: String?, val isFinal: Boolean,
    val start: LocalDate?, val end: LocalDate?,
) {
    fun contains(date: LocalDate): Boolean {
        if (start == null || end == null) return false
        return !date.isBefore(start) && date.isBefore(end.plusDays(1))
    }
}

fun parsePeriod(c: JsonObject) = Period(
    position = (c.int("periodPos") ?: 0).toInt(), code = c.str("periodCode") ?: "",
    name = (c.str("periodDesc") ?: "Periodo").sentenceCased(), label = c.str("periodLabel"), isFinal = c.bool("isFinal"),
    start = Dates.parseDate(c.str("dateStart")), end = Dates.parseDate(c.str("dateEnd")),
)

fun parsePeriodsResponse(c: JsonElement) = (c as? JsonObject).list("periods", ::parsePeriod)

fun syntheticPeriod(position: Int, name: String) = Period(position, "", name.sentenceCased(), null, false, null, null)

data class CalendarDay(val date: LocalDate, val status: String) {
    val isSchoolDay get() = status == "SD"
    val isHoliday get() = status == "HD" || status == "NW"
}

fun parseCalendarDay(c: JsonObject) = CalendarDay(Dates.parseDate(c.str("dayDate")) ?: distantPast.toLocalDate(), c.str("dayStatus") ?: "US")
fun parseCalendarResponse(c: JsonElement) = (c as? JsonObject).list("calendar", ::parseCalendarDay)

// MARK: - Voti

data class Grade(
    val id: Long, val subjectId: Long, val subjectName: String, val code: String, val date: LocalDate,
    val value: Double?, val displayValue: String, val notes: String?, val color: String, val canceled: Boolean,
    val underlined: Boolean, val periodPosition: Int, val periodName: String, val componentName: String?,
    val weight: Double?, val noAverage: Boolean, val teacherName: String?, val skillDescription: String?,
) {
    /**
     * Un voto concorre alla media se ha un valore e non è annullato, "blu" (non fa media) o una
     * prova con punteggio (GRT1, es. "35/50").
     */
    val countsTowardAverage: Boolean get() = value != null && !canceled && !noAverage && color != "blue" && code != "GRT1"
    val kind: String get() = componentName ?: when (code) {
        "GRV0" -> "Scritto"; "GRV1" -> "Orale"; "GRV2" -> "Pratico"; "GRT1" -> "Prova"; else -> "Voto"
    }
}

fun parseGrade(c: JsonObject): Grade {
    val subjectName = (c.str("subjectDesc") ?: "Materia").sentenceCased()
    val displayValue = c.str("displayValue") ?: "–"
    val date = Dates.parseDate(c.str("evtDate")) ?: distantPast.toLocalDate()
    return Grade(
        id = c.int("evtId") ?: StableID.make(subjectName, displayValue, Dates.dayKey(date)),
        subjectId = c.int("subjectId") ?: 0, subjectName = subjectName, code = c.str("evtCode") ?: "", date = date,
        value = c.dbl("decimalValue"), displayValue = displayValue, notes = c.str("notesForFamily").nilIfEmpty(),
        color = c.str("color") ?: "", canceled = c.bool("canceled"), underlined = c.bool("underlined"),
        periodPosition = (c.int("periodPos") ?: 0).toInt(), periodName = c.str("periodDesc").sentenceCased(),
        componentName = c.str("componentDesc").nilIfEmpty()?.sentenceCased(), weight = c.dbl("weightFactor"),
        noAverage = c.bool("noAverage"), teacherName = c.str("teacherName").nilIfEmpty()?.nameCased(),
        skillDescription = c.str("skillDesc").nilIfEmpty(),
    )
}

fun parseGradesResponse(c: JsonElement) = (c as? JsonObject).list("grades", ::parseGrade)

// MARK: - Agenda, lezioni, assenze

enum class AgendaKind(val title: String, val icon: String) {
    homework("Compiti", "book"), test("Verifiche", "test"), event("Eventi", "calendar"),
}

private val testWords = listOf("verifica", "compito in classe", "interrogazion", "test ", "prova scritta",
    "prova orale", "simulazione", "esame", "compito di", "verifiche")
private val homeworkWords = listOf("per casa", "esercizi", "studiare", "pag.", "pagina", "leggere", "ripassare")

data class AgendaEvent(
    val id: Long, val code: String, val begin: LocalDateTime, val end: LocalDateTime, val isFullDay: Boolean,
    val notes: String, val authorName: String, val classDescription: String?, val subjectId: Long?,
    val subjectName: String?, val homeworkId: Long?, val kind: AgendaKind,
) {
    val day: LocalDate get() = begin.toLocalDate()
    val title: String get() = subjectName ?: authorName.ifEmpty { "Evento" }
    val timeDescription: String
        get() = when {
            isFullDay -> "Tutto il giorno"
            begin == end -> Fmt.time(begin)
            else -> "${Fmt.time(begin)} – ${Fmt.time(end)}"
        }
}

fun parseAgendaEvent(c: JsonObject): AgendaEvent {
    val code = c.str("evtCode") ?: "AGNT"
    val begin = Dates.parseDateTime(c.str("evtDatetimeBegin")) ?: distantPast
    val notes = (c.str("notes") ?: "").trim()
    val subjectName = c.str("subjectDesc").nilIfEmpty()?.sentenceCased()
    val text = notes.lowercase(Dates.italian)
    val kind = when {
        code == "AGHW" -> AgendaKind.homework
        testWords.any { text.contains(it) } -> AgendaKind.test
        code == "AGNT" && subjectName != null && homeworkWords.any { text.contains(it) } -> AgendaKind.homework
        else -> AgendaKind.event
    }
    return AgendaEvent(
        id = c.int("evtId") ?: StableID.make(code, notes, Dates.dayKey(begin.toLocalDate())), code = code, begin = begin,
        end = Dates.parseDateTime(c.str("evtDatetimeEnd")) ?: begin, isFullDay = c.bool("isFullDay"), notes = notes,
        authorName = c.str("authorName").nameCased(), classDescription = c.str("classDesc").nilIfEmpty(),
        subjectId = c.int("subjectId"), subjectName = subjectName, homeworkId = c.int("homeworkId"), kind = kind,
    )
}

fun parseAgendaResponse(c: JsonElement) = (c as? JsonObject).list("agenda", ::parseAgendaEvent)

data class Lesson(
    val id: Long, val date: LocalDate, val code: String, val hour: Int, val duration: Double, val classDescription: String?,
    val authorName: String, val subjectId: Long?, val subjectName: String, val type: String, val topic: String,
) {
    val hoursDescription: String
        get() {
            val d = Math.round(duration).toInt()
            return if (d <= 1) "${hour}ª ora" else "${hour}ª–${hour + d - 1}ª ora"
        }
}

fun parseLesson(c: JsonObject): Lesson {
    val date = Dates.parseDate(c.str("evtDate")) ?: distantPast.toLocalDate()
    val subjectName = (c.str("subjectDesc") ?: "Lezione").sentenceCased()
    val hour = (c.int("evtHPos") ?: 0).toInt()
    return Lesson(
        id = c.int("evtId") ?: StableID.make(subjectName, hour.toString(), Dates.dayKey(date)), date = date,
        code = c.str("evtCode") ?: "", hour = hour, duration = c.dbl("evtDuration") ?: 1.0,
        classDescription = c.str("classDesc").nilIfEmpty(), authorName = c.str("authorName").nameCased(),
        subjectId = c.int("subjectId"), subjectName = subjectName, type = c.str("lessonType").sentenceCased(),
        topic = (c.str("lessonArg") ?: "").trim(),
    )
}

fun parseLessonsResponse(c: JsonElement) = (c as? JsonObject).list("lessons", ::parseLesson)

enum class AbsenceKind(val title: String, val plural: String, val icon: String, val letter: String) {
    absence("Assenza", "Assenze", "userX", "A"),
    late("Ritardo", "Ritardi", "clockAlert", "R"),
    shortLate("Ritardo breve", "Ritardi brevi", "clock", "Rb"),
    earlyExit("Uscita anticipata", "Uscite", "doorOpen", "U"),
}

data class Absence(
    val id: Long, val code: String, val date: LocalDate, val hour: Int?, val value: Long?, val isJustified: Boolean,
    val justificationReason: String?,
) {
    val kind: AbsenceKind
        get() = when (code) { "ABR0" -> AbsenceKind.late; "ABR1" -> AbsenceKind.shortLate; "ABU0" -> AbsenceKind.earlyExit; else -> AbsenceKind.absence }
    val detail: String
        get() = when (kind) {
            AbsenceKind.absence -> "Giornata intera"
            AbsenceKind.late, AbsenceKind.shortLate -> if (hour != null) "Entrata alla ${hour}ª ora" else "Entrata in ritardo"
            AbsenceKind.earlyExit -> if (hour != null) "Uscita alla ${hour}ª ora" else "Uscita anticipata"
        }
}

fun parseAbsence(c: JsonObject): Absence {
    val code = c.str("evtCode") ?: "ABA0"
    val date = Dates.parseDate(c.str("evtDate")) ?: distantPast.toLocalDate()
    return Absence(
        id = c.int("evtId") ?: StableID.make(code, Dates.dayKey(date)), code = code, date = date,
        hour = c.int("evtHPos")?.toInt(), value = c.int("evtValue"), isJustified = c.bool("isJustified"),
        justificationReason = c.str("justifReasonDesc").nilIfEmpty(),
    )
}

fun parseAbsencesResponse(c: JsonElement) = (c as? JsonObject).list("events", ::parseAbsence)

// MARK: - Bacheca

data class NoticeAttachment(val fileName: String, val number: Int)

data class Notice(
    val pubId: Long, val publishedAt: LocalDateTime?, val isRead: Boolean, val eventCode: String, val contentId: Long,
    val validFrom: LocalDate?, val validTo: LocalDate?, val isValid: Boolean, val status: String, val title: String,
    val category: String, val hasChanged: Boolean, val hasAttachments: Boolean, val needsJoin: Boolean,
    val needsReply: Boolean, val needsFile: Boolean, val needsSign: Boolean, val attachments: List<NoticeAttachment>,
) {
    val id: Long get() = pubId
    val requiresAction get() = needsJoin || needsSign || needsReply
    val isDeleted get() = status == "deleted"
}

fun parseNotice(c: JsonObject) = Notice(
    pubId = c.int("pubId") ?: 0, publishedAt = Dates.parseDateTime(c.str("pubDT")), isRead = c.bool("readStatus"),
    eventCode = c.str("evtCode") ?: "CF", contentId = c.int("cntId") ?: 0, validFrom = Dates.parseDate(c.str("cntValidFrom")),
    validTo = Dates.parseDate(c.str("cntValidTo")), isValid = c.bool("cntValidInRange"), status = c.str("cntStatus") ?: "",
    title = (c.str("cntTitle") ?: "Comunicazione").trim(), category = (c.str("cntCategory") ?: "Altro").nilIfEmpty() ?: "Altro",
    hasChanged = c.bool("cntHasChanged"), hasAttachments = c.bool("cntHasAttach"), needsJoin = c.bool("needJoin"),
    needsReply = c.bool("needReply"), needsFile = c.bool("needFile"), needsSign = c.bool("needSign"),
    attachments = c.list("attachments") { NoticeAttachment(it.str("fileName") ?: "Allegato", (it.int("attachNum") ?: 1).toInt()) },
)

fun parseNoticeboardResponse(c: JsonElement) = (c as? JsonObject).list("items", ::parseNotice)

data class NoticeDetail(val title: String?, val text: String?, val joined: Boolean, val signed: Boolean, val replyText: String?)

fun parseNoticeDetail(c: JsonElement): NoticeDetail {
    val o = c as? JsonObject
    val item = o.child("item")
    val reply = o.child("reply")
    return NoticeDetail(
        title = item.str("title"), text = item.str("text"), joined = reply?.bool("replJoin") ?: false,
        signed = reply?.bool("replSign") ?: false, replyText = reply.str("replText").nilIfEmpty(),
    )
}

// MARK: - Note disciplinari

enum class NoteCategory(val code: String, val title: String, val singular: String, val icon: String) {
    NTTE("NTTE", "Annotazioni", "Annotazione", "stickyNote"),
    NTCL("NTCL", "Note disciplinari", "Nota disciplinare", "messageAlert"),
    NTWN("NTWN", "Richiami", "Richiamo", "hand"),
    NTST("NTST", "Sanzioni", "Sanzione disciplinare", "octagonAlert");

    companion object {
        fun of(code: String?): NoteCategory? = entries.firstOrNull { it.code == code }
    }
}

data class Note(
    val id: Long, val text: String, val date: LocalDate, val authorName: String, val isRead: Boolean,
    val category: NoteCategory,
)

private fun parseNoteDTO(c: JsonObject, category: NoteCategory?): Note {
    val text = c.str("evtText") ?: ""
    val date = Dates.parseDate(c.str("evtDate")) ?: distantPast.toLocalDate()
    return Note(
        id = c.int("evtId") ?: StableID.make(text, Dates.dayKey(date)), text = text, date = date,
        authorName = c.str("authorName").nameCased(), isRead = c.bool("readStatus"),
        category = category ?: NoteCategory.of(c.str("evtCode")) ?: NoteCategory.NTTE,
    )
}

/** `notes/all` restituisce un oggetto con una lista per categoria. */
fun parseNotesResponse(c: JsonElement): List<Note> {
    val result = mutableListOf<Note>()
    when (c) {
        is JsonArray -> c.forEach { item -> (item as? JsonObject)?.let { result += parseNoteDTO(it, null) } }
        is JsonObject -> for (category in NoteCategory.entries) result += c.list(category.code) { parseNoteDTO(it, category) }
        else -> {}
    }
    return result.sortedByDescending { it.date }
}

fun parseNoteReadText(c: JsonElement): String? = (c as? JsonObject).child("event").str("evtText")

// MARK: - Materiale didattico

private val fileIcons = mapOf(
    "pdf" to "fileText", "jpg" to "image", "jpeg" to "image", "png" to "image", "heic" to "image", "gif" to "image",
    "ppt" to "presentation", "pptx" to "presentation", "key" to "presentation",
    "xls" to "table", "xlsx" to "table", "csv" to "table", "numbers" to "table",
    "mp3" to "audio", "m4a" to "audio", "wav" to "audio", "mp4" to "film", "mov" to "film", "zip" to "archive", "rar" to "archive",
)

enum class DidacticKind { file, link, text }

data class DidacticContent(val id: Long, val name: String, val objectType: String, val sharedAt: LocalDateTime?) {
    val kind: DidacticKind
        get() = when (objectType.lowercase()) { "link" -> DidacticKind.link; "text" -> DidacticKind.text; else -> DidacticKind.file }
    val icon: String
        get() = when (kind) {
            DidacticKind.link -> "link"
            DidacticKind.text -> "alignLeft"
            DidacticKind.file -> fileIcons[if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""] ?: "file"
        }
}

data class DidacticFolder(val id: Long, val name: String, val lastShare: LocalDateTime?, val contents: List<DidacticContent>)

data class DidacticTeacher(val id: String, val name: String, val folders: List<DidacticFolder>) {
    val lastShare: LocalDateTime? get() = folders.mapNotNull { it.lastShare }.maxOrNull()
}

fun parseDidacticContent(c: JsonObject) = DidacticContent(
    id = c.int("contentId") ?: 0, name = (c.str("contentName") ?: "Contenuto").trim(),
    objectType = c.str("objectType") ?: "file", sharedAt = Dates.parseDateTime(c.str("shareDT")),
)

fun parseDidacticFolder(c: JsonObject): DidacticFolder {
    val raw = (c.str("folderName") ?: "").trim()
    return DidacticFolder(
        id = c.int("folderId") ?: 0, name = if (raw.isEmpty() || raw == "Uncategorized") "Senza cartella" else raw,
        lastShare = Dates.parseDateTime(c.str("lastShareDT")), contents = c.list("contents", ::parseDidacticContent),
    )
}

fun parseDidacticTeacher(c: JsonObject): DidacticTeacher {
    val full = c.str("teacherName") ?: "${c.str("teacherFirstName") ?: ""} ${c.str("teacherLastName") ?: ""}"
    return DidacticTeacher(
        id = c.str("teacherId") ?: StableID.make(full).toString(), name = full.trim().nameCased(),
        folders = c.list("folders", ::parseDidacticFolder),
    )
}

fun parseDidacticsResponse(c: JsonElement): List<DidacticTeacher> {
    val o = c as? JsonObject
    // Il campo si chiama davvero "didacticts" nell'API.
    val primary = o.list("didacticts", ::parseDidacticTeacher)
    return primary.ifEmpty { o.list("didactics", ::parseDidacticTeacher) }
}

// MARK: - Scrutini e documenti

data class SchoolDocument(val documentHash: String, val title: String)
data class SchoolReport(val title: String, val viewLink: String?, val confirmLink: String?)
data class Documents(val documents: List<SchoolDocument>, val schoolReports: List<SchoolReport>)

fun parseDocumentsResponse(c: JsonElement): Documents {
    val o = c as? JsonObject
    return Documents(
        documents = o.list("documents") { SchoolDocument(it.str("hash") ?: "", it.str("desc") ?: "Documento") },
        schoolReports = o.list("schoolReports") {
            SchoolReport(it.str("desc") ?: "Pagella", it.str("viewLink").nilIfEmpty(), it.str("confirmLink").nilIfEmpty())
        },
    )
}

fun parseDocumentAvailable(c: JsonElement): Boolean = (c as? JsonObject).child("document")?.bool("available") ?: false

// MARK: - Libri di testo

data class Schoolbook(
    val id: Long, val isbn: String, val title: String, val subtitle: String?, val volume: String?, val author: String?,
    val publisher: String?, val subject: String, val price: Double?, val toBuy: Boolean, val alreadyOwned: Boolean,
    val inUse: Boolean, val recommended: Boolean,
)

data class SchoolbookCourse(val id: Long, val name: String, val books: List<Schoolbook>)

fun parseSchoolbook(c: JsonObject): Schoolbook {
    val isbn = c.str("isbnCode") ?: ""
    val title = (c.str("title") ?: "Libro").sentenceCased()
    return Schoolbook(
        id = c.int("bookId") ?: StableID.make(isbn, title), isbn = isbn, title = title,
        subtitle = c.str("subheading").nilIfEmpty() ?: c.str("subtitle").nilIfEmpty(), volume = c.str("volume").nilIfEmpty(),
        author = c.str("author").nilIfEmpty()?.nameCased(), publisher = c.str("publisher").nilIfEmpty()?.nameCased(),
        subject = (c.str("subjectDesc") ?: "Altro").sentenceCased(), price = c.dbl("price"), toBuy = c.bool("toBuy"),
        alreadyOwned = c.bool("alreadyOwned"), inUse = c.bool("alreadyInUse"), recommended = c.bool("recommended"),
    )
}

fun parseSchoolbooksResponse(c: JsonElement) = (c as? JsonObject).list("schoolbooks") { course ->
    SchoolbookCourse(course.int("courseId") ?: 0, (course.str("courseDesc") ?: "Corso").sentenceCased(), course.list("books", ::parseSchoolbook))
}
