package com.bcw.classeviva.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icone Material Symbols Rounded per nome (come `icon(name)` del web e `Image.m` dell'app iOS). */
object Symbols {
    private val cache = HashMap<String, ImageVector>()

    fun has(name: String) = IconPaths.paths.containsKey(name)

    fun vector(name: String): ImageVector = synchronized(cache) {
        cache.getOrPut(name) {
            val data = IconPaths.paths[name] ?: IconPaths.paths.getValue("circle")
            val box = IconPaths.viewBoxes[name]
            if (box != null) {
                ImageVector.Builder(name, 24.dp, 24.dp, box[2], box[3]).apply {
                    group(translationX = -box[0], translationY = -box[1]) {
                        addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black))
                    }
                }.build()
            } else {
                ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f).apply {
                    group(translationY = 960f) {
                        addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
                    }
                }.build()
            }
        }
    }
}

@Composable
fun Icon(name: String, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current, contentDescription: String? = null) {
    androidx.compose.material3.Icon(
        imageVector = Symbols.vector(name), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size),
    )
}
