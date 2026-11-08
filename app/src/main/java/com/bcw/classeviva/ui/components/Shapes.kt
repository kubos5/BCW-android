package com.bcw.classeviva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon

/** Forme di Material 3 Expressive per nome (le stesse di `js/shapes.js` e `MaterialShape` dell'app). */
object ShapeNames {
    fun polygon(name: String): RoundedPolygon = when (name) {
        "circle" -> MaterialShapes.Circle
        "square" -> MaterialShapes.Square
        "slanted" -> MaterialShapes.Slanted
        "arch" -> MaterialShapes.Arch
        "fan" -> MaterialShapes.Fan
        "arrow" -> MaterialShapes.Arrow
        "semiCircle" -> MaterialShapes.SemiCircle
        "oval" -> MaterialShapes.Oval
        "pill" -> MaterialShapes.Pill
        "triangle" -> MaterialShapes.Triangle
        "diamond" -> MaterialShapes.Diamond
        "clamShell" -> MaterialShapes.ClamShell
        "pentagon" -> MaterialShapes.Pentagon
        "gem" -> MaterialShapes.Gem
        "verySunny" -> MaterialShapes.VerySunny
        "sunny" -> MaterialShapes.Sunny
        "cookie4Sided" -> MaterialShapes.Cookie4Sided
        "cookie6Sided" -> MaterialShapes.Cookie6Sided
        "cookie7Sided" -> MaterialShapes.Cookie7Sided
        "cookie9Sided" -> MaterialShapes.Cookie9Sided
        "cookie12Sided" -> MaterialShapes.Cookie12Sided
        "ghostish" -> MaterialShapes.Ghostish
        "clover4Leaf" -> MaterialShapes.Clover4Leaf
        "clover8Leaf" -> MaterialShapes.Clover8Leaf
        "burst" -> MaterialShapes.Burst
        "softBurst" -> MaterialShapes.SoftBurst
        "boom" -> MaterialShapes.Boom
        "softBoom" -> MaterialShapes.SoftBoom
        "flower" -> MaterialShapes.Flower
        "puffy" -> MaterialShapes.Puffy
        "puffyDiamond" -> MaterialShapes.PuffyDiamond
        "pixelCircle" -> MaterialShapes.PixelCircle
        "pixelTriangle" -> MaterialShapes.PixelTriangle
        "bun" -> MaterialShapes.Bun
        "heart" -> MaterialShapes.Heart
        else -> MaterialShapes.Circle
    }

    @Composable
    fun shape(name: String): Shape = polygon(name).toShape()

    val all = listOf("cookie12Sided", "clover4Leaf", "softBurst", "sunny", "cookie7Sided", "flower", "puffy", "gem")
}

/** Contenuto al centro di una forma espressiva piena (es. icone di stato, avatar, voti). */
@Composable
fun Shaped(
    shape: String,
    fill: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().rotate(rotation).clip(ShapeNames.shape(shape)).background(fill))
        content()
    }
}
