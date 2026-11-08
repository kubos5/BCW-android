package com.bcw.classeviva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.bcw.classeviva.ui.theme.Fonts
import com.bcw.classeviva.ui.theme.Theme

/**
 * Logo di BCW: la scritta in Google Sans Flex (peso 800, arrotondata) e la linea nel colore del
 * marchio, accanto o sotto (`stacked`, come nell'introduzione e nell'icona dell'app).
 */
@Composable
fun BCWLogo(modifier: Modifier = Modifier, height: Dp = 32.dp, stacked: Boolean = false) {
    val size = height.value
    val style = Theme.type.style(size, 800, rounded = true).copy(
        fontFamily = Fonts.family(800, 100, size.toInt()), letterSpacing = (-0.01).em,
        lineHeight = 1.em, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val bar = @Composable { m: Modifier ->
        Box(m.width(height * 1.1f).height(height * 0.15f).clip(CircleShape).background(Theme.colors.brand))
    }
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(height * 0.18f)) {
            Text("BCW", style = style, color = Theme.scheme.onSurface)
            bar(Modifier)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(height * 0.42f)) {
            Text("BCW", style = style, color = Theme.scheme.onSurface)
            bar(Modifier.offset(y = height * 0.02f))
        }
    }
}
