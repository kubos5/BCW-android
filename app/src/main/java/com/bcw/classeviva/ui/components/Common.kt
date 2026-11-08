package com.bcw.classeviva.ui.components

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.AgendaEvent
import com.bcw.classeviva.data.AgendaKind
import com.bcw.classeviva.util.Dates
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.UiController
import com.bcw.classeviva.ui.theme.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Voce di un menu: `null` come etichetta = separatore. */
data class MenuItem(
    val label: String, val icon: String? = null, val checked: Boolean? = null, val destructive: Boolean = false,
    val enabled: Boolean = true, val subtitle: String? = null, val run: () -> Unit,
)

@Composable
fun BCWMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem?>, sections: Map<Int, String> = emptyMap()) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(Theme.radii.l)) {
        items.forEachIndexed { index, item ->
            sections[index]?.let { title ->
                Text(title, style = Theme.type.style(13, 650), color = Theme.scheme.primary, modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (item == null) {
                androidx.compose.material3.HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(vertical = 4.dp))
            } else {
                DropdownMenuItem(
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(item.label, color = if (item.destructive) Theme.scheme.error else Theme.scheme.onSurface)
                            item.subtitle?.let { Text(it, style = Theme.type.style(12), color = Theme.scheme.onSurfaceVariant) }
                        }
                    },
                    onClick = { onDismiss(); item.run() },
                    enabled = item.enabled,
                    leadingIcon = item.icon?.let { { Icon(it, size = 22.dp, tint = if (item.destructive) Theme.scheme.error else Theme.scheme.onSurfaceVariant) } },
                    trailingIcon = if (item.checked == true) ({ Icon("check", size = 20.dp, tint = Theme.scheme.primary) }) else null,
                )
            }
        }
    }
}


/** Aggiunge un compito, una verifica o un evento al calendario del telefono. */
fun addToCalendar(ui: UiController, event: AgendaEvent) {
    val prefix = when (event.kind) { AgendaKind.test -> "Verifica"; AgendaKind.homework -> "Compiti"; else -> "" }
    val title = listOf(prefix, event.title).filter { it.isNotEmpty() }.joinToString(" · ")
    val allDay = event.isFullDay || event.kind == AgendaKind.homework
    val begin = Dates.epochMillis(if (allDay) event.day.atStartOfDay() else event.begin)
    val end = if (allDay) Dates.epochMillis(event.day.plusDays(1).atStartOfDay())
    else Dates.epochMillis(if (event.end.isAfter(event.begin)) event.end else event.begin.plusHours(1))
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ui.context.startActivity(intent)
    } catch (e: Exception) {
        ui.toast("Nessuna app del calendario disponibile.")
    }
}

/**
 * Compito, verifica o evento in una lista segmentata, con le sue azioni: la casella del compito,
 * il testo tagliato che si apre, il menu tenendo premuto.
 */
@Composable
fun AgendaCell(event: AgendaEvent, expanded: Set<String>, onExpandedChange: (Set<String>) -> Unit, scope: String = "", showsDate: Boolean = false) {
    val model = LocalModel.current
    val ui = LocalUi.current
    val prefs = model.preferences
    val done = prefs.isCompleted(event)
    val key = "$scope${event.id}"
    var menu by remember { mutableStateOf(false) }
    Box {
        SegCell(onLongClick = { menu = true }) {
            AgendaEventRow(
                event, done, key in expanded,
                onToggleDone = {
                    prefs.toggleCompleted(event)
                    model.rescheduleReminders()
                },
                onExpand = { onExpandedChange(if (key in expanded) expanded - key else expanded + key) },
                showsDate = showsDate,
            )
        }
        BCWMenu(menu, { menu = false }, buildList {
            if (event.kind != AgendaKind.event) add(MenuItem(if (done) "Segna come da fare" else "Segna come fatto", if (done) "arrowUturn" else "checkCircle") {
                prefs.toggleCompleted(event)
                model.rescheduleReminders()
            })
            add(MenuItem("Aggiungi al calendario", "calendarPlus") { addToCalendar(ui, event) })
            add(MenuItem("Condividi", "share") { ui.shareText("${event.title} – ${Fmt.longDay(event.day)}\n${event.notes}") })
            add(MenuItem("Copia testo", "copy") { ui.copy(event.notes, "Testo copiato negli appunti.") })
        })
    }
}

private fun LocalDate.utcMillis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

/** Selettore della data di Material 3 (modale, con la data in grande e il mese). */
@Composable
fun DatePickerSheet(
    selection: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit, min: LocalDate? = null, max: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selection.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return (min == null || !day.isBefore(min)) && (max == null || !day.isAfter(max))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { onPick(Dates.today()); onDismiss() }) { Text("Oggi") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}
