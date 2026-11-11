package com.neonstereo.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonstereo.audio.AudioFrame
import com.neonstereo.elements.ElementInstance
import com.neonstereo.elements.boolOr
import com.neonstereo.elements.floatOr
import com.neonstereo.elements.stringOr
import com.neonstereo.media.MediaState
import com.neonstereo.visuals.bandEnergy
import com.neonstereo.visuals.bandFiltered
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Image as CImage

fun hexColor(hex: String, fallback: Color = Color.Cyan): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { fallback }

private val ledTypefaceCache = mutableMapOf<String, Typeface>()
fun assetFont(name: String, ctx: android.content.Context): FontFamily = try {
    val tf = ledTypefaceCache.getOrPut(name) { Typeface.createFromAsset(ctx.assets, "fonts/$name") }
    FontFamily(tf)
} catch (_: Exception) { FontFamily.Monospace }

@Composable
fun ClockWidget(el: ElementInstance, opacity: Float) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(el.specific.stringOr("seconds", "false").ifEmpty { "false" }) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (el.specific.boolOr("seconds", false)) 1000L else 5000L)
        }
    }
    val h24 = el.specific.boolOr("h24", true)
    val seconds = el.specific.boolOr("seconds", false)
    val pattern = when {
        h24 && seconds -> "HH:mm:ss"
        h24 -> "HH:mm"
        seconds -> "hh:mm:ss a"
        else -> "hh:mm a"
    }
    val text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    val font = when (el.specific.stringOr("font", "led")) {
        "led" -> assetFont("lcddot_tr.ttf", ctx)
        "seven" -> assetFont("Seven Segment.ttf", ctx)
        "mono" -> FontFamily.Monospace
        "sans" -> FontFamily.SansSerif
        else -> assetFont("lcddot_tr.ttf", ctx)
    }
    Text(
        text = text,
        fontFamily = font,
        fontSize = el.specific.floatOr("fontSize", 64f).coerceIn(8f, 240f).sp,
        color = hexColor(el.colorHex).copy(alpha = opacity * el.opacity),
        style = TextStyle(shadow = Shadow(hexColor(el.colorHex), Offset(0f, 0f), el.glow * 24f)),
    )
}

@Composable
fun DateWidget(el: ElementInstance, opacity: Float) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000L) } }
    val text = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date(now))
    Text(text, fontSize = el.specific.floatOr("fontSize", 18f).coerceIn(8f, 160f).sp, color = Color.White.copy(alpha = opacity * el.opacity))
}

@Composable
fun CoverWidget(el: ElementInstance, media: MediaState, opacity: Float, onTap: () -> Unit) {
    val bmp = media.cover
    Box(Modifier.pointerInput(Unit) { detectTapGestures { onTap() } }) {
        if (bmp != null) {
            CImage(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Portada",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (el.specific.boolOr("fit", false)) ContentScale.Fit else ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A12)))
        }
    }
}

@Composable
fun ControlsWidget(media: MediaState, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, tint: Color) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, enabled = media.canPrev, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = tint)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
            Icon(if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play", tint = tint)
        }
        IconButton(onClick = onNext, enabled = media.canNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = tint)
        }
    }
}

@Composable
fun ProgressWidget(el: ElementInstance, media: MediaState, opacity: Float, onSeek: (Long) -> Unit, editing: Boolean) {
    val frac = if (media.durationMs > 0) (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f) else 0f
    val thick = el.specific.floatOr("thickness", 6f).toInt().coerceIn(2, 24)
    Box(
        Modifier.fillMaxWidth().height((thick + 16).dp).pointerInput(media.canSeek, editing) {
            if (!editing && media.canSeek) detectTapGestures { off ->
                onSeek((frac * media.durationMs).toLong())
            }
        }
    ) {
        LinearProgressIndicator(
            progress = frac,
            modifier = Modifier.fillMaxWidth().height(thick.dp).align(Alignment.Center),
            color = hexColor(el.colorHex).copy(alpha = opacity),
            trackColor = Color.White.copy(alpha = 0.15f),
        )
    }
}

