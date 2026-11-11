package com.neonstereo.diagnostics

import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiagSnapshot(
    val appVersion: String = "1.0.0",
    val androidSdk: Int = Build.VERSION.SDK_INT,
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val notifAccess: Boolean = false,
    val recordGranted: Boolean = false,
    val captureMethod: String = "NONE",
    val captureReason: String = "",
    val sessionPkg: String = "",
    val sessionPlaying: Boolean = false,
    val signalEnergy: Float = 0f,
    val binsActive: Int = 0,
    val linkage: String = "UNKNOWN",
    val fps: Float = 0f,
    val events: List<String> = emptyList(),
)

class Diagnostics {
    private val _snap = MutableStateFlow(DiagSnapshot())
    val snap: StateFlow<DiagSnapshot> = _snap
    private val log = ArrayDeque<String>(100)

    fun update(fn: (DiagSnapshot) -> DiagSnapshot) { _snap.value = fn(_snap.value.copy(events = log.toList())) }
    fun event(msg: String) {
        if (log.size >= 100) log.removeFirst()
        // Nunca registrar títulos/artistas/contenido audio (§10).
        log.addLast(msg.take(220))
        _snap.value = _snap.value.copy(events = log.toList())
    }
    fun clear() { log.clear(); _snap.value = _snap.value.copy(events = emptyList()) }
}

/** Medidor FPS promedio + caídas (§9). */
class FpsMeter {
    private var last = 0L
    private var acc = 0
    private var accTime = 0L
    var fps: Float = 0f
        private set
    fun tick(now: Long = System.nanoTime()) {
        if (last != 0L) {
            acc++
            accTime += (now - last)
            if (accTime >= 1_000_000_000L) {
                fps = acc * 1_000_000_000f / accTime
                acc = 0; accTime = 0L
            }
        }
        last = now
    }
}
