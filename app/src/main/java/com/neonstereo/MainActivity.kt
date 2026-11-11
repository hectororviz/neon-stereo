package com.neonstereo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neonstereo.audio.AudioCaptureManager
import com.neonstereo.audio.CaptureMethod
import com.neonstereo.diagnostics.Diagnostics
import com.neonstereo.diagnostics.FpsMeter
import com.neonstereo.elements.ElementInstance
import com.neonstereo.elements.ElementTypes
import com.neonstereo.elements.boolOr
import com.neonstereo.elements.duplicate
import com.neonstereo.elements.floatOr
import com.neonstereo.elements.stringOr
import com.neonstereo.media.MediaMonitor
import com.neonstereo.profiles.AppProfile
import com.neonstereo.profiles.ProfileStore
import com.neonstereo.ui.AppSettingsPanel
import com.neonstereo.ui.BackgroundLayer
import com.neonstereo.ui.ClockWidget
import com.neonstereo.ui.ControlsWidget
import com.neonstereo.ui.CoverWidget
import com.neonstereo.ui.DateWidget
import com.neonstereo.ui.DotEq
import com.neonstereo.ui.EdgeBars
import com.neonstereo.ui.MarqueeText
import com.neonstereo.ui.ProgressWidget
import com.neonstereo.ui.RadialWidget
import com.neonstereo.ui.SpectrumBars
import com.neonstereo.ui.StarParticles
import com.neonstereo.ui.SwipeGestures
import com.neonstereo.ui.WaveWidget
import com.neonstereo.ui.EditorPanel
import com.neonstereo.ui.formatMs
import com.neonstereo.ui.hexColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NeonApp() } }
    }
}

