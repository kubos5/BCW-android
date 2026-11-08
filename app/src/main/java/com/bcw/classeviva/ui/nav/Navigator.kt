package com.bcw.classeviva.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Le quattro destinazioni principali (Dashboard, Orario, Voti, Tu). */
enum class Tab(val title: String, val icon: String, val iconSelected: String) {
    dashboard("Dashboard", "dashboard", "dashboardFill"),
    timetable("Orario", "timetable", "timetableFill"),
    grades("Voti", "grades", "gradesFill"),
    you("Tu", "you", "youFill"),
}

/** Pagine aperte sopra la pagina principale di una scheda. */
sealed interface Page {
    data class Root(val tab: Tab) : Page
    data class Subject(val id: Long) : Page
    data object Noticeboard : Page
    data class Notice(val id: Long) : Page
    data object Notes : Page
    data object Reports : Page
    data object Previous : Page
    data object Absences : Page
    data object Didactics : Page
    data object Lessons : Page
    data object Agenda : Page
    data object Subjects : Page
    data object Schoolbooks : Page
    data object Calendar : Page
    data object Settings : Page
    data class SettingsPane(val pane: String) : Page
    data object Account : Page
    data object HiddenTests : Page
}

/** Sezioni di Tu: titolo, icona e colore (come `sections-meta.js`). */
enum class Section(val title: String, val icon: String, val page: Page) {
    noticeboard("Bacheca", "megaphone", Page.Noticeboard),
    notes("Note e annotazioni", "messageAlert", Page.Notes),
    reports("Scrutini e pagelle", "fileSearch", Page.Reports),
    previous("Anni precedenti", "history", Page.Previous),
    absences("Assenze e ritardi", "userClock", Page.Absences),
    didactics("Materiale didattico", "folder", Page.Didactics),
    lessons("Registro delle lezioni", "bookText", Page.Lessons),
    agenda("Agenda completa", "checklist", Page.Agenda),
    subjects("Materie e docenti", "users", Page.Subjects),
    schoolbooks("Libri di testo", "library", Page.Schoolbooks),
    calendar("Calendario scolastico", "calendarClock", Page.Calendar),
}

val sectionGroups: List<Pair<String, List<Section>>> = listOf(
    "Comunicazioni" to listOf(Section.noticeboard, Section.notes),
    "Valutazioni" to listOf(Section.reports, Section.previous),
    "Frequenza" to listOf(Section.absences),
    "Didattica" to listOf(Section.didactics, Section.lessons, Section.agenda, Section.subjects, Section.schoolbooks, Section.calendar),
)

/**
 * Navigazione: una pila di pagine per scheda, come le schede di un'app Material. Il verso
 * dell'ultimo cambio serve alle transizioni (asse condiviso avanti/indietro).
 */
@Stable
class Navigator {
    var tab by mutableStateOf(Tab.dashboard)
        private set
    private val stacks = mutableStateMapOf<Tab, List<Page>>()
    /** +1 avanti, -1 indietro, 0 cambio di scheda. */
    var direction by mutableIntStateOf(0)
        private set
    /** Nuovo tocco sulla scheda aperta (le pagine lo ascoltano per la loro azione). */
    var reselection by mutableIntStateOf(0)
        private set
    var reselectedTab by mutableStateOf<Tab?>(null)
        private set
    /** Ricerca aperta dalla barra in cima alla Dashboard. */
    var searching by mutableStateOf(false)

    fun stack(t: Tab = tab): List<Page> = stacks[t] ?: listOf(Page.Root(t))
    val current: Page get() = stack().last()
    val canPop: Boolean get() = stack().size > 1

    fun select(t: Tab) {
        if (t == tab) {
            reselectedTab = t
            reselection += 1
            return
        }
        direction = 0
        searching = false
        tab = t
    }

    fun push(page: Page) {
        direction = 1
        stacks[tab] = stack() + page
    }

    fun pop(): Boolean {
        val s = stack()
        if (s.size <= 1) return false
        direction = -1
        stacks[tab] = s.dropLast(1)
        return true
    }

    fun popToRoot() {
        if (stack().size <= 1) return
        direction = -1
        stacks[tab] = listOf(Page.Root(tab))
    }

    /** Apre una pagina dentro Tu (le sezioni stanno lì sui telefoni). */
    fun openInYou(page: Page) {
        if (tab != Tab.you) {
            direction = 0
            tab = Tab.you
            stacks[Tab.you] = listOf(Page.Root(Tab.you), page)
        } else push(page)
    }

    fun reset() {
        stacks.clear()
        tab = Tab.dashboard
        direction = 0
        searching = false
    }
}
