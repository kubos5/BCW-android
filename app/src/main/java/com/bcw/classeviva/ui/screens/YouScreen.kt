package com.bcw.classeviva.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AbsenceKind
import com.bcw.classeviva.data.AppModel
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.util.GradeFormat
import com.bcw.classeviva.util.nameCased
import com.bcw.classeviva.ui.components.Avatar
import com.bcw.classeviva.ui.components.BCWLogo
import com.bcw.classeviva.ui.components.ChevronRight
import com.bcw.classeviva.ui.components.IconBadge
import com.bcw.classeviva.ui.components.ListGroup
import com.bcw.classeviva.ui.components.NavigationRow
import com.bcw.classeviva.ui.components.StatTile
import com.bcw.classeviva.ui.components.ListRow
import com.bcw.classeviva.ui.components.gradeTone
import com.bcw.classeviva.ui.components.tone
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.nav.Section
import com.bcw.classeviva.ui.nav.sectionGroups
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalNavigator
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.block
import com.bcw.classeviva.ui.theme.Theme

@Composable
fun sectionTint(section: Section) = when (section) {
    Section.noticeboard -> Theme.scheme.primary
    Section.notes -> Theme.colors.poor.color
    Section.reports -> Theme.colors.neutral.color
    Section.previous -> Theme.scheme.onSurfaceVariant
    Section.absences -> Theme.colors.fair.color
    Section.didactics -> Theme.colors.subject(3).color
    Section.lessons -> Theme.colors.subject(1).color
    Section.agenda -> Theme.colors.subject(5).color
    Section.subjects -> Theme.colors.subject(2).color
    Section.schoolbooks -> Theme.colors.subject(4).color
    Section.calendar -> Theme.colors.subject(7).color
}

fun sectionSubtitle(model: AppModel, section: Section): String = when (section) {
    Section.noticeboard -> model.unreadNoticesCount.let { if (it == 0) "Tutto letto" else if (it == 1) "1 comunicazione da leggere" else "$it comunicazioni da leggere" }
    Section.notes -> if (model.notes.isNotEmpty()) "${model.notes.size} in totale" else "Nessuna nota"
    Section.reports -> "Documenti di valutazione"
    Section.previous -> "Pagelle e archivio degli anni passati"
    Section.absences -> model.unjustifiedAbsences.size.let { if (it == 0) "Tutto giustificato" else "$it da giustificare" }
    Section.didactics -> "File condivisi dai docenti"
    Section.lessons -> "Argomenti svolti in classe"
    Section.agenda -> "Tutti i compiti e le verifiche"
    Section.subjects -> "${model.subjects.size} materie"
    Section.schoolbooks -> "Adozioni dell'anno in corso"
    Section.calendar -> "Vacanze e giorni di lezione"
}

fun sectionBadge(model: AppModel, section: Section): Int = when (section) {
    Section.noticeboard -> model.unreadNoticesCount
    Section.notes -> model.unreadNotesCount
    Section.absences -> model.unjustifiedAbsences.size
    else -> 0
}

/**
 * "Tu": profilo centrato (come la pagina dell'account Google), statistiche collegate, ciò che aspetta
 * l'utente e tutte le altre funzioni in liste segmentate. Le Impostazioni dall'ingranaggio in alto.
 */
@Composable
fun YouScreen() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    PageScaffold(
        title = "Tu",
        actions = { IconButton(onClick = { nav.push(Page.Settings) }) { Icon("gear", contentDescription = "Impostazioni") } },
        onRefresh = { model.refreshAll() },
        gap = 28.dp,
    ) {
        block("profile") { ProfileHero() }
        block("stats") { Stats() }
        block("attention") { Attention() }
        for ((title, sections) in sectionGroups) {
            block(title) {
                ListGroup(header = title) {
                    for (s in sections) item(s) {
                        NavigationRow(s.title, { nav.push(s.page) }, subtitle = sectionSubtitle(model, s), leading = { IconBadge(s.icon, sectionTint(s)) }, badge = sectionBadge(model, s))
                    }
                }
            }
        }
        block("footer") { Footer() }
    }
}

