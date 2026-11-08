package com.bcw.classeviva.data

import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.SeededRandom
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Server finto che risponde come l'API di Classeviva con dati plausibili, generati in modo
 * deterministico rispetto alla data di oggi (gli stessi della versione web, `js/demo.js`).
 */
class DemoTransport(private val yearOffset: Int = 0, private val latency: Boolean = true) : Transport {

    private class DemoSubject(val id: Int, val name: String, val teacher: String, val topics: List<String>)

    private val subjects = listOf(
        DemoSubject(101, "ITALIANO", "BIANCHI LAURA", listOf("Dante, Inferno: canto V", "Il Dolce Stil Novo", "Analisi del testo: Petrarca", "Boccaccio, Decameron", "Tipologia B: testo argomentativo")),
        DemoSubject(102, "LINGUA E CULTURA LATINA", "BIANCHI LAURA", listOf("Cicerone, De Officiis", "Sintassi dei casi: il genitivo", "Lucrezio, De rerum natura")),
        DemoSubject(103, "MATEMATICA", "ROSSI GIORGIO", listOf("Funzioni esponenziali", "Logaritmi e proprietà", "Equazioni logaritmiche", "Goniometria: archi associati", "Esercitazione in vista della verifica")),
        DemoSubject(104, "FISICA", "ROSSI GIORGIO", listOf("Termodinamica: primo principio", "Trasformazioni adiabatiche", "Macchine termiche")),
        DemoSubject(105, "LINGUA E CULTURA STRANIERA (INGLESE)", "SMITH JANE", listOf("The Romantic Age", "William Wordsworth", "Past perfect continuous", "Listening practice")),
        DemoSubject(106, "STORIA", "VERDI ANDREA", listOf("La guerra dei Trent'anni", "L'assolutismo di Luigi XIV", "La rivoluzione inglese")),
        DemoSubject(107, "FILOSOFIA", "VERDI ANDREA", listOf("Cartesio: il metodo", "Spinoza", "Leibniz e le monadi")),
        DemoSubject(108, "SCIENZE NATURALI", "GALLI FRANCESCA", listOf("Il DNA e la replicazione", "Sintesi proteica", "Equilibrio chimico")),
        DemoSubject(109, "DISEGNO E STORIA DELL'ARTE", "MORETTI PAOLO", listOf("Il Rinascimento a Firenze", "Brunelleschi", "Proiezioni assonometriche")),
        DemoSubject(110, "SCIENZE MOTORIE E SPORTIVE", "CONTI MARCO", listOf("Pallavolo: fondamentali", "Test di resistenza", "Atletica: salto in lungo")),
        DemoSubject(111, "RELIGIONE CATTOLICA", "DE LUCA ANNA", listOf("Etica e responsabilità", "Il dialogo interreligioso")),
    )

    /** Orario settimanale (lun–ven), indici in `subjects`. */
    private val timetable = listOf(
        listOf(2, 2, 0, 4, 5, 9),
        listOf(0, 1, 3, 7, 6, 8),
        listOf(2, 4, 0, 0, 7, 10),
        listOf(3, 2, 5, 6, 1, 8),
        listOf(4, 0, 2, 7, 9, 6),
    )

    private data class DemoNotice(val title: String, val category: String, val join: Boolean, val sign: Boolean)

    private val demoNotices = listOf(
        DemoNotice("Circ. 45 – Uscita didattica al Museo della Scienza", "Circolare", true, false),
        DemoNotice("Circ. 41 – Elezioni dei rappresentanti di classe", "Circolare", false, false),
        DemoNotice("Sciopero del comparto scuola", "Comunicazione", false, true),
        DemoNotice("Circ. 38 – Corsi di recupero pomeridiani", "Circolare", false, false),
        DemoNotice("Orario definitivo delle lezioni", "Avviso", false, false),
        DemoNotice("Autorizzazione uscita anticipata", "Modulistica", true, false),
    )

    private class Reply(var joined: Boolean = false, var signed: Boolean = false, var text: String? = null)
    // Le adesioni e le firme della demo restano finché l'app è aperta.
    private val replies = HashMap<Long, Reply>()

