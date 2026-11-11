package com.neonstereo.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class CaptureMethod { NONE, VISUALIZER, PLAYBACK_CAPTURE }
enum class Linkage { UNKNOWN, MIX }

data class AudioFrame(
    /** Magnitudes 0..1 por bin FFT (64). Vacío = sin señal válida. */
    val bins: FloatArray = FloatArray(0),
    val waveform: FloatArray = FloatArray(0),
    val energy: Float = 0f,
    val capturing: Boolean = false,
    val method: CaptureMethod = CaptureMethod.NONE,
    val linkage: Linkage = Linkage.UNKNOWN,
    val reason: String = "",
)

class AudioCaptureManager(private val ctx: Context, private val scope: CoroutineScope) {
    private val _frame = MutableStateFlow(AudioFrame(reason = "sin iniciar"))
    val frame: StateFlow<AudioFrame> = _frame
    /** FPS real del renderer musical (emisiones/seg, objetivo ~30). */
    private val _renderFps = MutableStateFlow(0f)
    val renderFps: StateFlow<Float> = _renderFps
    private var fpsCount = 0
    private var fpsWindowStart = System.nanoTime()
    private var lastEmit = 0L

    private var visualizer: Visualizer? = null
    private var record: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var captureJob: Job? = null

    // Suavizado ataque/decaimiento por bin.
    private val smooth = FloatArray(64)
    var attack: Float = 0.5f
    var decay: Float = 0.82f

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Intento Visualizer sesión 0 (puede dar silencio / no soportado, §8). */
    fun startVisualizer(): Boolean = startVisualizerOn(0)

    /**
     * Cadena Visualizer: reintenta con tamaños de captura decrecientes
     * (algunas ROM fallan con buffers grandes) y mapea el error a
     * diagnóstico accionable. `pkg` se registra para futura vinculación.
     */
    fun tryVisualizerChain(pkg: String): Boolean {
        val sizes = try {
            Visualizer.getCaptureSizeRange()
        } catch (_: Exception) { intArrayOf(128, 1024) }
        val candidates = listOf(512, 256, sizes[0]).distinct()
            .filter { it in sizes[0]..sizes[1] }
            .ifEmpty { listOf(sizes[0]) }
        var lastErr: Exception? = null
        for (s in candidates) {
            if (startVisualizerOn(0, s)) return true
            lastErr = null
        }
        if (!pkg.isBlank()) diagHintPkg = pkg
        return false
    }

    @Volatile private var diagHintPkg: String = ""

