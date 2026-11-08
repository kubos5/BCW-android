package com.bcw.classeviva.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Tab
import com.bcw.classeviva.ui.screens.PageContent
import com.bcw.classeviva.ui.theme.Theme

/** Schede visibili: l'Orario si può nascondere (Impostazioni › Orario). */
fun visibleTabs(showsTimetable: Boolean) = Tab.entries.filter { showsTimetable || it != Tab.timetable }

/** La shell dell'app: le quattro schede con la barra flottante (telefoni) o laterale (tablet). */
@Composable
fun MainShell() {
    val model = LocalModel.current
    val nav = LocalNavigator.current
    val ui = LocalUi.current
    val shell = remember { ShellState() }
    val tabs = visibleTabs(model.preferences.showsTimetableTab)

    // Se la scheda Orario viene nascosta mentre è aperta si passa alla Dashboard.
    LaunchedEffect(tabs) { if (nav.tab !in tabs) nav.select(Tab.dashboard) }
    // Collegamenti dai widget e dalle notifiche.
    LaunchedEffect(model.openedLink) {
        val link = model.openedLink ?: return@LaunchedEffect
        val tab = Tab.entries.firstOrNull { it.name == link.tab } ?: return@LaunchedEffect
        nav.popToRoot()
        nav.select(if (tab == Tab.timetable && Tab.timetable !in tabs) Tab.dashboard else tab)
    }

    BackHandler(enabled = nav.searching || nav.canPop || nav.tab != Tab.dashboard) {
        when {
            nav.searching -> nav.searching = false
            nav.canPop -> nav.pop()
            else -> nav.select(Tab.dashboard)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Theme.scheme.surface)) {
        val wide = maxWidth >= 600.dp
        shell.wide = wide
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barBottom = maxOf(14.dp, navInset - 10.dp)
        shell.bottomSpace = if (wide || shell.hidesBar) navInset else maxOf(64.dp + navInset, barBottom + 64.dp)
        CompositionLocalProvider(LocalShell provides shell) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = Theme.scheme.surface, modifier = Modifier.fillMaxHeight()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                        for (tab in tabs) {
                            val selected = nav.tab == tab
                            NavigationRailItem(
                                selected = selected, onClick = { nav.select(tab) },
                                icon = { TabIcon(tab, selected) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { TabsContent() }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    TabsContent()
                    AnimatedVisibility(
                        visible = !shell.hidesBar,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barBottom),
                        enter = slideInVertically(Theme.motion.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                        exit = slideOutVertically(Theme.motion.fastSpatialSpec()) { it * 2 } + fadeOut(),
                    ) {
                        FloatingNavBar(tabs, nav.tab, onSelect = { nav.select(it) }, onLongPressYou = { ui.accountSwitcher = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, selected: Boolean) {
    val model = LocalModel.current
    val badge = if (tab == Tab.you) model.unreadNoticesCount + model.unreadNotesCount else 0
    if (badge > 0) {
        BadgedBox(badge = { Badge { Text(badge.toString()) } }) { Icon(if (selected) tab.iconSelected else tab.icon) }
    } else Icon(if (selected) tab.iconSelected else tab.icon)
}

/** Contenuto delle schede: dissolvenza tra le schede, asse condiviso tra le pagine di una scheda. */
@Composable
private fun TabsContent() {
    val nav = LocalNavigator.current
    val holder = rememberSaveableStateHolder()
    val reduced = Theme.reducedMotion
    AnimatedContent(targetState = nav.tab, transitionSpec = { Transitions.fadeThrough(reduced) }, label = "tabs") { tab ->
        val stack = nav.stack(tab)
        AnimatedContent(
            targetState = stack.size - 1 to stack.last(),
            transitionSpec = { Transitions.sharedAxisX(if (targetState.first >= initialState.first) 1 else -1, reduced) },
            contentKey = { "${it.first}-${it.second}" },
            label = "pages",
        ) { (index, page) ->
            val key = "${tab.name}/$index/$page"
            holder.SaveableStateProvider(key) {
                CompositionLocalProvider(LocalPageContext provides PageContext(tab, nav.tab == tab && index == nav.stack(tab).size - 1, index == 0)) {
                    PageContent(page)
                }
            }
        }
    }
}

/**
 * Barra di navigazione flottante (come l'app iOS e il web): una pillola di 56 dp con i nomi delle
 * schede; quella aperta è piena nel primario con l'icona. Come sul web, cambiando scheda la
 * disposizione cambia subito, poi le schede scivolano dal posto di prima al nuovo e la pillola va
 * dalla scheda di prima a quella nuova, con la stessa molla (prima l'icona si allargava e tutte le
 * posizioni cambiavano a ogni fotogramma, e la pillola inseguiva un bersaglio che si spostava).
 */
@Composable
fun FloatingNavBar(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit, onLongPressYou: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val model = LocalModel.current
    val reduced = Theme.reducedMotion
    val spring = Theme.motion.defaultSpatialSpec<Float>()
    // Posizione e larghezza (nella barra) di ogni scheda, già nella disposizione nuova.
    val bounds = remember { mutableStateMapOf<Tab, Pair<Float, Float>>() }
    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    var pillReady by remember { mutableStateOf(false) }
    val target = bounds[selected]
    LaunchedEffect(target) {
        val (x, w) = target ?: return@LaunchedEffect
        if (!pillReady || reduced) {
            pillX.snapTo(x)
            pillW.snapTo(w)
            pillReady = true
            return@LaunchedEffect
        }
        launch { pillX.animateTo(x, spring) }
        launch { pillW.animateTo(w, spring) }
    }
    Surface(
        shape = RoundedCornerShape(28.dp), color = Theme.scheme.surfaceContainerHigh,
        modifier = Modifier.height(56.dp).shadow(8.dp, RoundedCornerShape(28.dp), ambientColor = Theme.colors.shadow, spotColor = Theme.colors.shadow),
    ) {
        Box(Modifier.padding(6.dp)) {
            if (pillReady) {
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val w = pillW.value.roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(w, 44.dp.roundToPx()))
                            layout(w, placeable.height) { placeable.place(pillX.value.roundToInt(), 0) }
                        }
                        .clip(RoundedCornerShape(22.dp)).background(Theme.scheme.primary),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                for (tab in tabs) {
                    NavBarTab(
                        tab, tab == selected, badge = if (tab == Tab.you) model.unreadNoticesCount + model.unreadNotesCount else 0,
                        onPlaced = { x, w -> bounds[tab] = x to w },
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(tab) },
                        onLongClick = if (tab == Tab.you) ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPressYou() }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarTab(tab: Tab, isSelected: Boolean, badge: Int, onPlaced: (Float, Float) -> Unit, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val reduced = Theme.reducedMotion
    val spring = Theme.motion.defaultSpatialSpec<Float>()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Theme.motion.fastSpatialSpec(), label = "press")
    val color by animateColorAsState(if (isSelected) Theme.scheme.onPrimary else Theme.scheme.onSurfaceVariant, Theme.motion.defaultEffectsSpec(), label = "tabColor")
    // La scheda scivola dal posto di prima a quello nuovo (FLIP).
    val shift = remember { Animatable(0f) }
    var lastX by remember { mutableStateOf<Float?>(null) }
    // L'icona della scheda aperta occupa subito il suo posto e compare ingrandendosi.
    val icon = remember { Animatable(if (isSelected) 1f else 0f) }
    LaunchedEffect(isSelected) {
        if (!isSelected) icon.snapTo(0f)
        else if (reduced) icon.snapTo(1f)
        else {
            icon.snapTo(0f)
            icon.animateTo(1f, spring)
        }
    }
    Row(
        Modifier
            .onPlaced { c ->
                val x = c.positionInParent().x
                val old = lastX
                lastX = x
                onPlaced(x, c.size.width.toFloat())
                if (old != null && abs(old - x) > 0.5f && !reduced) {
                    scope.launch {
                        shift.snapTo(shift.value + old - x)
                        shift.animateTo(0f, spring)
                    }
                }
            }
            .graphicsLayer {
                translationX = shift.value
                scaleX = scale
                scaleY = scale
            }
            .height(44.dp).clip(RoundedCornerShape(22.dp))
            .combinedClickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = if (isSelected) 16.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSelected) {
            Icon(tab.iconSelected, size = 20.dp, tint = color, modifier = Modifier.graphicsLayer {
                alpha = icon.value.coerceIn(0f, 1f)
                val s = 0.4f + 0.6f * icon.value
                scaleX = s
                scaleY = s
            })
        }
        Text(tab.title, style = Theme.type.style(14, if (isSelected) 650 else 550), color = color, maxLines = 1)
        if (badge > 0) Badge(containerColor = Theme.scheme.error) { Text(badge.toString()) }
    }
}

/** La pagina nasconde la barra di navigazione finché è in vista (ricerca, modifica dell'orario). */
@Composable
fun HidesNavigationBar(active: Boolean = true) {
    val shell = LocalShell.current
    DisposableEffect(active) {
        if (active) shell.barHiders += 1
        onDispose { if (active) shell.barHiders -= 1 }
    }
}