@Composable
fun MarqueeText(text: String, color: Color, fontSize: Float = 22f) {
    var shift by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) { while (text.length > 24) { delay(120L); shift = (shift + 1) % (text.length + 4) } }
    val shown = if (text.length <= 24) text else (text + "   " + text).drop(shift % (text.length + 3)).take(28)
    Text(shown, color = color, fontSize = fontSize.coerceIn(8f, 160f).sp, maxLines = 1)
}

/** Barras de espectro con datos reales; detenidas (vacío) si no hay captura. */
@Composable
fun SpectrumBars(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val sens = el.specific.floatOr("sensitivity", 1f)
    val bands = el.specific.floatOr("bands", 32f).toInt().coerceIn(4, 64)
    val band = el.specific.stringOr("band", "all")
    val col = hexColor(el.colorHex)
    Canvas(Modifier.fillMaxSize()) {
        if (!frame.capturing || frame.bins.isEmpty()) return@Canvas
        val vals = bandFiltered(frame.bins, band)
        val n = bands
        val bw = size.width / n
        for (i in 0 until n) {
            val v = vals[(i * vals.size / n).coerceIn(vals.indices)] * sens
            val h = (v.coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            drawRect(col.copy(alpha = opacity), Offset(i * bw + 1, size.height - h), androidx.compose.ui.geometry.Size(bw - 2, h))
        }
    }
}

@Composable
fun EdgeBars(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val sens = el.specific.floatOr("sensitivity", 1f)
    val bands = el.specific.floatOr("bands", 48f).toInt().coerceIn(8, 64)
    val col = hexColor(el.colorHex)
    Canvas(Modifier.fillMaxSize()) {
        if (!frame.capturing || frame.bins.isEmpty()) return@Canvas
        val n = bands
        val bw = size.width / n
        val topH = size.height * 0.35f
        val botH = size.height * 0.35f
        for (i in 0 until n) {
            val v = (frame.bins[(i * frame.bins.size / n).coerceIn(frame.bins.indices)] * sens).coerceIn(0f, 1f)
            drawRect(col.copy(alpha = opacity), Offset(i * bw + 1, 0f), androidx.compose.ui.geometry.Size(bw - 2, (v * topH).coerceAtLeast(2f)))
            val bh = (v * botH).coerceAtLeast(2f)
            drawRect(col.copy(alpha = opacity), Offset(i * bw + 1, size.height - bh), androidx.compose.ui.geometry.Size(bw - 2, bh))
        }
    }
}

@Composable
fun WaveWidget(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val amp = el.specific.floatOr("amplitude", 0.8f)
    val col = hexColor(el.colorHex)
    Canvas(Modifier.fillMaxSize()) {
        if (!frame.capturing || frame.waveform.isEmpty()) return@Canvas
        val w = frame.waveform
        var prev: Offset? = null
        for (i in w.indices step 4) {
            val x = i.toFloat() / w.size * size.width
            val y = size.height / 2 + w[i] * size.height / 2 * amp
            val p = Offset(x, y)
            prev?.let { drawLine(col.copy(alpha = opacity), it, p, strokeWidth = 3f) }
            prev = p
        }
    }
}

@Composable
fun DotEq(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val rows = el.specific.floatOr("rows", 12f).toInt().coerceIn(4, 24)
    val cols = el.specific.floatOr("cols", 24f).toInt().coerceIn(8, 48)
    val sens = el.specific.floatOr("sensitivity", 1f)
    val col = hexColor(el.colorHex)
    Canvas(Modifier.fillMaxSize()) {
        if (!frame.capturing || frame.bins.isEmpty()) return@Canvas
        val cw = size.width / cols; val ch = size.height / rows
        for (c in 0 until cols) {
            val v = (frame.bins[(c * frame.bins.size / cols).coerceIn(frame.bins.indices)] * sens).coerceIn(0f, 1f)
            val lit = (v * rows).toInt()
            for (r in 0 until rows) {
                val on = r < lit
                drawCircle(
                    if (on) col.copy(alpha = opacity) else Color.White.copy(alpha = 0.12f),
                    radius = (cw.coerceAtMost(ch) * 0.3f).coerceAtLeast(2f),
                    center = Offset(c * cw + cw / 2, size.height - r * ch - ch / 2),
                )
            }
        }
    }
}

@Composable
fun RadialWidget(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val bands = el.specific.floatOr("bands", 48f).toInt().coerceIn(12, 96)
    val sens = el.specific.floatOr("sensitivity", 1f)
    val col = hexColor(el.colorHex)
    Canvas(Modifier.fillMaxSize()) {
        if (!frame.capturing || frame.bins.isEmpty()) return@Canvas
        val cx = size.width / 2; val cy = size.height / 2
        val base = size.minDimension * 0.25f
        for (i in 0 until bands) {
            val v = (frame.bins[(i * frame.bins.size / bands).coerceIn(frame.bins.indices)] * sens).coerceIn(0f, 1f)
            val a = i.toFloat() / bands * 2 * PI
            val r1 = base; val r2 = base + v * base * 1.5f + 4f
            drawLine(
                col.copy(alpha = opacity),
                Offset(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1),
                Offset(cx + cos(a).toFloat() * r2, cy + sin(a).toFloat() * r2),
                strokeWidth = 4f,
            )
        }
        drawCircle(col.copy(alpha = opacity * 0.4f), radius = base, center = Offset(cx, cy), style = Stroke(2f))
    }
}

@Composable
fun StarParticles(el: ElementInstance, frame: AudioFrame, opacity: Float) {
    val count = el.specific.floatOr("count", 120f).toInt().coerceIn(10, 400)
    val band = el.specific.stringOr("band", "all")
    val sens = el.specific.floatOr("sensitivity", 1f)
    val col = hexColor(el.colorHex)
    val e = bandEnergy(if (frame.bins.isEmpty()) FloatArray(64) else frame.bins, band) * sens
    // Sin captura: detenidos, sin movimiento residual (§4.7).
    if (!frame.capturing) return
    var t by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(50L); t++ } }
    Canvas(Modifier.fillMaxSize()) {
        val n = count.coerceAtMost(400)
        for (i in 0 until n) {
            val seed = (i * 2654435761L) and 0x7fffffff
            val sx = ((seed % 1000) / 1000f * size.width)
            val sy = (((seed / 1000) % 1000) / 1000f * size.height)
            val tw = 0.3f + 0.7f * ((t * 0.05f + i) % 10 / 10f)
            val r = (1f + e * 6f) * tw
            drawCircle(col.copy(alpha = (opacity * (0.25f + e.coerceIn(0f, 1f) * 0.75f)).coerceIn(0f, 1f)), radius = r.coerceIn(1f, 8f), center = Offset(sx, sy))
        }
    }
}

