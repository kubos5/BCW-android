package com.bcw.classeviva.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.data.TrendPoint
import com.bcw.classeviva.util.Fmt
import com.bcw.classeviva.ui.theme.Theme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ChartDot(val date: LocalDate, val value: Double, val color: Color)

/**
 * Andamento della media in stile Material 3 Expressive: linea spessa e arrotondata con l'area
 * tonale sotto, la fascia dell'insufficienza nel contenitore rosso e l'ultimo valore segnato da un
 * punto grande; sotto i mesi.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    domain: ClosedFloatingPointRange<Double> = 3.0..10.0,
    ticks: List<Int> = listOf(4, 6, 8, 10),
    color: Color = Theme.scheme.primary,
    dots: List<ChartDot> = emptyList(),
    area: Boolean = true,
    lastDot: Boolean = true,
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = Theme.type.style(11, 550).copy(color = Theme.scheme.onSurfaceVariant)
    val grid = Theme.scheme.outlineVariant
    val band = Theme.colors.poor.container.copy(alpha = 0.55f)
    val surface = Theme.scheme.surfaceContainer
    val reveal = remember(points) { Animatable(0f) }
    val spec = Theme.motion.slowSpatialSpec<Float>()
    LaunchedEffect(points) { reveal.animateTo(1f, spec) }
    val first = minOf(points.first().date, dots.minOfOrNull { it.date } ?: points.first().date)
    val last = maxOf(points.last().date, dots.maxOfOrNull { it.date } ?: points.last().date)
    val span = maxOf(1L, ChronoUnit.DAYS.between(first, last)).toFloat()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val left = 28.dp.toPx()
        val right = 10.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun x(d: LocalDate) = left + w * (ChronoUnit.DAYS.between(first, d) / span)
        fun y(v: Double) = top + h * (1f - ((v.coerceIn(domain.start, domain.endInclusive) - domain.start) / (domain.endInclusive - domain.start)).toFloat())
        // Fascia dell'insufficienza.
        drawRoundRect(band, Offset(left, y(6.0)), Size(w, y(domain.start) - y(6.0)), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        for (t in ticks) {
            val ty = y(t.toDouble())
            drawLine(grid, Offset(left, ty), Offset(left + w, ty), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(t.toString(), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 8.dp.toPx(), ty - layout.size.height / 2f))
        }
        // Mesi sotto il grafico.
        var month = first.withDayOfMonth(1)
        while (!month.isAfter(last)) {
            val mx = x(if (month < first) first else month)
            val layout = measurer.measure(Fmt.monthShort(month), labelStyle)
            if (mx + layout.size.width <= size.width) drawText(layout, topLeft = Offset(mx, size.height - layout.size.height))
            month = month.plusMonths(1)
        }
        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.date), y(p.value)) else line.lineTo(x(p.date), y(p.value)) }
        clipRect(right = left + w * reveal.value + 6.dp.toPx()) {
            if (area && points.size > 1) {
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(points.last().date), top + h)
                    lineTo(x(points.first().date), top + h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), startY = top, endY = top + h))
            }
            drawPath(line, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (d in dots) {
                drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(d.date), y(d.value)))
                drawCircle(d.color, radius = 4.5.dp.toPx(), center = Offset(x(d.date), y(d.value)))
            }
            if (lastDot) {
                val p = points.last()
                drawCircle(surface, radius = 9.dp.toPx(), center = Offset(x(p.date), y(p.value)))
                drawCircle(color, radius = 6.5.dp.toPx(), center = Offset(x(p.date), y(p.value)))
            }
        }
    }
}
