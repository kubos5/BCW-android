package com.bcw.classeviva.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bcw.classeviva.ui.components.EmptyState
import com.bcw.classeviva.ui.icons.Icon
import com.bcw.classeviva.ui.shell.LocalUi
import com.bcw.classeviva.ui.shell.ViewerFile
import com.bcw.classeviva.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Anteprima dei file (come Quick Look nell'app iOS e `js/viewer.js` sul web): immagini e PDF con lo
 * zoom, testo; per il resto "Apri con…". Sempre: condividi e apri con un'altra app.
 */
@Composable
fun FileViewer(file: ViewerFile, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Theme.scheme.surfaceContainerLowest)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon("x", contentDescription = "Chiudi") }
                Text(file.name, style = Theme.type.style(18, 500, rounded = true), color = Theme.scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { ui.share(file.file, file.type) }) { Icon("share", contentDescription = "Condividi") }
                IconButton(onClick = { ui.openExternally(file.file, file.type) }) { Icon("externalLink", contentDescription = "Apri con un'altra app") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    file.type.startsWith("image/") -> ImageContent(file.file)
                    file.type == "application/pdf" || file.name.endsWith(".pdf", true) -> PdfContent(file.file)
                    file.type.startsWith("text/") -> TextContent(file.file)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState("Anteprima non disponibile", "file", "Apri il file con un'altra app.") {
                            Button(onClick = { ui.openExternally(file.file, file.type) }) { Text("Apri con…") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Zoomable(content: @Composable (Modifier) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().transformable(state), contentAlignment = Alignment.Center) {
        content(Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
    }
}

private fun decodeSampled(file: File, maxSide: Int = 4096): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun ImageContent(file: File) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { runCatching { decodeSampled(file) }.getOrNull() }
        failed = bitmap == null
    }
    val b = bitmap
    when {
        b != null -> Zoomable { m -> Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = m.fillMaxSize()) }
        failed -> EmptyState("Immagine non leggibile", "image")
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

@Composable
private fun PdfContent(file: File) {
    var pages by remember(file) { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        pages = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        (0 until minOf(renderer.pageCount, 60)).map { i ->
                            renderer.openPage(i).use { page ->
                                val width = 1400
                                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }
            }.getOrNull()
        }
        failed = pages == null
    }
    val list = pages
    when {
        list != null -> Zoomable { m ->
            LazyColumn(m.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list) { bmp ->
                    Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.radii.s)).background(Color.White))
                }
            }
        }
        failed -> EmptyState("PDF non leggibile", "fileText", "Prova ad aprirlo con un'altra app.")
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
}

@Composable
private fun TextContent(file: File) {
    var text by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) { text = withContext(Dispatchers.IO) { runCatching { file.readText().take(200_000) }.getOrDefault("") } }
    SelectionContainer {
        Text(text ?: "", style = Theme.type.style(15), color = Theme.scheme.onSurface,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding())
    }
}