    private fun row(day: LocalDate) = timetable[(day.dayOfWeek.value - 1) % 5]
    private fun millis(day: LocalDate) = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun dayIndex(day: LocalDate) = floor(millis(day) / 86_400_000.0).toLong()
    private fun minuteIndex(day: LocalDate) = floor(millis(day) / 60_000.0).toLong()

    private val referenceDate: LocalDate get() = Dates.today().plusYears(yearOffset.toLong())
    private val schoolYear get() = Dates.schoolYear(referenceDate)
    private val firstSchoolDay get() = LocalDate.of(schoolYear.startYear, 9, 14)
    private val lastSchoolDay get() = LocalDate.of(schoolYear.startYear + 1, 6, 8)

    /** Per l'anno corrente i dati arrivano fino a oggi; per l'archivio, tutto l'anno. */
    private val gradesUntil: LocalDate
        get() {
            val today = Dates.today()
            return if (yearOffset < 0) lastSchoolDay else if (today < lastSchoolDay) today else lastSchoolDay
        }

    // MARK: Instradamento

    override suspend fun send(method: String, path: String, body: String?, token: String?): HttpResult {
        if (latency) delay(150 + Random.nextLong(300))
        if (path == "/auth/login") {
            return json(obj(
                "ident" to "S1234567X", "firstName" to "GIULIA", "lastName" to "ESPOSITO", "token" to "demo-token",
                "release" to Dates.isoLocal(LocalDateTime.now()), "expire" to Dates.isoLocal(LocalDateTime.now().plusMinutes(90)),
            ))
        }
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.size < 3 || parts[0] != "students") return notFound()
        val route = parts.drop(2)
        fun at(i: Int) = route.getOrNull(i)
        return when (route[0]) {
            "card", "cards" -> json(obj("card" to card()))
            "grades", "grades2", "grades2324" -> json(obj("grades" to JsonArray(grades())))
            "periods" -> json(obj("periods" to periods()))
            "subjects" -> json(obj("subjects" to subjectsJSON()))
            "agenda" -> {
                val from = Dates.parseApiDate(at(2))
                val to = Dates.parseApiDate(at(3))
                if (from == null || to == null) notFound() else json(obj("agenda" to JsonArray(agenda(from, to))))
            }
            "lessons" -> {
                val today = Dates.today()
                json(obj("lessons" to JsonArray(lessons(Dates.parseApiDate(at(1)) ?: today, Dates.parseApiDate(at(2)) ?: today))))
            }
            "absences" -> json(obj("events" to JsonArray(absences())))
            "noticeboard" -> when (at(1)) {
                "read" -> json(noticeDetail(at(3)?.toLongOrNull() ?: 0, body))
                "attach" -> {
                    // Lo stesso nome dell'elenco degli allegati, anche nel visore.
                    val index = max(0L, (at(3)?.toLongOrNull()?.takeIf { it != 0L } ?: 5000L) - 5000)
                    file(DemoPdf.make("Circolare", "Allegato dimostrativo della comunicazione."), "circolare_${index + 1}.pdf", "application/pdf")
                }
                else -> json(obj("items" to JsonArray(notices())))
            }
            "notes" -> {
                if (route.size >= 4 && route[2] == "read") {
                    val id = route[3].toLongOrNull() ?: 0
                    val note = notesList().firstOrNull { (it["evtId"] as JsonPrimitive).content.toLong() == id }
                    json(obj("event" to obj("evtId" to id, "evtText" to (note?.get("evtText")?.jsonPrimitive?.content ?: ""), "readStatus" to true)))
                } else json(notes())
            }
            "didactics" -> {
                if (at(1) == "item") {
                    val id = at(2)?.toLongOrNull() ?: 0
                    when (id % 10) {
                        2L -> json(obj("item" to obj("link" to "https://it.wikipedia.org/wiki/Logaritmo")))
                        3L -> json(obj("item" to obj("text" to "Ripassare i capitoli 4 e 5 del libro di testo e svolgere gli esercizi di fine capitolo.")))
                        else -> file(DemoPdf.make("Dispensa", "Materiale didattico dimostrativo di BCW."), "dispensa-$id.pdf", "application/pdf")
                    }
                } else json(obj("didacticts" to JsonArray(didactics())))
            }
            "documents" -> when (at(1)) {
                "check" -> json(obj("document" to obj("available" to true)))
                "read" -> file(DemoPdf.make("Pagella", "Documento di valutazione dimostrativo."), "pagella.pdf", "application/pdf")
                else -> json(documents())
            }
            "calendar" -> json(obj("calendar" to JsonArray(calendarDays())))
            "schoolbooks" -> json(obj("schoolbooks" to schoolbooks()))
            else -> notFound()
        }
    }

    // MARK: Generatori

    private fun card() = obj(
        "ident" to "S1234567X", "usrType" to "S", "usrId" to 1234567, "firstName" to "GIULIA", "lastName" to "ESPOSITO",
        "birthDate" to "2009-03-21", "fiscalCode" to "SPSGLI09C61F205X", "schCode" to "MIPS00000",
        "schName" to "LICEO SCIENTIFICO STATALE", "schDedication" to "ALESSANDRO VOLTA", "schCity" to "MILANO",
        "schProv" to "MI", "miurSchoolCode" to "MIPS00000X", "miurDivisionCode" to "4B",
    )

    private fun periods(): JsonArray {
        val y = schoolYear.startYear
        return JsonArray(listOf(
            obj("periodCode" to "Q1", "periodPos" to 1, "periodDesc" to "TRIMESTRE", "isFinal" to false, "dateStart" to "$y-09-01", "dateEnd" to "$y-12-22"),
            obj("periodCode" to "Q3", "periodPos" to 3, "periodDesc" to "PENTAMESTRE", "isFinal" to true, "dateStart" to "$y-12-23", "dateEnd" to "${y + 1}-06-30"),
        ))
    }

    private fun subjectsJSON() = JsonArray(subjects.mapIndexed { index, s ->
        obj("id" to s.id, "description" to s.name, "order" to index + 1,
            "teachers" to JsonArray(listOf(obj("teacherId" to "T${s.id}", "teacherName" to s.teacher))))
    })

    private fun schoolDays(from: LocalDate, to: LocalDate): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = if (from > firstSchoolDay) from else firstSchoolDay
        val end = if (to < lastSchoolDay) to else lastSchoolDay
        while (!day.isAfter(end)) {
            if (!Dates.isWeekend(day) && !isHoliday(day)) days += day
            day = day.plusDays(1)
        }
        return days
    }

    private fun isHoliday(day: LocalDate): Boolean {
        val m = day.monthValue
        val d = day.dayOfMonth
        if ((m == 11 && d == 1) || (m == 12 && d == 8) || (m == 4 && d == 25) || (m == 5 && d == 1) || (m == 6 && d == 2)) return true
        if (m == 12 && d >= 23) return true
        if (m == 1 && d <= 6) return true
        return false
    }

    private fun grades(): List<JsonObject> {
        val rng = SeededRandom(schoolYear.startYear.toLong())
        val result = mutableListOf<JsonObject>()
        var id = 900_000
        val trimesterEnd = LocalDate.of(schoolYear.startYear, 12, 22)
        val bases = listOf(7.5, 6.5, 6.8, 7.0, 8.0, 7.2, 7.8, 7.4, 8.3, 8.8, 0.0)
        for (day in schoolDays(firstSchoolDay.plusDays(3), gradesUntil)) {
            val count = rng.next(3) + 1
            for (i in 0 until count) {
                val sIndex = row(day)[rng.next(6)]
                val s = subjects[sIndex]
                if (s.id == 111) continue
                val raw = min(10.0, max(3.5, bases[sIndex] + (rng.next(9) - 4) * 0.5))
                val value = Math.round(raw * 4) / 4.0
                val isOral = rng.next(3) == 0
                val code = if (s.id == 110) "GRV2" else if (isOral) "GRV1" else "GRV0"
                val isBlue = rng.next(18) == 0
                val isFirst = !day.isAfter(trimesterEnd)
                id += 1
                val notes = if (rng.next(3) == 0) s.topics[rng.next(s.topics.size)] else ""
                result += obj(
                    "subjectId" to s.id, "subjectCode" to "", "subjectDesc" to s.name, "evtId" to id, "evtCode" to code,
                    "evtDate" to Dates.dayKey(day), "decimalValue" to value, "displayValue" to display(value), "displaPos" to 1,
                    "notesForFamily" to notes, "color" to if (isBlue) "blue" else if (value >= 6) "green" else "red",
                    "canceled" to false, "underlined" to false, "periodPos" to if (isFirst) 1 else 3,
                    "periodDesc" to if (isFirst) "TRIMESTRE" else "PENTAMESTRE", "componentPos" to 1,
                    "componentDesc" to when (code) { "GRV0" -> "Scritto"; "GRV1" -> "Orale"; else -> "Pratico" },
                    "weightFactor" to 1.0, "noAverage" to isBlue, "teacherName" to s.teacher,
                )
            }
        }
        return result.reversed()
    }

    private fun agenda(from: LocalDate, to: LocalDate): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        for (day in schoolDays(from, to)) {
            val rng = SeededRandom(dayIndex(day))
            val r = row(day)
            val homeworkCount = rng.next(3)
            for (i in 0 until homeworkCount) {
                val s = subjects[r[rng.next(r.size)]]
                val tasks = listOf(
                    "Esercizi da pag. ${rng.next(200) + 20} n. ${rng.next(30) + 1}–${rng.next(20) + 31}",
                    "Studiare: ${s.topics[rng.next(s.topics.size)]}",
                    "Leggere e riassumere il capitolo ${rng.next(12) + 1}",
                    "Ripassare gli appunti della lezione",
                )
                result += event(minuteIndex(day) + i, "AGHW", day, 8, tasks[rng.next(tasks.size)], s)
            }
            if (rng.next(5) == 0) {
                val s = subjects[r[rng.next(r.size)]]
                val kinds = listOf("Verifica scritta: ${s.topics[rng.next(s.topics.size)]}", "Interrogazioni programmate", "Compito in classe")
                val hour = 9 + rng.next(3)
                result += event(minuteIndex(day) + 10, "AGNT", day, hour, kinds[rng.next(kinds.size)], s)
            }
            if (rng.next(9) == 0) {
                val events = listOf("Uscita didattica al Museo della Scienza", "Assemblea di classe",
                    "Incontro di orientamento universitario", "Consiglio di classe (solo docenti)")
                result += event(minuteIndex(day) + 20, "AGNT", day, 11, events[rng.next(events.size)], null)
            }
        }
        return result
    }

    private fun lessons(from: LocalDate, to: LocalDate): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val today = Dates.today()
        for (day in schoolDays(from, if (to < today) to else today)) {
            val rng = SeededRandom(dayIndex(day) + 7)
            val r = row(day)
            var hour = 1
            var index = 0
            while (index < r.size) {
                var duration = 1
                while (index + duration < r.size && r[index + duration] == r[index]) duration += 1
                val s = subjects[r[index]]
                val type = if (rng.next(4) == 0) "Esercitazione" else "Lezione"
                val topic = s.topics[rng.next(s.topics.size)]
                result += obj(
                    "evtId" to minuteIndex(day) + hour, "evtDate" to Dates.dayKey(day), "evtCode" to "LSF0", "evtHPos" to hour,
                    "evtDuration" to duration, "classDesc" to "4B LICEO SCIENTIFICO", "authorName" to s.teacher,
                    "subjectId" to s.id, "subjectCode" to "", "subjectDesc" to s.name, "lessonType" to type, "lessonArg" to topic,
                )
                hour += duration
                index += duration
            }
        }
        return result
    }

    private fun absences(): List<JsonObject> {
        val days = schoolDays(firstSchoolDay, gradesUntil)
        if (days.size <= 6) return emptyList()
        val rng = SeededRandom(42L + schoolYear.startYear)
        val picks = LinkedHashSet<Int>()
        val count = max(3, days.size / 12)
        repeat(count) { picks += rng.next(days.size) }
        val sorted = picks.sorted()
        val codes = listOf("ABA0", "ABR0", "ABU0", "ABA0", "ABR1")
        return sorted.mapIndexed { n, index ->
            // L'ultimo evento è sempre un ritardo da giustificare, per mostrare anche quel caso.
            val isLast = n == sorted.size - 1
            val code = if (isLast) "ABR0" else codes[n % 5]
            val justified = !isLast && (index < days.size - 4 || n % 2 == 0)
            val hour: Int? = if (code == "ABU0") 5 else if (code == "ABA0") null else 2
            obj(
                "evtId" to 700_000 + n, "evtCode" to code, "evtDate" to Dates.dayKey(days[index]), "evtHPos" to hour,
                "evtValue" to 1, "isJustified" to justified, "justifReasonCode" to if (justified) "A" else "",
                "justifReasonDesc" to if (justified) "Motivi di salute" else "", "hoursAbsence" to JsonArray(emptyList()),
            )
        }
    }

    private fun notices(): List<JsonObject> = demoNotices.mapIndexed { index, n ->
        val date = LocalDateTime.now().minusDays(index * 4L + 1)
        obj(
            "pubId" to 5000 + index, "pubDT" to Dates.isoLocal(date), "readStatus" to (index > 2), "evtCode" to "CF",
            "cntId" to 9000 + index, "cntValidFrom" to Dates.dayKey(date.toLocalDate()),
            "cntValidTo" to Dates.dayKey(date.toLocalDate().plusDays(60)), "cntValidInRange" to true, "cntStatus" to "active",
            "cntTitle" to n.title, "cntCategory" to n.category, "cntHasChanged" to false, "cntHasAttach" to (index % 2 == 0),
            "needJoin" to n.join, "needReply" to false, "needFile" to false, "needSign" to n.sign, "evento_id" to index.toString(),
            "attachments" to JsonArray(if (index % 2 == 0) listOf(obj("fileName" to "circolare_${index + 1}.pdf", "attachNum" to 1)) else emptyList()),
        )
    }

    private fun noticeDetail(pubId: Long, body: String?): JsonObject {
        val index = max(0, min(demoNotices.size - 1, (pubId - 5000).toInt()))
        val saved = replies.getOrPut(pubId) { Reply() }
        if (!body.isNullOrBlank()) {
            runCatching {
                val payload = com.bcw.classeviva.util.parseJson(body) as JsonObject
                if ((payload["join"] as? JsonPrimitive)?.booleanOrNull == true) saved.joined = true
                if ((payload["sign"] as? JsonPrimitive)?.booleanOrNull == true) saved.signed = true
                (payload["text"] as? JsonPrimitive)?.takeIf { it.isString }?.let { saved.text = it.content }
            }
        }
        return obj(
            "item" to obj(
                "title" to demoNotices[index].title,
                "text" to "Si comunica alle famiglie e agli studenti quanto segue.\n\nLe attività si svolgeranno secondo il calendario allegato. Si raccomanda la puntualità.\n\nIl Dirigente Scolastico",
            ),
            "reply" to obj("replJoin" to saved.joined, "replSign" to saved.signed, "replText" to saved.text),
        )
    }

    private fun notesList(): List<JsonObject> {
        val today = Dates.today()
        return listOf(
            obj("evtId" to 801, "evtCode" to "NTTE", "evtText" to "Lo studente ha dimenticato il materiale di disegno.",
                "evtDate" to Dates.dayKey(today.minusDays(6)), "authorName" to "MORETTI PAOLO", "readStatus" to true),
            obj("evtId" to 802, "evtCode" to "NTTE", "evtText" to "Ottima partecipazione al dibattito in classe.",
                "evtDate" to Dates.dayKey(today.minusDays(11)), "authorName" to "VERDI ANDREA", "readStatus" to false),
            obj("evtId" to 803, "evtCode" to "NTCL", "evtText" to "Uso del cellulare durante la lezione.",
                "evtDate" to Dates.dayKey(today.minusDays(15)), "authorName" to "ROSSI GIORGIO", "readStatus" to false),
        )
    }

    private fun notes(): JsonObject {
        val grouped = linkedMapOf<String, MutableList<JsonObject>>("NTTE" to mutableListOf(), "NTCL" to mutableListOf(), "NTWN" to mutableListOf(), "NTST" to mutableListOf())
        for (note in notesList()) grouped[(note["evtCode"] as JsonPrimitive).content]!!.add(note)
        return JsonObject(grouped.mapValues { JsonArray(it.value) })
    }

    private fun didactics(): List<JsonObject> {
        val data = listOf(
            "ROSSI GIORGIO" to listOf("Logaritmi" to listOf("Teoria dei logaritmi.pdf", "Video spiegazione", "Compiti per le vacanze"),
                "Fisica – Termodinamica" to listOf("Formulario termodinamica.pdf", "Esercizi svolti.pdf")),
            "BIANCHI LAURA" to listOf("Dante" to listOf("Inferno – canti scelti.pdf", "Schema canto V.pptx"),
                "Uncategorized" to listOf("Griglia di valutazione.pdf")),
            "SMITH JANE" to listOf("Romanticism" to listOf("Wordsworth poems.pdf", "BBC documentary", "Reading list")),
        )
        var contentId = 40_000
        val now = LocalDateTime.now()
        return data.mapIndexed { tIndex, (teacher, folders) ->
            val parts = teacher.split(' ')
            obj(
                "teacherId" to "D$tIndex", "teacherName" to teacher, "teacherFirstName" to parts.last(), "teacherLastName" to parts.first(),
                "folders" to JsonArray(folders.mapIndexed { fIndex, (folder, items) ->
                    obj(
                        "folderId" to tIndex * 10 + fIndex, "folderName" to folder,
                        "lastShareDT" to Dates.isoLocal(now.minusDays((tIndex * 3 + fIndex * 5 + 1).toLong())),
                        "contents" to JsonArray(items.mapIndexed { cIndex, name ->
                            contentId += 10
                            val isLink = name.contains("Video") || name.contains("BBC")
                            val isText = !name.contains('.') && !isLink
                            val id = contentId + if (isLink) 2 else if (isText) 3 else 1
                            obj(
                                "contentId" to id, "contentName" to name, "objectId" to id,
                                "objectType" to if (isLink) "link" else if (isText) "text" else "file",
                                "shareDT" to Dates.isoLocal(now.minusDays((tIndex * 3 + fIndex * 5 + cIndex + 1).toLong())),
                            )
                        }),
                    )
                }),
            )
        }
    }

    private fun documents(): JsonObject {
        val docs = mutableListOf<JsonObject>()
        if (yearOffset < 0 || Dates.today() > LocalDate.of(schoolYear.startYear + 1, 1, 20)) {
            docs += obj("hash" to "demo-trimestre", "desc" to "Pagella trimestre")
        }
        if (yearOffset < 0) {
            docs += obj("hash" to "demo-finale", "desc" to "Pagella finale")
            docs += obj("hash" to "demo-certificato", "desc" to "Certificato delle competenze")
        }
        return obj("documents" to JsonArray(docs), "schoolReports" to JsonArray(emptyList()))
    }

    private fun calendarDays(): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var day = schoolYear.start
        while (!day.isAfter(schoolYear.end)) {
            val status = when {
                day < firstSchoolDay || day > lastSchoolDay -> "NW"
                Dates.isWeekend(day) -> "ND"
                isHoliday(day) -> "HD"
                else -> "SD"
            }
            result += obj("dayDate" to Dates.dayKey(day), "dayOfWeek" to (day.dayOfWeek.value % 7) + 1, "dayStatus" to status)
            day = day.plusDays(1)
        }
        return result
    }

    private fun schoolbooks(): JsonArray {
        fun book(id: Int, isbn: String, title: String, volume: String?, author: String, publisher: String, subject: String, price: Double, toBuy: Boolean) =
            obj("bookId" to id, "isbnCode" to isbn, "title" to title, "volume" to (volume ?: ""), "author" to author,
                "publisher" to publisher, "subjectDesc" to subject, "price" to price, "toBuy" to toBuy,
                "alreadyOwned" to !toBuy, "alreadyInUse" to !toBuy, "recommended" to false)
        return JsonArray(listOf(obj(
            "courseId" to 1, "courseDesc" to "LICEO SCIENTIFICO – CLASSE 4ª",
            "books" to JsonArray(listOf(
                book(1, "9788808220851", "MATEMATICA.BLU 2.0", "Volume 4", "Bergamini Massimo", "Zanichelli", "MATEMATICA", 34.9, false),
                book(2, "9788808520470", "L'AMALDI PER I LICEI SCIENTIFICI", "Volume 2", "Amaldi Ugo", "Zanichelli", "FISICA", 38.5, false),
                book(3, "9788839536143", "PERFORMER HERITAGE", "Volume 1", "Spiazzi Marina", "Zanichelli", "INGLESE", 31.2, true),
                book(4, "9788822173843", "LA DIVINA COMMEDIA", null, "Alighieri Dante", "Le Monnier", "ITALIANO", 24.0, false),
            )),
        )))
    }

    private fun event(id: Long, code: String, day: LocalDate, hour: Int, notes: String, subject: DemoSubject?): JsonObject {
        val begin = day.atTime(hour, 0)
        val pairs = mutableListOf<Pair<String, Any?>>(
            "evtId" to id, "evtCode" to code, "evtDatetimeBegin" to Dates.isoLocal(begin),
            "evtDatetimeEnd" to Dates.isoLocal(begin.plusHours(1)), "isFullDay" to false, "notes" to notes,
            "authorName" to (subject?.teacher ?: "SEGRETERIA DIDATTICA"), "classDesc" to "4B LICEO SCIENTIFICO",
        )
        if (subject != null) {
            pairs += "subjectId" to subject.id
            pairs += "subjectDesc" to subject.name
        }
        return obj(*pairs.toTypedArray())
    }

    private fun display(value: Double): String {
        val whole = floor(value).toInt()
        return when (value - whole) {
            0.25 -> "$whole+"
            0.5 -> "$whole½"
            0.75 -> "${whole + 1}-"
            else -> "$whole"
        }
    }

    private fun json(o: JsonElement) = HttpResult(200, "application/json", null, o.toString().toByteArray())
    private fun file(data: ByteArray, name: String, type: String) = HttpResult(200, type, name, data)
    private fun notFound() = HttpResult(404, "application/json", null, obj("statusCode" to 404, "message" to "Non disponibile nella demo").toString().toByteArray())
}

