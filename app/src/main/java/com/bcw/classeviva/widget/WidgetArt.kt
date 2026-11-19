package com.bcw.classeviva.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.bcw.classeviva.R
import com.bcw.classeviva.ui.icons.IconPaths
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ciò che i widget disegnano come immagine. Android non carica i font dell'app nei widget (il
 * contesto in cui il launcher li gonfia è ristretto e `TextView` ignora `@font`), quindi titoli e
 * numeri in Google Sans Flex sono disegnati qui. Testo e icone escono come maschere `ALPHA_8`
 * (un byte per pixel) che il widget colora con `ColorFilter.tint`: lo stesso disegno vale per il tema
 * chiaro e per lo scuro, e pesa un quarto di un'immagine a colori.
 */
object WidgetArt {
    private const val RING_WAVES = 11
    private const val LOBES = 9

    /** Un testo disegnato e le sue misure in dp (per dare all'immagine la dimensione giusta). */
    class Drawn(val bitmap: Bitmap, val widthDp: Float, val heightDp: Float)

    private var typeface: Typeface? = null

    private fun typeface(context: Context): Typeface =
        typeface ?: (ResourcesCompat.getFont(context, R.font.google_sans_flex) ?: Typeface.DEFAULT).also { typeface = it }

    /**
     * Testo in Google Sans Flex con gli assi dell'app (`wght`, `ROND`, `opsz`), a capo entro
     * `maxWidthDp` e con i puntini dopo `maxLines` righe.
     */
    fun text(
        context: Context, text: String, sizeSp: Float, weight: Int, rond: Int, maxWidthDp: Float,
        maxLines: Int = 1, center: Boolean = false, tabular: Boolean = false,
    ): Drawn {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            fontVariationSettings = "'wght' $weight, 'ROND' $rond, 'opsz' ${sizeSp.coerceIn(6f, 144f)}"
            if (tabular) fontFeatureSettings = "tnum"
            color = android.graphics.Color.WHITE
        }
        val maxWidth = max(1, (maxWidthDp * density).roundToInt())
        val natural = ceil(Layout.getDesiredWidth(text, paint)).toInt() + 2
        val width = min(maxWidth, max(1, natural))
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false).setLineSpacing(0f, 1.05f).build()
        val lineWidth = (0 until layout.lineCount).maxOfOrNull { ceil(layout.getLineWidth(it)).toInt() } ?: 1
        val w = if (center) width else min(width, max(1, lineWidth + 2))
        val h = max(1, layout.height)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        layout.draw(Canvas(bitmap))
        return Drawn(bitmap, w / density, h / density)
    }

    /** Un'icona Material Symbols dagli stessi tracciati dell'app (riquadro 0 -960 960 960). */
    fun icon(context: Context, name: String, sizeDp: Float): Bitmap {
        val px = max(1, (sizeDp * context.resources.displayMetrics.density).roundToInt())
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8)
        val data = IconPaths.paths[name] ?: return bitmap
        val path = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return bitmap
        val box = IconPaths.viewBoxes[name]
        val matrix = Matrix()
        if (box != null) {
            matrix.setTranslate(-box[0], -box[1])
            matrix.postScale(px / box[2], px / box[3])
        } else {
            matrix.setTranslate(0f, 960f)
            matrix.postScale(px / 960f, px / 960f)
        }
        path.transform(matrix)
        Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        return bitmap
    }

    /**
     * Anello ondulato come `AverageRing` della app, come maschera: con `progress` 1 la traccia intera,
     * altrimenti il tratto dell'avanzamento (0..1) dall'alto in senso orario.
     */
    fun ringMask(progress: Float, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8)
        if (progress <= 0f) return bitmap
        val stroke = px * 0.09f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.WHITE
        }
        val center = px / 2f
        val base = center - stroke - px * 0.02f
        val amp = px * 0.03f
        Canvas(bitmap).drawPath(wavy(center, base, amp, RING_WAVES, 0.0, 2 * PI * progress.coerceAtMost(1f), (240 * progress).toInt().coerceAtLeast(8)), paint)
        return bitmap
    }

    /** Biscotto a nove lobi (o cerchio), come maschera da colorare. */
    fun badge(context: Context, sizeDp: Float, lobed: Boolean): Bitmap {
        val px = max(1, (sizeDp * context.resources.displayMetrics.density).roundToInt())
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8)
        val center = px / 2f
        val base = px / 2f * 0.9f
        val amp = if (lobed) base * 0.07f else 0f
        Canvas(bitmap).drawPath(
            wavy(center, base, amp, if (lobed) LOBES else 1, 0.0, 2 * PI, 240, closed = true),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE },
        )
        return bitmap
    }

    private fun wavy(cx: Float, base: Float, amp: Float, waves: Int, from: Double, to: Double, steps: Int, closed: Boolean = false): Path {
        val path = Path()
        for (i in 0..steps) {
            val t = from + (to - from) * i / steps
            val r = base + amp * sin(waves * t).toFloat()
            val x = cx + r * cos(t - PI / 2).toFloat()
            val y = cx + r * sin(t - PI / 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (closed) path.close()
        return path
    }
}
