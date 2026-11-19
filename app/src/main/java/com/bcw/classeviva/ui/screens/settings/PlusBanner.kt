package com.bcw.classeviva.ui.screens.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bcw.classeviva.ui.components.ShapeNames
import com.bcw.classeviva.ui.components.Shaped
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalModel
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.theme.Theme

/**
 * Banner in cima a Tu › Impostazioni, come il banner "Build GitHub" di iOS 4.0 (`PlusBanner`, build
 * normale). Su Android BCW+ non si compra: tutte le funzioni sono sempre sbloccate (richiesta
 * dell'utente). Si nasconde con la X e si rimostra da Impostazioni › Generale.
 */
@Composable
fun PlusBanner() {
    val model = LocalModel.current
    val ui = LocalUi.current
    val s = Theme.scheme
    val ink = s.onSecondaryContainer
    val reduced = Theme.reducedMotion
    val turn = rememberInfiniteTransition(label = "plusBanner")
    val angle by turn.animateFloat(0f, if (reduced) 0f else 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "angle")

    Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(Theme.radii.xl)).background(s.secondaryContainer)) {
        // Forme decorative: una grande che sporge dall'angolo in basso a destra e ruota piano, una piccola sopra.
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 46.dp, y = 58.dp).size(150.dp)
                .rotate(angle).clip(ShapeNames.shape("clover8Leaf")).background(s.secondary.copy(alpha = 0.1f)),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = (-96).dp, y = (-60).dp).size(44.dp)
                .clip(ShapeNames.shape("clover4Leaf")).background(s.secondary.copy(alpha = 0.08f)),
        )
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Shaped("cookie12Sided", s.secondary, 52.dp) { Icon("android", size = 28.dp, tint = s.onSecondary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("BCW per Android", style = Theme.type.style(20, 650, rounded = true), color = ink)
                Text(
                    "Le funzioni BCW+ sono automaticamente sbloccate su Android",
                    style = Theme.type.style(13, lineHeight = 18.sp), color = ink.copy(alpha = 0.82f),
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f))
                    .clickable(role = Role.Button, onClickLabel = "Nascondi il banner") {
                        model.preferences.set("showsPlusBanner", false)
                        ui.toast("Banner nascosto: lo ritrovi in Impostazioni › Generale")
                    },
                contentAlignment = Alignment.Center,
            ) { Icon("x", size = 20.dp, tint = ink, contentDescription = "Nascondi il banner") }
        }
    }
}
