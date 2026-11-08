package com.bcw.classeviva.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.nav.Tab
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.launch

/** Spazio della shell: in fondo per la barra flottante, e se lo schermo è largo. */
@Stable
class ShellState {
    var bottomSpace by mutableStateOf(0.dp)
    var wide by mutableStateOf(false)
    /** Pagine che nascondono la barra di navigazione (ricerca, modifica dell'orario). */
    var barHiders by mutableIntStateOf(0)
    val hidesBar: Boolean get() = barHiders > 0
}

val LocalShell = compositionLocalOf { ShellState() }

/** La scheda e se la pagina è quella in vista (per il nuovo tocco sulla scheda). */
data class PageContext(val tab: Tab, val isCurrent: Boolean, val isRoot: Boolean)

val LocalPageContext = compositionLocalOf<PageContext?> { null }

/** Barra superiore: titolo grande nella riga dei pulsanti per le pagine principali, freccia indietro per le altre. */
@Composable
fun AppBar(title: String, isRoot: Boolean, scrolled: Boolean, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    val bg by animateColorAsState(if (scrolled) Theme.scheme.surfaceContainer else Theme.scheme.surface, Theme.motion.defaultEffectsSpec(), label = "bar")
    Row(
        Modifier.fillMaxWidth().background(bg).windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon("arrowBack", contentDescription = "Indietro") }
        }
        Text(
            title,
            style = if (isRoot) Theme.type.style(32, 500, rounded = true, tracking = -0.2) else Theme.type.style(22, 500, rounded = true),
            color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * Pagina di BCW: barra superiore (o una barra propria, es. la ricerca della Dashboard), una parte
 * ferma facoltativa sopra l'area che scorre, contenuto in una lista pigra con i margini di 16 dp,
 * trascinamento per aggiornare e spazio in fondo per la barra di navigazione flottante.
 */
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    topBar: (@Composable (scrolled: Boolean) -> Unit)? = null,
    fixed: (@Composable () -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    gap: Dp = 16.dp,
    maxContentWidth: Dp = 840.dp,
    contentPadding: PaddingValues? = null,
    onReselect: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    listModifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val nav = LocalNavigator.current
    val page = LocalPageContext.current
    val shell = LocalShell.current
    val scope = rememberCoroutineScope()
    val isRoot = page?.isRoot ?: true
    val scrolled = fixed == null && listState.canScrollBackward

    // Nuovo tocco sulla scheda: in cima, poi alla pagina principale, poi l'azione della pagina.
    if (page != null && page.isCurrent) {
        LaunchedEffect(nav.reselection) {
            if (nav.reselectedTab != page.tab || nav.reselection == 0) return@LaunchedEffect
            if (listState.canScrollBackward) listState.animateScrollToItem(0)
            else if (!page.isRoot) nav.popToRoot()
            else onReselect?.invoke()
        }
    }

    Column(modifier.fillMaxSize().background(Theme.scheme.surface)) {
        if (topBar != null) topBar(scrolled)
        else AppBar(title, isRoot, scrolled, if (isRoot) null else ({ nav.pop() }), actions)
        if (fixed != null) fixed()
        var refreshing by remember { mutableStateOf(false) }
        val body = @Composable {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().then(listModifier),
                contentPadding = contentPadding ?: PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + shell.bottomSpace),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopCenter).widthIn(max = maxContentWidth).fillMaxSize()) {
                if (onRefresh != null) {
                    val state = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch {
                                refreshing = true
                                try { onRefresh() } finally { refreshing = false }
                            }
                        },
                        state = state,
                        indicator = { PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter)) },
                    ) { body() }
                } else body()
            }
            overlay?.invoke(this)
        }
    }
}

/** Elemento della lista a tutta larghezza (le pagine stanno in una colonna larga al massimo 840 dp). */
fun LazyListScope.block(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.fillMaxWidth()) { content() } }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(LocalShell.current.bottomSpace))

/** Stato delle pagine che resta finché è aperta la sessione (come lo stato delle viste del web). */
class ScreenMemory {
    val values = HashMap<String, Any?>()
}

val LocalScreenMemory = compositionLocalOf { ScreenMemory() }

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> rememberInSession(key: String, init: () -> T): T {
    val memory = LocalScreenMemory.current
    return memory.values.getOrPut(key) { init() } as T
}