@Composable
fun NeonApp() {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val scope = rememberCoroutineScope()
    val media = remember { MediaMonitor(ctx.applicationContext) }
    val audio = remember { AudioCaptureManager(ctx.applicationContext, scope) }
    val diag = remember { Diagnostics() }
    val fpsMeter = remember { FpsMeter() }

    var profile by remember { mutableStateOf<AppProfile?>(null) }
    var profiles by remember { mutableStateOf<List<AppProfile>>(emptyList()) }
    var activeId by remember { mutableStateOf("factory-dividido") }
    var preview by remember { mutableStateOf<AppProfile?>(null) }
    var editing by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    var customBg by remember { mutableStateOf<Uri?>(null) }
    val undo = remember { ArrayDeque<AppProfile>() }

    val mediaState by media.state.collectAsState()
    val frame by audio.frame.collectAsState()
    val diagSnap by diag.snap.collectAsState()

    fun pushUndo(p: AppProfile) {
        undo.addLast(p)
        if (undo.size > 50) undo.removeFirst()
    }
    suspend fun refresh() { profiles = ProfileStore.list(ctx); activeId = ProfileStore.activeId(ctx) }

    // Carga inicial: perfil + caché visual sin presentarla como activa (§4.2).
    LaunchedEffect(Unit) {
        refresh()
        val id = ProfileStore.activeId(ctx)
        profile = ProfileStore.load(ctx, id) ?: ProfileStore.factoryProfile()
        media.start()
        diag.event("inicio: perfil=$id sdk=${android.os.Build.VERSION.SDK_INT}")
    }

    // Ciclo de vida: al ocultar/apagar liberar captura y render (§8.2, §9).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) {
                audio.stopAll(reason = "app oculta/pantalla apagada")
                diag.event("release captura por onPause; reproducción externa intacta")
            } else if (e == Lifecycle.Event.ON_RESUME) {
                diag.event("resume: revalidar autorizaciones")
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); media.stop(); audio.stopAll() }
    }

    // Cierre settings a 60 s sin interacción (§5.1).
    LaunchedEffect(settingsOpen, lastTouch) {
        if (!settingsOpen) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            if (System.currentTimeMillis() - lastTouch > 60_000L) { settingsOpen = false; editing = false; break }
        }
    }

    // Decaimiento al pausar: reducir a cero (§8.2).
    LaunchedEffect(mediaState.playing) {
        if (!mediaState.playing && frame.capturing) audio.fadeOut()
    }

    // Ticker posición real: base + elapsed mientras reproduce (§13.4).
    var displayPos by remember { mutableStateOf(0L) }
    var anchorPos by remember { mutableStateOf(0L) }
    var anchorAt by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(mediaState.positionMs, mediaState.playing) {
        anchorPos = mediaState.positionMs
        anchorAt = android.os.SystemClock.elapsedRealtime()
        if (!mediaState.playing) displayPos = mediaState.positionMs
    }
    LaunchedEffect(mediaState.playing, mediaState.durationMs) {
        while (mediaState.playing) {
            delay(500L)
            val p = anchorPos + (android.os.SystemClock.elapsedRealtime() - anchorAt)
            val dur = mediaState.durationMs
            displayPos = if (dur > 0) p.coerceAtMost(dur) else p
        }
    }

    // Launchers oficiales.
    val projLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        audio.onProjectionResult(res.resultCode, res.data)
        diag.event("MediaProjection result=${res.resultCode}")
        lastTouch = System.currentTimeMillis()
    }
    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        diag.event("RECORD_AUDIO granted=$granted")
        diag.update { it.copy(recordGranted = granted) }
        if (granted) audio.tryVisualizerChain(mediaState.packageName)
        lastTouch = System.currentTimeMillis()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && preview == null) {
            scope.launch {
                val p = profile ?: return@launch
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(ProfileStore.exportJson(p).toByteArray()) }
                diag.event("perfil exportado")
            }
        }
        lastTouch = System.currentTimeMillis()
    }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    val exportOneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && exportTarget != null) {
            scope.launch {
                val p = ProfileStore.load(ctx, exportTarget!!)
                if (p != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(ProfileStore.exportJson(p).toByteArray()) }
                exportTarget = null
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        val raw = ins.bufferedReader().readText()
                        val p = ProfileStore.importJson(ctx, raw)
                        refresh(); profile = p; ProfileStore.setActive(ctx, p.id)
                        diag.event("importado como nuevo: ${p.name}")
                    }
                } catch (e: Exception) { diag.event("import falló: ${e.message}") }
            }
        }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            customBg = uri
            val p = profile
            if (p != null) {
                val els = p.elements.map { el ->
                    if (el.type == ElementTypes.BACKGROUND) el.copy(
                        specific = kotlinx.serialization.json.buildJsonObject {
                            el.specific.forEach { (k, e) -> put(k, e) }
                            put("mode", kotlinx.serialization.json.JsonPrimitive("custom"))
                            put("customUri", kotlinx.serialization.json.JsonPrimitive(uri.toString()))
                        }
                    ) else el
                }
                pushUndo(p); val np = p.copy(elements = els)
                profile = np; scope.launch { ProfileStore.save(ctx, np) }
            }
        }
    }

    fun notifEnabled(): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ctx.packageName)
    }

    val shown = preview ?: profile
    if (shown == null) { Text("Cargando…", color = Color.White); return }

    // FPS vía Choreographer.
    DisposableEffect(Unit) {
        val ch = android.view.Choreographer.getInstance()
        var alive = true
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                if (!alive) return
                fpsMeter.tick(System.nanoTime())
                ch.postFrameCallback(this)
            }
        }
        ch.postFrameCallback(cb)
        onDispose { alive = false; ch.removeFrameCallback(cb) }
    }
    val renderFps by audio.renderFps.collectAsState()
    LaunchedEffect(fpsMeter.fps, renderFps, frame.energy, mediaState.packageName) {
        diag.update {
            it.copy(
                fps = renderFps.takeIf { f -> f > 0f } ?: fpsMeter.fps,
                captureMethod = frame.method.name,
                captureReason = frame.reason,
                sessionPkg = mediaState.packageName,
                sessionPlaying = mediaState.playing,
                signalEnergy = frame.energy,
                binsActive = frame.bins.count { b -> b > 0.02f },
                linkage = frame.linkage.name,
                notifAccess = notifEnabled(),
            )
        }
    }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        // Superficie principal.
        Box(Modifier.weight(1f).fillMaxHeight()
            .pointerInput(editing) {
                // Long-press 2 s abre configuración (§5.1), sin interferir botones.
                detectTapGestures(
                    onPress = {
                        val t0 = System.currentTimeMillis()
                        lastTouch = t0
                        try {
                            awaitRelease()
                        } catch (_: Exception) {}
                    },
                    onLongPress = { lastTouch = System.currentTimeMillis(); settingsOpen = true },
                    onTap = { lastTouch = System.currentTimeMillis() },
                )
            }
        ) {
            SwipeGestures(onPrev = { media.prev() }, onNext = { media.next() }, editing = editing || settingsOpen) {
                CompositionView(
                    profile = shown,
                    mediaState = mediaState.copy(positionMs = displayPos),
                    frame = audio.frame.collectAsState().value,
                    customBg = customBg,
                    editing = editing || settingsOpen,
                    selectedId = selectedId,
                    onSelect = { selectedId = it; lastTouch = System.currentTimeMillis() },
                    onPlayPause = { media.toggle() },
                    onNext = { media.next() },
                    onPrev = { media.prev() },
                    onSeek = { media.seekTo(it) },
                    onOpenPlayer = {
                        try { media.openPlayerIntent()?.send() } catch (_: Exception) { diag.event("open player no permitido") }
                    },
                )
            }
            // Indicadores discretos (§8.2): sesión stale / sin captura.
            if (mediaState.stale || !frame.capturing) {
                androidx.compose.foundation.layout.Column(Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp)) {
                    if (mediaState.stale) Text("■ ${ctx.getString(com.neonstereo.R.string.stopped_hint)}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                    if (mediaState.active && !frame.capturing) Text("■ ${ctx.getString(com.neonstereo.R.string.no_capture_hint)}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                }
            }
        }

        if (settingsOpen) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxHeight().background(Color(0xFF0B0B10))) {
                androidx.compose.foundation.layout.Row {
                    val tabDesign = remember { mutableStateOf(true) }
                    androidx.compose.material3.Button(onClick = { tabDesign.value = true; lastTouch = System.currentTimeMillis() }) { Text(ctx.getString(com.neonstereo.R.string.design_tab)) }
                    androidx.compose.material3.Button(onClick = { tabDesign.value = false; lastTouch = System.currentTimeMillis() }) { Text(ctx.getString(com.neonstereo.R.string.app_tab)) }
                    androidx.compose.material3.Button(onClick = {
                        editing = !editing; lastTouch = System.currentTimeMillis()
                    }) { Text(if (editing) "Ver" else "Editar") }
                    androidx.compose.material3.Button(onClick = { settingsOpen = false; preview = null; lastTouch = System.currentTimeMillis() }) { Text("X") }
                }
                if (editing) {
                    EditorPanel(
                        elements = shown.elements,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        onUpdate = { updated ->
                            val p = (preview ?: profile) ?: return@EditorPanel
                            // Aplicar al soltar ya garantizado por Slider onValueChangeFinished.
                            val np = p.copy(elements = p.elements.map { if (it.id == updated.id) updated else it })
                            if (preview != null) preview = np else profile = np
                            pushUndo(p)
                            scope.launch { ProfileStore.save(ctx, np) }
                            lastTouch = System.currentTimeMillis()
                        },
                        onDuplicate = { id ->
                            val p = (preview ?: profile) ?: return@EditorPanel
                            val src = p.elements.firstOrNull { it.id == id } ?: return@EditorPanel
                            pushUndo(p)
                            val np = p.copy(elements = p.elements + src.duplicate())
                            if (preview != null) preview = np else profile = np
                            scope.launch { ProfileStore.save(ctx, np) }
                        },
                        onDelete = { id ->
                            val p = (preview ?: profile) ?: return@EditorPanel
                            pushUndo(p)
                            val np = p.copy(elements = p.elements.filterNot { it.id == id })
                            if (preview != null) preview = np else profile = np
                            selectedId = null
                            scope.launch { ProfileStore.save(ctx, np) }
                        },
                        onUndo = {
                            val prev = undo.removeLastOrNull()
                            if (prev != null) {
                                if (preview != null) preview = prev else profile = prev
                                scope.launch { ProfileStore.save(ctx, prev) }
                            }
                        },
                        onReset = { id ->
                            scope.launch {
                                val factory = ProfileStore.factoryProfile()
                                val base = factory.elements.firstOrNull { it.type == (shown.elements.firstOrNull { e -> e.id == id }?.type) }
                                if (base != null) {
                                    val p = (preview ?: profile) ?: return@launch
                                    pushUndo(p)
                                    val np = p.copy(elements = p.elements.map { if (it.id == id) it.copy(x = base.x, y = base.y, width = base.width, height = base.height, scale = 1f, rotationDegrees = 0f, opacity = 1f, colorHex = base.colorHex, glow = base.glow, specific = base.specific) else it })
                                    if (preview != null) preview = np else profile = np
                                    ProfileStore.save(ctx, np)
                                }
                            }
                        },
                        onMoveLayer = { id, delta ->
                            val p = (preview ?: profile) ?: return@EditorPanel
                            pushUndo(p)
                            val np = p.copy(elements = p.elements.map { if (it.id == id) it.copy(zIndex = it.zIndex + delta) else it })
                            if (preview != null) preview = np else profile = np
                            scope.launch { ProfileStore.save(ctx, np) }
                        },
                    )
                } else {
                    AppSettingsPanel(
                        profiles = profiles,
                        activeId = activeId,
                        previewId = preview?.id,
                        onSelectPreview = { preview = it; lastTouch = System.currentTimeMillis() },
                        onConfirmProfile = { id ->
                            scope.launch {
                                val p = ProfileStore.load(ctx, id)
                                if (p != null) { profile = p; ProfileStore.setActive(ctx, id); preview = null; refresh() }
                            }
                            lastTouch = System.currentTimeMillis()
                        },
                        onDuplicate = { id -> scope.launch {
                            val p = ProfileStore.load(ctx, id) ?: return@launch
                            val np = p.copy(id = java.util.UUID.randomUUID().toString(), name = p.name + " copia")
                            ProfileStore.save(ctx, np); refresh()
                        } },
                        onDelete = { id -> scope.launch {
                            if (id.startsWith("factory-")) { diag.event("no borrar plantilla de fábrica"); return@launch }
                            val f = java.io.File(ctx.filesDir, "profiles/$id.json")
                            // Respaldo antes de borrar.
                            if (f.exists()) f.copyTo(java.io.File(ctx.filesDir, "profiles/$id.json.deleted-bak"), true)
                            f.delete(); refresh()
                        } },
                        onResetFactory = { scope.launch {
                            val f = ProfileStore.factoryProfile()
                            // Respaldo automático antes de restablecer (§6).
                            profile?.let { ProfileStore.save(ctx, it.copy(id = it.id + "-pre-reset")) }
                            ProfileStore.save(ctx, f); ProfileStore.setActive(ctx, f.id)
                            profile = f; preview = null; refresh()
                        } },
                        onNew = { tid -> scope.launch {
                            val np = ProfileStore.newFromTemplate(ctx, tid)
                            profile = np; ProfileStore.setActive(ctx, np.id)
                            preview = null; refresh()
                            lastTouch = System.currentTimeMillis()
                        } },
                        onExport = { exportTarget = it; exportOneLauncher.launch("$it.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json")) },
                        onPickBackground = { bgLauncher.launch(arrayOf("image/*")) },
                        diag = diagSnap,
                        notifAccess = notifEnabled(),
                        recordGranted = audio.hasRecordPermission(),
                        captureInfo = "${frame.method} ${frame.reason}".trim(),
                        onRequestNotif = {
                            try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
                        },
                        onRequestRecord = { recordLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        onRequestProjection = {
                            val intent = audio.projectionIntent()
                            if (intent != null) projLauncher.launch(intent) else diag.event("MediaProjection no disponible")
                        },
                        onStartVisualizer = {
                            val ok = audio.tryVisualizerChain(mediaState.packageName)
                            diag.event(if (ok) "Visualizer iniciado" else "Visualizer falló: ${audio.frame.value.reason}")
                        },
                        onClearLogs = { diag.clear() },
                        onExportLogs = { exportLauncher.launch("neon-logs.json") },
                        onLanguage = { lang ->
                            scope.launch {
                                ctx.getSharedPreferences("neon", android.content.Context.MODE_PRIVATE).edit().putString("lang", lang).apply()
                                activity?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun CompositionView(
    profile: AppProfile,
    mediaState: com.neonstereo.media.MediaState,
    frame: com.neonstereo.audio.AudioFrame,
    customBg: Uri?,
    editing: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sorted = profile.elements.filter { it.visible }.sortedBy { it.zIndex }
        // Fondo primero (si hay).
        sorted.firstOrNull { it.type == ElementTypes.BACKGROUND }?.let { bg ->
            BackgroundLayer(bg, mediaState.cover, customBg ?: bg.specific.stringOr("customUri", "").takeIf { it.isNotBlank() }?.let { Uri.parse(it) }, 1f,
                pulse = if (frame.capturing) frame.energy else 0f)
        }
        for (el in sorted.filter { it.type != ElementTypes.BACKGROUND }) {
            val w = maxWidth * el.width * el.scale
            val h = maxHeight * el.height * el.scale
            val x = maxWidth * el.x
            val y = maxHeight * el.y
            // Garantizar al menos parte visible (§5.1): clamp suave.
            Box(
                Modifier
                    .size(w, h)
                    .offset(x, y)
                    .rotate(el.rotationDegrees)
                    .alpha(el.opacity)
                    .background(if (editing && el.id == selectedId) Color.White.copy(0.08f) else Color.Transparent)
                    .pointerInput(editing, el.id) {
                        if (editing) detectTapGestures { onSelect(el.id) }
                    }
            ) {
                ElementContent(el, mediaState, frame, editing, onPlayPause, onNext, onPrev, onSeek, onOpenPlayer)
            }
        }
    }
}

@Composable
fun ElementContent(
    el: ElementInstance,
    media: com.neonstereo.media.MediaState,
    frame: com.neonstereo.audio.AudioFrame,
    editing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    when (el.type) {
        ElementTypes.CLOCK -> ClockWidget(el, 1f)
        ElementTypes.DATE -> DateWidget(el, 1f)
        ElementTypes.TITLE -> if (media.title.isNotBlank()) MarqueeText(media.title, hexColor(el.colorHex), el.specific.floatOr("fontSize", 22f)) else if (!editing) {}
        ElementTypes.ARTIST -> if (media.artist.isNotBlank()) Text(media.artist, color = Color.White.copy(el.opacity), fontSize = el.specific.floatOr("fontSize", 16f).coerceIn(8f, 160f).sp) else {}
        ElementTypes.COVER -> CoverWidget(el, media, 1f, onOpenPlayer)
        ElementTypes.CONTROLS -> ControlsWidget(media, onPlayPause, onNext, onPrev, hexColor(el.colorHex))
        ElementTypes.PROGRESS -> ProgressWidget(el, media, 1f, onSeek, editing)
        ElementTypes.TIME_CURRENT -> Text(formatMs(media.positionMs), color = Color.White, fontSize = el.specific.floatOr("fontSize", 14f).coerceIn(8f, 160f).sp)
        ElementTypes.TIME_TOTAL -> Text(formatMs(media.durationMs), color = Color.White.copy(0.7f), fontSize = el.specific.floatOr("fontSize", 14f).coerceIn(8f, 160f).sp)
        ElementTypes.SPECTRUM_BARS -> SpectrumBars(el, frame, el.opacity)
        ElementTypes.EDGE_BARS -> EdgeBars(el, frame, el.opacity)
        ElementTypes.WAVE -> WaveWidget(el, frame, el.opacity)
        ElementTypes.DOT_EQ -> DotEq(el, frame, el.opacity)
        ElementTypes.RADIAL -> RadialWidget(el, frame, el.opacity)
        ElementTypes.STAR_PARTICLES -> StarParticles(el, frame, el.opacity)
        else -> {}
    }
}
