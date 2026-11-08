package com.bcw.classeviva.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.theme.Theme

/** Posizione di una voce in una lista segmentata: decide gli angoli (ampi solo all'esterno). */
data class CellPosition(val first: Boolean, val last: Boolean)

val LocalCellPosition = compositionLocalOf<CellPosition?> { null }
val LocalCellBackground = compositionLocalOf<Color?> { null }

class ListGroupScope {
    internal val items = mutableListOf<Pair<Any?, @Composable () -> Unit>>()
    fun item(key: Any? = null, content: @Composable () -> Unit) { items += key to content }
}

/** Forma di una voce: angoli `li` all'esterno, `xs` tra le voci, `l` mentre la si preme. */
@Composable
fun cellShape(position: CellPosition?, pressed: Boolean = false, selected: Boolean = false): RoundedCornerShape {
    val r = Theme.radii
    val p = position ?: CellPosition(true, true)
    val spec = Theme.motion.fastSpatialSpec<Dp>()
    val active = pressed || selected
    val top by animateDpAsState(if (active) r.l else if (p.first) r.li else r.xs, spec, label = "top")
    val bottom by animateDpAsState(if (active) r.l else if (p.last) r.li else r.xs, spec, label = "bottom")
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Lista segmentata di Material 3 Expressive: voci separate da 2 dp, la prima e l'ultima con gli
 * angoli ampi, intestazione e nota facoltative (come `listGroup` del web e `ListGroup` dell'app).
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    background: Color? = null,
    content: ListGroupScope.() -> Unit,
) {
    val scope = ListGroupScope().apply(content)
    if (scope.items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (header != null) ListHeader(header)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            scope.items.forEachIndexed { index, (_, item) ->
                val position = CellPosition(index == 0, index == scope.items.size - 1)
                CompositionLocalProvider(LocalCellPosition provides position, LocalCellBackground provides (background ?: Theme.scheme.surfaceContainer)) {
                    item()
                }
            }
        }
        if (footer != null) ListFooter(footer)
    }
}

@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Theme.type.style(14, 650, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified), color = Theme.scheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}

@Composable
fun ListFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Theme.type.style(12, 400, tracking = 0.3), color = Theme.scheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
}

/** Contenitore di una voce con angoli, sfondo e tocco (pressione = angoli che si stringono). */
@Composable
fun Cell(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    background: Color? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shape = cellShape(LocalCellPosition.current, pressed && onClick != null, selected)
    val bg = if (selected) Theme.scheme.secondaryContainer else background ?: LocalCellBackground.current ?: Theme.scheme.surfaceContainer
    var m = modifier.fillMaxWidth().clip(shape).background(bg)
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(
            interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button,
            onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = { onClick?.invoke() },
        )
    }
    Box(m.padding(padding).then(if (enabled) Modifier else Modifier.alpha(0.5f))) {
        CompositionLocalProvider(LocalContentColor provides if (selected) Theme.scheme.onSecondaryContainer else Theme.scheme.onSurface) {
            content()
        }
    }
}

/** Voce con il contenuto libero e i margini delle voci (`segCell` del web). */
@Composable
fun SegCell(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Cell(modifier, onClick = onClick, onLongClick = onLongClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(content = content)
    }
}

/** Voce di una lista: icona o altro a sinistra, titolo e sottotitolo, contenuto a destra. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    multiline: Boolean = false,
    titleColor: Color? = null,
) {
    Cell(modifier, onClick = onClick, onLongClick = onLongClick, selected = selected, enabled = enabled) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leading != null) leading()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (multiline) Theme.type.style(15, 400, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified) else Theme.type.style(16, 500),
                    color = titleColor ?: if (destructive) Theme.scheme.error else LocalContentColor.current,
                    maxLines = if (multiline) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(subtitle, style = Theme.type.style(14, 400), color = Theme.scheme.onSurfaceVariant, maxLines = if (multiline) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
        }
    }
}

/** Icona di una riga dei moduli (impostazioni). */
@Composable
fun FormIcon(name: String, tint: Color = Theme.scheme.onSurfaceVariant) {
    Icon(name, size = 24.dp, tint = tint)
}

@Composable
fun ChevronRight() = Icon("chevronRight", size = 20.dp, tint = Theme.scheme.onSurfaceVariant)

/** Riga con un interruttore: toccando la riga intera l'interruttore cambia. */
@Composable
fun ToggleRow(
    title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, icon: String? = null,
    subtitle: String? = null, enabled: Boolean = true,
) {
    ListRow(
        title, modifier, subtitle = subtitle, leading = icon?.let { { FormIcon(it) } }, enabled = enabled,
        onClick = { if (enabled) onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled) },
    )
}

/** Riga con un valore a destra. */
@Composable
fun ValueRow(title: String, value: String, modifier: Modifier = Modifier, icon: String? = null, subtitle: String? = null) {
    ListRow(title, modifier, subtitle = subtitle, leading = icon?.let { { FormIcon(it) } }, trailing = {
        Text(value, style = Theme.type.style(14, 500), color = Theme.scheme.onSurfaceVariant, maxLines = 1)
    })
}

/** Riga che fa un'azione (pulsante in un elenco). */
@Composable
fun ButtonRow(title: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false, enabled: Boolean = true, subtitle: String? = null) {
    ListRow(
        title, modifier, subtitle = subtitle, onClick = onClick, enabled = enabled, destructive = destructive,
        titleColor = if (destructive) Theme.scheme.error else Theme.scheme.primary,
        leading = { FormIcon(icon, if (destructive) Theme.scheme.error else Theme.scheme.primary) },
    )
}

/** Riga che apre una pagina. */
@Composable
fun NavigationRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, badge: Int = 0) {
    ListRow(title, modifier, subtitle = subtitle, leading = leading, onClick = onClick, trailing = {
        if (badge > 0) CountBadge(badge)
        ChevronRight()
    })
}

@Composable
fun Gap(size: Dp) = Spacer(Modifier.size(size))

@Composable
fun HGap(size: Dp) = Spacer(Modifier.width(size))