@Composable
private fun ProfileHero() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val ui = LocalUi.current
    val haptics = LocalHapticFeedback.current
    val card = model.card
    val school = card?.schoolDescription?.let { listOfNotNull(it, card.schoolCity?.nameCased()).joinToString(" · ") }
    var turns by rememberSaveable { mutableIntStateOf(0) }
    val angle by animateFloatAsState(turns * 60f, Theme.motion.slowSpatialSpec(), label = "spin")
    val many = model.accounts.size > 1
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier.clickable(remember { MutableInteractionSource() }, null) { turns += 1; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
        ) {
            Avatar(model.initials, 112.dp, shape = "cookie12Sided", rotation = angle)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(model.displayName, style = Theme.type.style(28, 500, rounded = true), color = Theme.scheme.onSurface, textAlign = TextAlign.Center)
            if (!school.isNullOrEmpty()) Text(school, style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { nav.push(Page.Account) }) {
                Icon("manageAccounts", size = 18.dp)
                Text("Gestisci account", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { ui.accountSwitcher = true }) {
                Icon(if (many) "switchAccount" else "userPlus", size = 18.dp)
                Text(if (many) "Cambia" else "Aggiungi", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Media e frequenza in una griglia collegata; assenze, ritardi e uscite aprono la pagina delle assenze. */
@Composable
private fun Stats() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    fun count(vararg kinds: AbsenceKind) = model.absences.count { it.kind in kinds }
    val average = model.gradeBook.average()
    val open = { nav.push(Page.Absences) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Media generale", GradeFormat.average(average), "graduationCap", tone(gradeTone(average)).color, "cookie9Sided", Modifier.weight(1f), corners = "tl")
            StatTile("Assenze", count(AbsenceKind.absence).toString(), "userX", Theme.colors.poor.color, "clover4Leaf", Modifier.weight(1f), corners = "tr", onClick = open)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Ritardi", count(AbsenceKind.late, AbsenceKind.shortLate).toString(), "clock", Theme.colors.fair.color, "sunny", Modifier.weight(1f), corners = "bl", onClick = open)
            StatTile("Uscite", count(AbsenceKind.earlyExit).toString(), "doorOpen", Theme.colors.neutral.color, "cookie6Sided", Modifier.weight(1f), corners = "br", onClick = open)
        }
    }
}

/** Ciò che aspetta l'utente, nel contenitore secondario; non c'è se è tutto a posto. */
@Composable
private fun Attention() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val notices = model.unreadNoticesCount
    val notes = model.unreadNotesCount
    val absences = model.unjustifiedAbsences.size
    if (notices == 0 && notes == 0 && absences == 0) return
    ListGroup(header = "Da vedere", background = Theme.scheme.secondaryContainer) {
        if (notices > 0) item("n") {
            ListRow(if (notices == 1) "1 comunicazione da leggere" else "$notices comunicazioni da leggere", onClick = { nav.push(Page.Noticeboard) },
                leading = { IconBadge("megaphone", Theme.scheme.primary) }, trailing = { ChevronRight() })
        }
        if (notes > 0) item("o") {
            ListRow(if (notes == 1) "1 nota da leggere" else "$notes note da leggere", onClick = { nav.push(Page.Notes) },
                leading = { IconBadge("messageAlert", Theme.colors.poor.color) }, trailing = { ChevronRight() })
        }
        if (absences > 0) item("a") {
            ListRow(if (absences == 1) "1 assenza da giustificare" else "$absences assenze da giustificare", onClick = { nav.push(Page.Absences) },
                leading = { IconBadge("userClock", Theme.colors.fair.color) }, trailing = { ChevronRight() })
        }
    }
}

@Composable
private fun Footer() {
    val model = LocalModel.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BCWLogo(height = 28.dp)
        Text("Better ClasseViVa", style = Theme.type.style(14, 600), color = Theme.scheme.onSurfaceVariant)
        model.lastUpdated?.let { Text("Aggiornato alle ${Fmt.time(it)}", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant) }
        if (model.isDemo) Text("Modalità demo: i dati sono di esempio.", style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant)
    }
}
