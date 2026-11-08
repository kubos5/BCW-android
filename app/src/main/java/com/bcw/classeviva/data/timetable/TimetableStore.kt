package com.bcw.classeviva.data.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.newUUID
import com.bcw.classeviva.util.normalized
import com.bcw.classeviva.util.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

/** Bozza generata dalle lezioni, in anteprima finché non viene applicata o scartata. */
data class TimetableDraft(val entries: List<TimetableEntry>, val settings: GenerationSettings, val analyzedDays: Int, val from: LocalDate? = null, val to: LocalDate? = null)

/** Dove vale una nota, un file o una verifica: un solo giorno, ogni settimana o tutti gli eventi uguali. */
sealed class EditScope {
    data class Day(val day: LocalDate) : EditScope()
    object EveryWeek : EditScope()
    object AllSameName : EditScope()
}

sealed class HiddenReason(val description: String) {
    object Manual : HiddenReason("Nascosta a mano")
    class Rule(val rule: String) : HiddenReason("Nascosta dalla regola «$rule»")
}

data class EntryBadges(val isTest: Boolean, val isRegisterTest: Boolean, val hasNote: Boolean, val attachmentCount: Int)

data class DayContent(
    val day: LocalDate,
    val entries: List<TimetableEntry>,
    val layout: TimetableLayout,
    val isDraft: Boolean,
    val registerTests: Map<String, List<AgendaEvent>>,
    val hiddenTests: Map<String, List<AgendaEvent>>,
    val unmatchedTests: List<AgendaEvent>,
    val badges: Map<String, EntryBadges>,
) {
    val testCount: Int get() = badges.values.count { it.isTest } + unmatchedTests.size
}

/**
 * L'orario di un account: un JSON per proprietario in `files/timetable`, i file allegati in
 * `files/timetable-files/<proprietario>` (cancellati quando nessuno li usa).
 */
class TimetableStore(private val root: File) {
    var owner: String? by mutableStateOf(null)
        private set
    var timetable: Timetable by mutableStateOf(Timetable())
        private set
    var draft: TimetableDraft? by mutableStateOf(null)
        private set
    var saveError: String? by mutableStateOf(null)
        private set

    /** Avvisa la sincronizzazione dopo ogni salvataggio. */
    var onSaved: ((String) -> Unit)? = null
    /** Scarica dal server un file mancante (account BCW). */
    var remoteFile: (suspend (owner: String, storedName: String) -> ByteArray?)? = null

    private var byWeekday: Map<Int, List<TimetableEntry>> = emptyMap()