/** Oggetto JSON da coppie chiave-valore. */
fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(linkedMapOf<String, JsonElement>().apply {
    for ((k, v) in pairs) put(k, toJson(v))
})

fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is List<*> -> JsonArray(v.map { toJson(it) })
    else -> JsonPrimitive(v.toString())
}

/** Piccolo generatore di PDF di una pagina, per i documenti della demo. */
object DemoPdf {
    private fun latin1(text: String) = text.map { ch -> if (ch.code < 256) ch else if (ch == '–') '-' else '?' }.joinToString("")
    private fun escape(text: String) = latin1(text).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun wrap(text: String, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                if ("$line $word".trim().length > maxChars) {
                    lines += line
                    line = word
                } else line = "$line $word".trim()
            }
            lines += line
        }
        return lines
    }

    fun make(title: String, body: String): ByteArray {
        val bodyLines = wrap(body, 80)
        val content = StringBuilder("BT /F1 28 Tf 56 760 Td (${escape(title)}) Tj ET\n")
        bodyLines.forEachIndexed { i, line -> content.append("BT /F2 14 Tf 56 ${712 - i * 20} Td (${escape(line)}) Tj ET\n") }
        content.append("0.5 g BT /F2 10 Tf 56 52 Td (${escape("BCW · Modalità demo")}) Tj ET\n")
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
            "<< /Length ${content.length} >>\nstream\n${content}endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        )
        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { i, o ->
            offsets += pdf.length
            pdf.append("${i + 1} 0 obj\n$o\nendobj\n")
        }
        val xref = pdf.length
        pdf.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (offset in offsets) pdf.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset))
        pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return ByteArray(pdf.length) { (pdf[it].code and 0xFF).toByte() }
    }
}
