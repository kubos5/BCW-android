package com.bcw.classeviva.data

import android.content.SharedPreferences
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.parseJson
import com.bcw.classeviva.util.sentenceCased
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime

/**
 * Notifiche delle novità: dopo ogni aggiornamento si confrontano voti, comunicazioni, note,
 * assenze e compiti/verifiche futuri con quelli già visti (per account) e si notificano solo i
 * tipi attivi in Impostazioni › Notifiche. La prima volta si ricorda senza notificare; più di 4
 * novità di un tipo diventano una notifica riassuntiva. Mai nella demo.
 */
object News {
    private const val MAX_SEEN = 3000
    private fun key(owner: String, kind: NewsKind) = "bcw.seenNews.$owner.${kind.name}"

    data class Item(val id: String, val title: String, val body: String)

    fun items(model: AppModel, kind: NewsKind): List<Item> = when (kind) {
        NewsKind.grades -> model.grades.filter { !it.canceled }.map { g ->
            Item(g.id.toString(), "${g.subjectName.sentenceCased()}: ${g.displayValue}", listOfNotNull(g.componentName, g.notes).joinToString(" · "))
        }
        NewsKind.notices -> model.notices.map { Item(it.id.toString(), it.title, it.category) }
        NewsKind.notes -> model.notes.map { n -> Item("${n.category.code}-${n.id}", "${n.category.title} · ${n.authorName}", n.text) }
        NewsKind.absences -> model.absences.map { a ->
            Item(a.id.toString(), "${a.kind.title} del ${Fmt.shortDay(a.date)}", if (a.isJustified) "Giustificata" else "Da giustificare")
        }
        NewsKind.agenda -> {
            val today = Dates.today().atStartOfDay()
            model.agenda.filter { it.kind != AgendaKind.event && !it.begin.isBefore(today) }.map { e ->
                Item(e.id.toString(), "${if (e.kind == AgendaKind.test) "Verifica" else "Compiti"} di ${e.title.lowercase(Dates.italian)} · ${Fmt.shortDay(e.day)}", e.notes)
            }
        }
    }

    fun process(model: AppModel, store: SharedPreferences) {
        val owner = model.activeAccountID ?: return
        if (model.isDemo) return
        val prefs = model.preferences
        val toNotify = mutableListOf<Pair<NewsKind, List<Item>>>()
        for (kind in NewsKind.entries) {
            val current = items(model, kind)
            val stored = store.getString(key(owner, kind), null)?.let { parseJson(it) as? JsonArray }?.mapNotNull { (it as? JsonPrimitive)?.content }
            if (stored == null) {
                // Prima volta: si ricorda tutto senza notificare (solo se i dati sono arrivati).
                if (current.isNotEmpty()) write(store, owner, kind, current.map { it.id }.takeLast(MAX_SEEN))
                continue
            }
            val seen = stored.toHashSet()
            val fresh = current.filter { it.id !in seen }
            if (fresh.isEmpty()) continue
            write(store, owner, kind, (stored + fresh.map { it.id }).takeLast(MAX_SEEN))
            if (prefs.newsEnabled(kind)) toNotify += kind to fresh
        }
        if (toNotify.isEmpty()) return
        val account = if (model.accounts.size > 1) model.activeAccount?.name else null
        fun withAccount(body: String) = listOfNotNull(account, body.ifEmpty { null }).joinToString("\n")
        for ((kind, fresh) in toNotify) {
            if (fresh.size > 4) {
                val lines = fresh.take(5).joinToString("\n") { it.title } + if (fresh.size > 5) "\n…" else ""
                model.hooks?.notify("${kind.title}: ${fresh.size}", withAccount(lines), "bcw.news.${kind.name}.${System.currentTimeMillis()}", "news")
            } else {
                for (item in fresh) model.hooks?.notify(item.title, withAccount(item.body.ifEmpty { kind.title }), "bcw.news.${kind.name}.${item.id}", "news")
            }
        }
    }

    private fun write(store: SharedPreferences, owner: String, kind: NewsKind, ids: List<String>) {
        store.edit().putString(key(owner, kind), JsonArray(ids.map { JsonPrimitive(it) }).toString()).apply()
    }
}

/** Promemoria: la sera prima di ogni compito o verifica, all'ora scelta. */
object Reminders {
    fun plan(events: List<AgendaEvent>, hour: Int, completed: Set<Long>, now: LocalDateTime = LocalDateTime.now()): List<ReminderPlanItem> {
        val horizon = now.plusDays(14)
        val relevant = events.filter { it.kind != AgendaKind.event && it.begin.isAfter(now) && it.begin.isBefore(horizon) && it.id !in completed }
            .sortedBy { it.begin }
        val plan = mutableListOf<ReminderPlanItem>()
        for ((day, items) in relevant.groupBy { it.day }.toSortedMap().entries.take(40)) {
            val fire = day.minusDays(1).atTime(hour.coerceIn(0, 23), 0)
            if (!fire.isAfter(now)) continue
            val tests = items.filter { it.kind == AgendaKind.test }
            val homework = items.filter { it.kind == AgendaKind.homework }
            val title = if (tests.isNotEmpty()) {
                if (tests.size == 1) "Domani: verifica di ${tests[0].title.lowercase(Dates.italian)}" else "Domani: ${tests.size} verifiche"
            } else {
                if (homework.size == 1) "Compiti per domani" else "${homework.size} compiti per domani"
            }
            plan += ReminderPlanItem("bcw.agenda.${Dates.dayKey(day)}", fire, title, items.joinToString("\n") { "${it.title}: ${it.notes}" })
        }
        return plan
    }
}
