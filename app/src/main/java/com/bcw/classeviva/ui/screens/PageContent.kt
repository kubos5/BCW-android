package com.bcw.classeviva.ui.screens

import androidx.compose.runtime.Composable
import com.bcw.classeviva.ui.nav.Page
import com.bcw.classeviva.ui.nav.Tab
import com.bcw.classeviva.ui.screens.settings.AccountScreen
import com.bcw.classeviva.ui.screens.settings.SettingsPaneScreen
import com.bcw.classeviva.ui.screens.settings.SettingsScreen
import com.bcw.classeviva.ui.screens.timetable.HiddenTestsScreen
import com.bcw.classeviva.ui.screens.timetable.TimetableScreen

/** La pagina per ogni destinazione della navigazione. */
@Composable
fun PageContent(page: Page) {
    when (page) {
        is Page.Root -> when (page.tab) {
            Tab.dashboard -> DashboardScreen()
            Tab.timetable -> TimetableScreen()
            Tab.grades -> GradesScreen()
            Tab.you -> YouScreen()
        }
        is Page.Subject -> SubjectDetailScreen(page.id)
        Page.Noticeboard -> NoticeboardScreen()
        is Page.Notice -> NoticeDetailScreen(page.id)
        Page.Notes -> NotesScreen()
        Page.Reports -> ReportsScreen()
        Page.Previous -> PreviousScreen()
        Page.Absences -> AbsencesScreen()
        Page.Didactics -> DidacticsScreen()
        Page.Lessons -> LessonsScreen()
        Page.Agenda -> AgendaScreen()
        Page.Subjects -> SubjectsScreen()
        Page.Schoolbooks -> SchoolbooksScreen()
        Page.Calendar -> CalendarScreen()
        Page.Settings -> SettingsScreen()
        is Page.SettingsPane -> SettingsPaneScreen(page.pane)
        Page.Account -> AccountScreen()
        Page.HiddenTests -> HiddenTestsScreen()
    }
}
