package com.bcw.classeviva.ui.screens.timetable

import androidx.compose.runtime.Composable
import com.bcw.classeviva.data.timetable.Alignment
import com.bcw.classeviva.data.timetable.TimetableStore
import com.bcw.classeviva.ui.shell.PageScaffold
import com.bcw.classeviva.ui.shell.UiController

@Composable fun TimetableScreen() = PageScaffold(title = "Orario") {}
@Composable fun HiddenTestsScreen() = PageScaffold(title = "Verifiche nascoste") {}
fun requestAlignment(ui: UiController, store: TimetableStore, value: Alignment) = store.setAlignment(value)
@Composable fun TimetableBackupSection() {}