/** Fondo: negro / portada difuminada / imagen custom. 75%/80% -> sigma 25 + scrim 0.8. */
@Composable
fun BackgroundLayer(el: ElementInstance, coverBmp: android.graphics.Bitmap?, customUri: android.net.Uri?, opacity: Float, pulse: Float) {
    val mode = el.specific.stringOr("mode", "blur_cover")
    val dim = el.specific.floatOr("dim", 0.8f)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            "black" -> Box(Modifier.fillMaxSize().background(Color.Black))
            "custom" -> {
                if (customUri != null) {
                    val ctx = LocalContext.current
                    val bmp = remember(customUri) {
                        try { ctx.contentResolver.openInputStream(customUri)?.use { android.graphics.BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
                    }
                    if (bmp != null) CImage(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            else -> {
                if (coverBmp != null) {
                    // Sin blur real caro: dibujar portada escalada + scrim oscuro (equivalencia documentada).
                    CImage(coverBmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
                }
            }
        }
        val pulseAlpha = (pulse * 0.25f).coerceIn(0f, 0.25f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dim - pulseAlpha).coerceIn(0f, 1f))))
    }
}

fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SwipeGestures(onPrev: () -> Unit, onNext: () -> Unit, editing: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(editing) {
        if (!editing) detectHorizontalDragGestures { _, drag ->
            if (drag > 60) onPrev() else if (drag < -60) onNext()
        }
    }) { content() }
}
