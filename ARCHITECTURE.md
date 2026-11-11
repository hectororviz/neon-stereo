# ARCHITECTURE

## Módulos internos (paquete único `com.neonstereo`, 8 subsistemas §11)
- `app/ui` (`MainActivity`, `Widgets`, `Editor`, `Settings`): Compose landscape,
  modo presentación / edición, gestos, side-panel 380dp, auto-cierre 60s, long-press 2s.
- `elements` (`Elements`, `Registry`): `ElementInstance` serializable
  (`id/type/displayName/visible/x/y/width/height/scale/rotation/opacity/zIndex/color/glow/aspect/schema + specific:JsonObject`),
  coordenadas en proporciones 0..1 del área, `duplicate()` con ID nuevo + offset,
  `ElementRegistry` con props comunes + específicas para editor genérico por metadatos.
- `media` (`Media`): `MediaSessionManager` + `ComponentName(MediaNotificationListener)`,
  `MediaController.Callback`, selección prioriza PLAYING, foco estable, conserva última
  conocida `stale=true`, comandos solo si `PlaybackState.actions` lo permite,
  `sessionActivity` solo si existe PendingIntent.
- `audio` (`Audio`): `AudioCaptureManager` + `Fft` radix-2 propia.
  - `Visualizer(0)` con `SCALING_MODE_NORMALIZED`, FFT 64 bins + waveform, puede dar silencio.
  - `AudioPlaybackCaptureConfiguration + MediaProjection + AudioRecord 44.1kHz mono`,
    consentimiento por sesión, `RECORD_AUDIO` + `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
  - `attack/decay` parametrizable, `bandFiltered/bandEnergy` (low 0-10 / mid 10-32 / high 32-64 / all).
  - Invariante: `playing != señal`. Sin bins válidos → `capturing=false`, render detenido.
  - `stopAll()` en ON_PAUSE, `fadeOut()` al pausar. Buffers solo en memoria, nada se guarda.
- `visuals` (`Draw` + composables en `ui/Widgets`): barras espectro, barras bordes sup/inf,
  onda, EQ puntos, radial, estrellas por banda, glow/pulso fondo. Canvas Compose
  (sin OpenGL obligatorio). Si `!capturing || bins.isEmpty()` retornan sin dibujar.
- `profiles` (`Profiles`): `AppProfile(schema=1, id, name, refWxH, palette, backgroundHex, elements, extras)`.
  DataStore `active_profile`, archivos `filesDir/profiles/<id>.json` con tmp+rename atómico,
  `.bak` previo, validación ≤2MB, import siempre ID nuevo + resolución de nombre,
  `extras` preservados por `ignoreUnknownKeys` + reexportación.
- `diagnostics` (`Diagnostics`): `DiagSnapshot` + `FpsMeter` (Choreographer, promedio/seg).
  Eventos máx 100, sin PII (nunca títulos/artistas/audio). Panel muestra método, motivo,
  sesión vs fuente, vínculo MIX/UNKNOWN, FPS.
- `localization`: `res/values/strings.xml` (ES) + `values-en`, cambio con recreate.

## Estado reactivo
`StateFlow`: `MediaMonitor.state`, `AudioCaptureManager.frame`, `Diagnostics.snap`,
perfil en `mutableStateOf`. Hilos: captura y FFT en `Dispatchers.Default`,
UI en Main. Reloj 1s/5s, fecha 30s, posición 1s — nunca a 30Hz salvo renderer musical.

## Fondo negro + blur equivalencia (§3, decisión 8)
Fábrica `blur_cover`: portada `Crop + alpha 0.55` + scrim negro `alpha=dim(0.80) − pulse*0.25`.
`blur 75%` = normalizado (reservado para futuro sigma real ≈ 25px@1080p sin costo extra);
`dim 80%` = scrim 0.80. Modo `black` = sólido `#000`. Modo `custom` = imagen SAF persistida.
Sin portada → sólido tema, jamás portada vieja.

## Fuentes
`assets/fonts/lcddot_tr.ttf` (LCDDot), `Seven Segment.ttf` provistas por el usuario,
cargadas vía `Typeface.createFromAsset` → `FontFamily(Typeface)`. Requiere verificar
licencia antes de publicar en Play.

## Seguridad
SAF `OpenDocument/CreateDocument`, sin `READ_EXTERNAL` total, sin `INTERNET`,
sin rutas/URIs ejecutables desde JSON, `require(length<=MAX)` y try/catch por archivo.