    private fun startVisualizerOn(sessionId: Int, size: Int = 512): Boolean {
        stopAll(reason = "cambio de método")
        return try {
            val v = Visualizer(sessionId)
            val maxRate = Visualizer.getMaxCaptureRate()
            // captureSize chico = más compatible (algunas ROM fallan con 1024).
            val sizes = Visualizer.getCaptureSizeRange()
            v.captureSize = size.coerceIn(sizes[0], sizes[1])
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(viz: Visualizer?, data: ByteArray?, rate: Int) {
                    if (data == null) return
                    val wave = FloatArray(data.size) { i -> (data[i].toFloat() / 128f) }
                    pushWave(wave, CaptureMethod.VISUALIZER)
                }
                override fun onFftDataCapture(viz: Visualizer?, data: ByteArray?, rate: Int) {
                    if (data == null) return
                    pushFft(data, CaptureMethod.VISUALIZER)
                }
            }, maxRate / 2, true, true)
            v.enabled = true
            visualizer = v
            true
        } catch (e: Exception) {
            _frame.value = AudioFrame(reason = mapVisualizerError(e, sessionId))
            false
        }
    }

    private fun mapVisualizerError(e: Exception, sessionId: Int): String {
        val m = (e.message ?: "").lowercase()
        val where = if (sessionId == 0) "sesión 0 global" else "sesión $sessionId"
        return when {
            m.contains("-3") || m.contains("no_init") || m.contains("cannot initialize") ->
                "Visualizer no disponible ($where): motor bloqueado por ROM/EQ u otra app (error -3). " +
                "Desactivá ecualizador/Dolby o probá captura de reproducción."
            m.contains("-4") -> "Visualizer rechazó parámetros ($where, error -4)."
            m.contains("-5") -> "Visualizer operación inválida ($where, error -5)."
            else -> "Visualizer no disponible ($where): ${e.message}"
        }
    }

    /** Captura de reproducción oficial con MediaProjection (requiere consentimiento por sesión). */
    fun startPlaybackCapture(projection: MediaProjection) {
        stopAll(reason = "cambio de método")
        this.projection = projection
        if (!hasRecordPermission()) {
            _frame.value = AudioFrame(reason = "RECORD_AUDIO denegado")
            return
        }
        // Probar 44100 y caer a 48000 según HAL del equipo.
        val tried = StringBuilder()
        for (rate in listOf(44100, 48000)) {
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) { tried.append("$rate(buf inválido) "); continue }
                val rec = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    try { rec.release() } catch (_: Exception) {}
                    tried.append("$rate(no inicializado) "); continue
                }
                record = rec
                rec.startRecording()
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    try { rec.stop(); rec.release() } catch (_: Exception) {}
                    record = null
                    tried.append("$rate(no graba: app bloquea captura) "); continue
                }
                captureJob = scope.launch(Dispatchers.Default) {
                    val buf = ShortArray(2048)
                    val fft = Fft(2048)
                    while (true) {
                        val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                        if (n <= 0) continue
                        val mags = fft.magnitudes(buf, n)
                        pushBins(mags, CaptureMethod.PLAYBACK_CAPTURE)
                    }
                }
                return
            } catch (e: Exception) {
                tried.append("$rate(${e.message}) ")
            }
        }
        _frame.value = AudioFrame(reason = "PlaybackCapture falló [$tried]. La app/ROM puede bloquear captura.")
    }

    fun projectionIntent(): android.content.Intent? {
        val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return try { mgr.createScreenCaptureIntent() } catch (_: Exception) { null }
    }

    fun onProjectionResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            _frame.value = AudioFrame(reason = "consentimiento MediaProjection cancelado")
            return
        }
        val mgr = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val p = mgr.getMediaProjection(resultCode, data)
            if (Build.VERSION.SDK_INT >= 34) {
                try { p.registerCallback(object : MediaProjection.Callback() {}, null) } catch (_: Exception) {}
            }
            startPlaybackCapture(p)
        } catch (e: Exception) {
            _frame.value = AudioFrame(reason = "MediaProjection falló: ${e.message}")
        }
    }

    private fun countRenderTick() {
        fpsCount++
        val now = System.nanoTime()
        if (now - fpsWindowStart >= 1_000_000_000L) {
            _renderFps.value = fpsCount * 1_000_000_000f / (now - fpsWindowStart)
            fpsCount = 0; fpsWindowStart = now
        }
    }

    /** Pausa: decaimiento a cero y detener animación (§8.2). Llamar al pausar música u ocultar. */
    fun fadeOut() {
        for (i in smooth.indices) smooth[i] *= 0.5f
        _frame.value = AudioFrame(bins = smooth.copyOf(), waveform = FloatArray(0), energy = 0f, capturing = false, method = _frame.value.method, linkage = Linkage.UNKNOWN, reason = "pausado")
    }

    fun stopAll(reason: String = "oculto") {
        captureJob?.cancel(); captureJob = null
        try { record?.stop(); record?.release() } catch (_: Exception) {}
        record = null
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        smooth.fill(0f)
        _frame.value = AudioFrame(reason = reason)
    }

    private fun pushFft(data: ByteArray, method: CaptureMethod) {
        // FFT Visualizer: pares (real, imag), n/2 puntos.
        val n = data.size / 2
        val mags = FloatArray(64)
        val per = (n / 64).coerceAtLeast(1)
        for (b in 0 until 64) {
            var acc = 0f
            for (k in 0 until per) {
                val idx = (b * per + k) * 2
                if (idx + 1 >= data.size) break
                val re = data[idx].toFloat(); val im = data[idx + 1].toFloat()
                acc += sqrt(re * re + im * im) / 128f
            }
            mags[b] = (acc / per).coerceIn(0f, 1f)
        }
        pushBins(mags, method)
    }

    private fun pushWave(wave: FloatArray, method: CaptureMethod) {
        val e = wave.map { it * it }.average().toFloat()
        val now = System.nanoTime()
        if (now - lastEmit < 33_000_000L && _frame.value.capturing) return
        lastEmit = now
        countRenderTick()
        // Si solo hay onda sin FFT válida, igual es señal real (no inventar espectro).
        val cur = _frame.value
        _frame.value = cur.copy(waveform = wave, energy = e, capturing = e > 0.00002f, method = method, linkage = Linkage.MIX,
            reason = if (e > 0.00002f) "" else "silencio: posible bloqueo de captura por la app")
    }

    private fun pushBins(mags: FloatArray, method: CaptureMethod) {
        for (i in smooth.indices) {
            val target = mags.getOrElse(i) { 0f }
            smooth[i] = if (target > smooth[i]) attack * target + (1 - attack) * smooth[i]
            else decay * smooth[i] + (1 - decay) * target
        }
        // Throttle ~30Hz: el suavizado sigue, la emisión se limita.
        val now = System.nanoTime()
        if (now - lastEmit < 33_000_000L && _frame.value.capturing) return
        lastEmit = now
        countRenderTick()
        val energy = smooth.average().toFloat()
        _frame.value = AudioFrame(bins = smooth.copyOf(), waveform = _frame.value.waveform, energy = energy,
            capturing = energy > 0.0005f, method = method, linkage = Linkage.MIX,
            reason = if (energy > 0.0005f) "" else "sin señal: verificar app permite captura")
    }
}

/** FFT radix-2 mínima para AudioRecord (sin dependencias). */
class Fft(private val size: Int) {
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)
    fun magnitudes(buf: ShortArray, n: Int): FloatArray {
        val len = size.coerceAtMost(n)
        for (i in 0 until size) {
            re[i] = if (i < len) buf[i] / 32768.0 else 0.0
            im[i] = 0.0
        }
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t; val u = im[i]; im[i] = im[j]; im[j] = u }
        }
        var len2 = 2
        while (len2 <= size) {
            val ang = -2.0 * Math.PI / len2
            val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < size) {
                var cwr = 1.0; var cwi = 0.0
                for (k in 0 until len2 / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len2 / 2] * cwr - im[i + k + len2 / 2] * cwi
                    val vi = re[i + k + len2 / 2] * cwi + im[i + k + len2 / 2] * cwr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len2 / 2] = ur - vr; im[i + k + len2 / 2] = ui - vi
                    val nwr = cwr * wr - cwi * wi; cwi = cwr * wi + cwi * wr; cwr = nwr
                }
                i += len2
            }
            len2 = len2 shl 1
        }
        val out = FloatArray(64)
        val per = (size / 2) / 64
        for (b in 0 until 64) {
            var acc = 0.0
            for (k in 0 until per) {
                val idx = 1 + b * per + k
                acc += sqrt(re[idx] * re[idx] + im[idx] * im[idx]) / (size / 4)
            }
            out[b] = (acc / per).toFloat().coerceIn(0f, 1f)
        }
        return out
    }
}
