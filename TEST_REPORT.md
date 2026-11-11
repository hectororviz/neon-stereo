# TEST_REPORT

Fecha: 2026-11-11 · Entorno: JDK 17.0.21, SDK Platform 34 / Build-Tools 34.0.0, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24.
Sin dispositivos `adb` conectados al compilar.

## Unitarias (`./gradlew :app:testDebugUnitTest`) — PASS 8/8
`com.neonstereo.ProfilesTest` (evidencia: `app/build/test-results/testDebugUnitTest/TEST-*.xml`):
- duplicate_assignsNewIdAndOffset — PASS
- commonProps_uniqueIdsStableTypes — PASS
- bands_filterHonest — PASS
- bandEnergy_partialSignal — PASS
- profileJson_sizeGuard — PASS
- fft_magnitudesInRange — PASS
- fontSize_perElement_wideLimits — PASS (reloj hasta 240sp, resto hasta 160sp)
- playState_isNotSignal — PASS

## Build
- `:app:assembleDebug` — PASS. APK: `app/build/outputs/apk/debug/app-debug.apk` (~55MB, debug signed).
- `:app:assembleRelease` — PASS parcial. APK no firmado:
  `app/build/outputs/apk/release/app-release-unsigned.apk` (2.3MB, con R8).
  No instalable hasta que el usuario provea keystore (no se inventa).

## Instrumentadas
- `CompositionInstrumentedTest` — NOT_RUN (sin emulador/dispositivo en este entorno).

## Cambios v1.0.1 (2026-11-11, sobre reporte de campo Xiaomi SDK36)
- Progreso/tiempos: ticker real `base + elapsed` a 500ms (antes solo callbacks).
- `fontSize` por elemento: reloj 8–240sp, título/artista/fecha/tiempos 8–160sp.
- Visualizer: cadena con captureSize 512→256→mínimo + mapeo error -3 a "bloqueado por ROM/EQ".
- PlaybackCapture: prueba 44100→48000, verifica `STATE_INITIALIZED`/`RECORDSTATE_RECORDING`, motivo detallado.
- Emisión de frames limitada a ~30Hz + métrica `renderFps` en Diagnóstico (antes vsync 120).
- Plantillas: Minimal / Nocturno / Bordes + botón Nuevo (ID nuevo, §6). Fábricas no borrables.

## Matriz manual E01–E08 (para ejecutar en estéreo Android 12 + Spotify por el usuario)
| ID | Escenario | Esperado | Estado |
|---|---|---|---|
| E01 | Inicio sin música | Perfil + reloj, efectos detenidos, sin crash | NOT_RUN — probar con APK |
| E02 | Reproducción Spotify/YT Music | Metadatos + controles + visualizadores solo con captura válida | BLOCKED — sin dispositivo |
| E03 | Cambio de canción | Transicionan portada/fondo/textos; reloj/controles fijos | NOT_RUN |
| E04 | Captura bloqueada | Diagnóstico explica motivo; resto operativo | BLOCKED — sin dispositivo |
| E05 | Edición | Selección superior, duplicar/rotar/capas/undo/autosave | NOT_RUN (lógica cubierta parcial por tests) |
| E06 | Cambio de resolución | Reescalado proporcional, elementos accesibles | NOT_RUN |
| E07 | Perfil dañado | Backup recupera último válido + aviso | NOT_RUN (código: try/catch + .bak) |
| E08 | Pantalla apagada | Libera captura/render; música externa intacta | BLOCKED — sin dispositivo |

Checklist para el usuario (instalar APK debug, reproducir Spotify y YT Music por separado,
denegar/revocar permisos, cancelar proyección, importar `docs/example-profile.json` válido
e inválido, rotar/duplicar/apilar elementos, medir FPS en Diagnóstico y anotar PASS/FAIL).
Ninguna prueba no ejecutada se marca como pasada.