    companion object {
        private fun safeName(owner: String) = owner.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "default" }
    }

    private fun documentFile(owner: String) = File(root, "timetable/${safeName(owner)}.json")
    private fun filesDir(owner: String) = File(root, "timetable-files/${safeName(owner)}")
    fun file(owner: String, storedName: String) = File(filesDir(owner), storedName)

    // MARK: Salvataggio

    /** Carica l'orario di un account (`null` = nessuna sessione). */
    fun load(newOwner: String?) {
        owner = newOwner
        draft = null
        saveError = null
        var loaded = Timetable()
        if (newOwner != null) {
            runCatching {
                val f = documentFile(newOwner)
                if (f.exists()) parseJson(f.readText())?.let { loaded = decodeTimetable(it) }
            }
        }
        timetable = loaded
        reindex()
        removeUnreferencedFiles()
    }

    /** Cancella del tutto l'orario di un proprietario (es. quello della demo). */
    fun removeData(owner: String) {
        runCatching { documentFile(owner).delete() }
        runCatching { filesDir(owner).deleteRecursively() }
    }

    fun storedText(owner: String): String? = runCatching { documentFile(owner).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Copia orario e file a un proprietario che non ha ancora un orario (account abbinato a BCW). */
    fun copyData(from: String, to: String) {
        val text = storedText(from) ?: return
        if (storedText(to) != null) return
        runCatching {
            val src = filesDir(from)
            if (src.exists()) src.copyRecursively(filesDir(to), overwrite = true)
            documentFile(to).parentFile?.mkdirs()
            documentFile(to).writeText(text)
        }
    }

    private fun write(owner: String, text: String) {
        val f = documentFile(owner)
        f.parentFile?.mkdirs()
        val temp = File(f.parentFile, f.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(f)) {
            f.delete()
            temp.renameTo(f)
        }
    }

    private fun save() {
        val o = owner ?: return
        try {
            write(o, encodeTimetable(timetable).toString())
            saveError = null
        } catch (e: Exception) {
            saveError = e.message ?: "Impossibile salvare l'orario."
            return
        }
        onSaved?.invoke(o)
    }

    /** Sostituisce l'orario con quello arrivato dal server (senza avvisare `onSaved`). Restituisce il JSON salvato. */
    fun applyRemote(owner: String, data: JsonElement): String? {
        val decoded = runCatching { decodeTimetable(data) }.getOrNull() ?: return storedText(owner)
        val text = encodeTimetable(decoded).toString()
        runCatching { write(owner, text) }
        if (owner == this.owner) {
            timetable = decoded
            reindex()
            removeUnreferencedFiles()
        }
        return text
    }

    private fun reindex() {
        byWeekday = timetable.entries.groupBy { it.weekday }.mapValues { (_, list) -> list.sortedWith(entryOrder) }
    }

    private fun mutate(change: (Timetable) -> Timetable) {
        timetable = change(timetable)
        reindex()
        save()
    }

    fun removeUnreferencedFiles() {
        val o = owner ?: return
        val referenced = timetable.referencedFiles
        runCatching {
            filesDir(o).listFiles()?.forEach { if (it.name !in referenced) it.delete() }
        }
    }

    // MARK: Lettura

    val isEmpty: Boolean get() = timetable.entries.isEmpty()
    val alignment: Alignment get() = timetable.alignment

    fun entries(weekday: Int, includingDraft: Boolean = true): List<TimetableEntry> {
        val d = draft
        if (includingDraft && d != null) return d.entries.filter { it.weekday == weekday }.sortedWith(entryOrder)
        // Legge `timetable` così le viste si aggiornano quando cambia.
        timetable
        return byWeekday[weekday] ?: emptyList()
    }

    fun entriesOn(day: LocalDate, includingDraft: Boolean = true) = entries(Weekday.of(day), includingDraft)

    fun entry(id: String): TimetableEntry? = timetable.entries.firstOrNull { it.id == id } ?: draft?.entries?.firstOrNull { it.id == id }

    fun isDraftEntry(id: String): Boolean = draft?.entries?.any { it.id == id } == true && timetable.entries.none { it.id == id }

    fun occurrence(id: String, day: LocalDate): Occurrence = timetable.occurrences[occurrenceKey(id, day)] ?: Occurrence()

    fun isManualTest(entry: TimetableEntry, day: LocalDate?): Boolean {
        if (day == null) return entry.isWeeklyTest
        return occurrence(entry.id, day).isTest ?: entry.isWeeklyTest
    }

    fun sameNameEntries(entry: TimetableEntry): List<TimetableEntry> {
        val key = entry.matchKey
        if (key.isEmpty()) return emptyList()
        return timetable.entries.filter { it.id != entry.id && it.kind == entry.kind && it.matchKey == key }
    }

    val knownTitles: List<String>
        get() {
            val seen = HashSet<String>()
            val titles = mutableListOf<String>()
            for (e in timetable.entries) {
                if (e.isBreak) continue
                val key = normalized(e.displayTitle)
                if (seen.add(key)) titles += e.displayTitle
            }
            return titles.sorted()
        }

    val unalignedEntries: List<TimetableEntry> get() = timetable.entries.filter { !it.isAutoAligned }

    fun palette(id: String): Palette? = builtInPalettes.firstOrNull { it.id == id } ?: timetable.customPalettes.firstOrNull { it.id == id }
    val allPalettes: List<Palette> get() = builtInPalettes + timetable.customPalettes

    fun tint(entry: TimetableEntry): EntryTint = entryTint(entry, ::palette)

    // MARK: Modifiche

    fun add(entries: List<TimetableEntry>) = mutate { it.copy(entries = it.entries + entries) }

    /** Salva un evento; con `original` copia i campi cambiati negli eventi con lo stesso nome. */
    fun saveEntry(entry: TimetableEntry, original: TimetableEntry? = null) = mutate { t ->
        var list = t.entries
        if (original != null) {
            val key = original.matchKey
            list = list.map { other ->
                if (other.id == entry.id || other.kind != original.kind || other.matchKey != key) other
                else {
                    var o = other
                    if (original.title != entry.title) o = o.copy(title = entry.title)
                    if (original.subjectId != entry.subjectId) o = o.copy(subjectId = entry.subjectId)
                    if (original.teacher != entry.teacher) o = o.copy(teacher = entry.teacher)
                    if (original.room != entry.room) o = o.copy(room = entry.room)
                    if (original.note != entry.note) o = o.copy(note = entry.note)
                    if (original.liveActivity != entry.liveActivity) o = o.copy(liveActivity = entry.liveActivity)
                    if (original.color != entry.color && entry.color !is EntryColor.Series) o = o.copy(color = entry.color)
                    o
                }
            }
        }
        val index = list.indexOfFirst { it.id == entry.id }
        t.copy(entries = if (index >= 0) list.toMutableList().also { it[index] = entry } else list + entry)
    }

    fun delete(ids: Collection<String>) {
        val set = ids.toSet()
        mutate { t ->
            t.copy(
                entries = t.entries.filter { it.id !in set },
                occurrences = t.occurrences.filterKeys { key -> set.none { key.startsWith(it) } },
            )
        }
        removeUnreferencedFiles()
    }

    fun move(id: String, weekday: Int?, start: Int, end: Int) {
        val entry = timetable.entries.firstOrNull { it.id == id } ?: return
        val w = weekday ?: entry.weekday
        if (entry.start == start && entry.end == end && entry.weekday == w) return
        mutate { t -> t.copy(entries = t.entries.map { if (it.id == id) it.copy(start = start, end = end, weekday = w) else it }) }
    }

    /** Sposta più eventi insieme (minuti per id); `freeAlignment` passa all'allineamento libero. */
    fun shift(minutes: Map<String, Int>, freeAlignment: Boolean = false) {
        if (minutes.values.none { it != 0 }) return
        mutate { t ->
            t.copy(
                alignment = if (freeAlignment) Alignment.free else t.alignment,
                entries = t.entries.map { e -> minutes[e.id]?.takeIf { it != 0 }?.let { e.shifted(it) } ?: e },
            )
        }
    }

    /** Copia di un evento subito dopo l'originale. */
    fun duplicate(entry: TimetableEntry): TimetableEntry {
        val length = entry.duration
        val start = minOf(entry.end, 1440 - length)
        val copy = entry.copy(id = newUUID(), start = start, end = start + length, color = if (entry.color is EntryColor.Series) null else entry.color)
        add(listOf(copy))
        return copy
    }

    fun deleteAll() {
        mutate { it.copy(entries = emptyList(), occurrences = emptyMap(), hiddenTests = emptyList(), shownTests = emptyList()) }
        removeUnreferencedFiles()
    }

    // MARK: Note, file e verifiche

    private fun targets(entry: TimetableEntry, scope: EditScope): Set<String> =
        if (scope is EditScope.AllSameName) (listOf(entry.id) + sameNameEntries(entry).map { it.id }).toSet() else setOf(entry.id)

    private fun updatingOccurrence(t: Timetable, id: String, day: LocalDate, change: (Occurrence) -> Occurrence): Timetable {
        val key = occurrenceKey(id, day)
        val info = change(t.occurrences[key] ?: Occurrence())
        val map = LinkedHashMap(t.occurrences)
        if (info.isEmpty) map.remove(key) else map[key] = info
        return t.copy(occurrences = map)
    }

    fun setNote(note: String, entry: TimetableEntry, scope: EditScope) {
        if (scope is EditScope.Day) {
            mutate { t -> updatingOccurrence(t, entry.id, scope.day) { it.copy(note = note) } }
            return
        }
        val ids = targets(entry, scope)
        mutate { t -> t.copy(entries = t.entries.map { if (it.id in ids) it.copy(note = note) else it }) }
    }

    fun setTest(isTest: Boolean, entry: TimetableEntry, scope: EditScope) {
        if (scope is EditScope.Day) {
            val weekly = entry(entry.id)?.isWeeklyTest ?: false
            mutate { t -> updatingOccurrence(t, entry.id, scope.day) { it.copy(isTest = if (isTest == weekly) null else isTest) } }
            return
        }
        val ids = targets(entry, scope)
        mutate { t ->
            val occurrences = LinkedHashMap<String, Occurrence>()
            for ((key, o) in t.occurrences) {
                if (ids.any { key.startsWith(it) } && o.isTest == isTest) {
                    val cleared = o.copy(isTest = null)
                    if (!cleared.isEmpty) occurrences[key] = cleared
                } else occurrences[key] = o
            }
            t.copy(entries = t.entries.map { if (it.id in ids) it.copy(isWeeklyTest = isTest) else it }, occurrences = occurrences)
        }
    }

    suspend fun attach(data: ByteArray, name: String, entry: TimetableEntry, scope: EditScope): Attachment {
        val o = owner ?: throw IllegalStateException("Nessun account attivo.")
        val id = newUUID()
        val ext = fileExtension(name)
        val storedName = if (ext.isNotEmpty()) "$id.$ext" else id
        withContext(Dispatchers.IO) {
            val f = file(o, storedName)
            f.parentFile?.mkdirs()
            f.writeBytes(data)
        }
        val attachment = Attachment(id, name, storedName, data.size.toLong(), Instant.ofEpochSecond(Instant.now().epochSecond))
        if (scope is EditScope.Day) {
            mutate { t -> updatingOccurrence(t, entry.id, scope.day) { it.copy(attachments = it.attachments + attachment) } }
        } else {
            val ids = targets(entry, scope)
            mutate { t -> t.copy(entries = t.entries.map { if (it.id in ids) it.copy(attachments = it.attachments + attachment) else it }) }
        }
        return attachment
    }

    fun removeAttachment(attachment: Attachment, entry: TimetableEntry, scope: EditScope) {
        if (scope is EditScope.Day) {
            mutate { t -> updatingOccurrence(t, entry.id, scope.day) { o -> o.copy(attachments = o.attachments.filter { it.id != attachment.id }) } }
        } else {
            val ids = targets(entry, scope)
            mutate { t -> t.copy(entries = t.entries.map { e -> if (e.id in ids) e.copy(attachments = e.attachments.filter { it.id != attachment.id }) else e }) }
        }
        removeUnreferencedFiles()
    }

    /** Il file di un allegato (con un account BCW può essere solo sul server). */
    suspend fun openAttachment(attachment: Attachment): File {
        val o = owner ?: throw IllegalStateException("Il file «${attachment.name}» non è più disponibile.")
        val f = file(o, attachment.storedName)
        if (withContext(Dispatchers.IO) { f.exists() }) return f
        val fetched = runCatching { remoteFile?.invoke(o, attachment.storedName) }.getOrNull()
        if (fetched != null && withContext(Dispatchers.IO) { f.exists() }) return f
        throw IllegalStateException("Il file «${attachment.name}» non è più disponibile.")
    }

    // MARK: Allineamento

    fun setAlignment(alignment: Alignment) = mutate { t ->
        t.copy(alignment = alignment, entries = if (alignment == Alignment.automatic) t.entries.flatMap { it.alignedPieces() } else t.entries)
    }

    // MARK: Verifiche nascoste

    fun ruleReason(test: AgendaEvent, unmatched: Boolean): String? {
        val rules = timetable.testRules
        if (rules.count == 0) return null
        if (test.authorName.isNotEmpty() && test.authorName in rules.teachers) return "Docente: ${test.authorName}"
        val subject = test.subjectName
        if (subject != null && normalized(subject) in rules.subjects) return "Materia: $subject"
        val w = Weekday.of(test.day)
        if (w in rules.weekdays) return "Giorno: ${Weekday.lower(w)}"
        val day = test.day
        rules.periods.firstOrNull { !day.isBefore(it.start) && !day.isAfter(it.end) }?.let {
            return "Periodo: ${com.bcw.classeviva.util.Fmt.shortDay(it.start)}–${com.bcw.classeviva.util.Fmt.shortDay(it.end)}"
        }
        val text = normalized(test.notes)
        rules.keywords.firstOrNull { it.isNotEmpty() && text.contains(normalized(it)) }?.let { return "Contiene «$it»" }
        if (unmatched && rules.unmatched) return "Senza lezione in orario"
        return null
    }

    /** `null` se la verifica è visibile. */
    fun hiddenReason(test: AgendaEvent, unmatched: Boolean): HiddenReason? {
        val t = timetable
        if (test.id in t.shownTests) return null
        if (test.id in t.hiddenTests) return HiddenReason.Manual
        return ruleReason(test, unmatched)?.let { HiddenReason.Rule(it) }
    }

    fun setTestHidden(hidden: Boolean, test: AgendaEvent, unmatched: Boolean = false) {
        val ruled = ruleReason(test, unmatched) != null
        mutate { t ->
            if (hidden) t.copy(
                shownTests = t.shownTests.filter { it != test.id },
                hiddenTests = if (!ruled && test.id !in t.hiddenTests) t.hiddenTests + test.id else t.hiddenTests,
            ) else t.copy(
                hiddenTests = t.hiddenTests.filter { it != test.id },
                shownTests = if (ruled && test.id !in t.shownTests) t.shownTests + test.id else t.shownTests,
            )
        }
    }

    fun resetTestOverride(id: Long) = mutate { t -> t.copy(shownTests = t.shownTests.filter { it != id }) }
    fun setTestRules(rules: TestRules) = mutate { it.copy(testRules = rules) }

    // MARK: Colori e palette

    fun saveCustomColor(hex: Int) = mutate { t -> if (hex in t.customColors) t else t.copy(customColors = t.customColors + hex) }
    fun deleteCustomColor(hex: Int) = mutate { t -> t.copy(customColors = t.customColors.filter { it != hex }) }

    fun savePalette(palette: Palette) = mutate { t ->
        val index = t.customPalettes.indexOfFirst { it.id == palette.id }
        t.copy(customPalettes = if (index >= 0) t.customPalettes.toMutableList().also { it[index] = palette } else t.customPalettes + palette)
    }

    fun deletePalette(id: String) = mutate { t ->
        t.copy(
            customPalettes = t.customPalettes.filter { it.id != id },
            entries = t.entries.map { e -> if ((e.color as? EntryColor.Series)?.paletteID == id) e.copy(color = null) else e },
        )
    }

    /** Colora una serie di eventi con una palette (`null` = colore automatico). */
    fun applyPalette(paletteID: String?, ids: Collection<String>) {
        val set = ids.toSet()
        mutate { t ->
            val selected = t.entries.filter { it.id in set }.sortedWith(entryOrder)
            val (positions, count) = seriesPositions(selected)
            val colors = selected.mapIndexed { i, e -> e.id to (if (paletteID == null) null else EntryColor.Series(paletteID, positions[i], count)) }.toMap()
            t.copy(entries = t.entries.map { e -> if (e.id in colors) e.copy(color = colors[e.id]) else e })
        }
    }

    fun setColor(color: EntryColor?, ids: Collection<String>) {
        val set = ids.toSet()
        mutate { t -> t.copy(entries = t.entries.map { if (it.id in set) it.copy(color = color) else it }) }
    }

    // MARK: Bozza

    fun showDraft(value: TimetableDraft?) { draft = value }
    fun discardDraft() { draft = null }

    /** La bozza ha lezioni che l'allineamento automatico sposterebbe: applicandola si passa a quello libero. */
    val draftNeedsFreeAlignment: Boolean
        get() = alignment == Alignment.automatic && (draft?.entries?.any { !it.isAutoAligned } == true)

    /** Applica la bozza: sostituisce l'orario o aggiunge solo gli eventi che non si sovrappongono. */
    fun applyDraft(replacing: Boolean) {
        val d = draft ?: return
        applyEntries(d.entries, d.settings, replacing)
        draft = null
    }

    /** Applica eventi generati (anche dall'introduzione, che tiene la bozza per sé). */
    fun applyEntries(entries: List<TimetableEntry>, settings: GenerationSettings, replacing: Boolean) {
        val needsFree = alignment == Alignment.automatic && entries.any { !it.isAutoAligned }
        mutate { t ->
            val base = if (needsFree) t.copy(alignment = Alignment.free) else t
            if (replacing) base.copy(entries = entries, occurrences = emptyMap(), generation = settings)
            else {
                val additions = entries.filter { n ->
                    base.entries.none { o -> o.weekday == n.weekday && o.isBreak == n.isBreak && o.start < n.end && n.start < o.end }
                }
                base.copy(entries = base.entries + additions, generation = settings)
            }
        }
        removeUnreferencedFiles()
    }

    // MARK: Backup

    suspend fun makeBackup(appVersion: String): String = withContext(Dispatchers.IO) {
        val files = mutableListOf<JsonElement>()
        val o = owner
        if (o != null) {
            for (name in timetable.referencedFiles.sorted()) {
                val f = file(o, name)
                if (f.exists()) files += JsonObject(sortedMapOf("data" to JsonPrimitive(Base64.getEncoder().encodeToString(f.readBytes())), "storedName" to JsonPrimitive(name)))
            }
        }
        val doc = JsonObject(sortedMapOf(
            "appVersion" to JsonPrimitive(appVersion),
            "exportedAt" to JsonPrimitive(Dates.isoUtc(Instant.now())),
            "files" to JsonArray(files),
            "format" to JsonPrimitive(BACKUP_FORMAT),
            "timetable" to encodeTimetable(timetable),
            "version" to JsonPrimitive(BACKUP_VERSION),
        ))
        prettyJson.encodeToString(JsonElement.serializer(), doc)
    }

    /** Valida un backup senza applicarlo. */
    fun readBackup(text: String): Backup {
        val json = parseJson(text) as? JsonObject ?: throw IllegalArgumentException(BackupError.notABackup)
        if ((json["format"] as? JsonPrimitive)?.content != BACKUP_FORMAT) throw IllegalArgumentException(BackupError.notABackup)
        val version = (json["version"] as? JsonPrimitive)?.intOrNull ?: 0
        if (version > BACKUP_VERSION) throw IllegalArgumentException(BackupError.newerVersion)
        try {
            val appVersion = (json["appVersion"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw TimetableFormatError()
            val exportedAt = Dates.parseIsoInstant((json["exportedAt"] as? JsonPrimitive)?.content) ?: throw TimetableFormatError()
            val filesJson = json["files"] as? JsonArray ?: throw TimetableFormatError()
            val tt = json["timetable"] as? JsonObject ?: throw TimetableFormatError()
            if ((json["version"] as? JsonPrimitive)?.intOrNull == null) throw TimetableFormatError()
            val files = filesJson.map { f ->
                val fo = f as? JsonObject ?: throw TimetableFormatError()
                val data = (fo["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw TimetableFormatError()
                val stored = (fo["storedName"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw TimetableFormatError()
                BackupFile(data, stored)
            }
            return Backup(appVersion, exportedAt, files, decodeTimetable(tt, strict = true))
        } catch (e: Exception) {
            throw IllegalArgumentException(BackupError.notABackup)
        }
    }

    suspend fun restore(backup: Backup) {
        val o = owner ?: throw IllegalStateException(BackupError.noSession)
        withContext(Dispatchers.IO) {
            for (f in backup.files) {
                if (!isPlainFileName(f.storedName)) continue
                val target = file(o, f.storedName)
                target.parentFile?.mkdirs()
                target.writeBytes(Base64.getDecoder().decode(f.data))
            }
        }
        draft = null
        timetable = backup.timetable
        reindex()
        save()
        removeUnreferencedFiles()
    }

    // MARK: Contenuto di una giornata

    /**
     * Tutto ciò che serve per mostrare un giorno: eventi (della bozza, se c'è), disposizione,
     * verifiche di Classeviva associate alle lezioni e segni di note, file e verifiche.
     */
    fun dayContent(day: LocalDate, events: List<AgendaEvent>, splitAtBreaks: Boolean = true): DayContent {
        val entries = entriesOn(day)
        val layout = timetableLayout(entries, splitAtBreaks)
        val isDraft = draft != null
        val tests = events.filter { it.kind == com.bcw.classeviva.data.AgendaKind.test }
        val match = matchTests(tests, entries)
        val registerTests = LinkedHashMap<String, List<AgendaEvent>>()
        val hidden = LinkedHashMap<String, List<AgendaEvent>>()
        for ((id, list) in match.matched) {
            val visible = list.filter { hiddenReason(it, false) == null }
            val h = list.filter { hiddenReason(it, false) != null }
            if (visible.isNotEmpty()) registerTests[id] = visible
            if (h.isNotEmpty()) hidden[id] = h
        }
        val unmatchedTests = match.unmatched.filter { hiddenReason(it, true) == null }
        val badges = LinkedHashMap<String, EntryBadges>()
        for (entry in entries) {
            val info = if (isDraft) Occurrence() else occurrence(entry.id, day)
            val manual = !isDraft && isManualTest(entry, day)
            badges[entry.id] = EntryBadges(
                isTest = manual || registerTests.containsKey(entry.id),
                isRegisterTest = registerTests.containsKey(entry.id),
                hasNote = entry.note.isNotEmpty() || info.note.isNotEmpty(),
                attachmentCount = entry.attachments.size + info.attachments.size,
            )
        }
        return DayContent(day, entries, layout, isDraft, registerTests, hidden, unmatchedTests, badges)
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true; prettyPrintIndent = "  " }
