# Neon Stereo — README

[![APK](https://github.com/hectororviz/neon-stereo/actions/workflows/apk.yml/badge.svg)](https://github.com/hectororviz/neon-stereo/releases)

> APK automático: cada cambio en `main` publica el debug en
> [Releases → `latest`](https://github.com/hectororviz/neon-stereo/releases/tag/latest)
> (prerelease). Los tags `v*` generan releases versionados con `TEST_REPORT.md`.

App Android nativa (Kotlin + Jetpack Compose) de visualización multimedia para estéreos en landscape.
Acompaña a Spotify / YouTube Music / cualquier app con MediaSession: muestra hora, portada, metadatos,
progreso, controles y visualizadores reactivos a **audio real capturable**. Sin simulación musical.

## Requisitos de entorno
- JDK 17, Android SDK Platform 34 + Build-Tools 34.0.0
- `sdk.dir` en `local.properties` (ya incluido en este repo si compilás acá)
- Sin emulador obligatorio; estéreo o tablet Android 12+ para prueba real

## Compilar e instalar
```bash
./gradlew :app:assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 7 tests unitarios
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Release: configurar keystore propio (no se incluye). Variables sugeridas
`NEON_KEYSTORE / NEON_KEY_ALIAS / NEON_KEYSTORE_PASSWORD / NEON_KEY_PASSWORD`,
o `~/.gradle/gradle.properties`. Sin keystore no se firma release.

## Primeros pasos en el estéreo
1. Abrir Neon Stereo (landscape). Se ve perfil "Dividido" + reloj aunque no haya música.
2. Long-press 2 s en zona libre → Configuración → Aplicación.
3. Conceder acceso a notificaciones (para ver qué suena), RECORD_AUDIO (para APIs de captura),
   y proyección de pantalla cuando se pida captura de reproducción.
4. Reproducir en Spotify/YT Music. Diagnóstico muestra método, señal y FPS.
5. Editar: botón "Editar", tocar elemento (capa superior), sliders (aplican al soltar), +/− fino,
   duplicar, capas, undo, reset. Cierra solo a los 60 s sin uso.

## Idioma, perfiles, permisos
- ES por defecto / según sistema, cambio ES/EN en Configuración.
- Perfiles: crear/duplicar/renombrar/exportar/importar JSON (SAF, 1 archivo = 1 perfil,
  importar siempre crea nuevo). Autosave atómico + `.bak`.
- Texto: cada elemento de texto (reloj, fecha, título, artista, tiempos) tiene
  `fontSize` propio (reloj 8–240sp, resto 8–160sp) en Básicas, con slider + −/+ fino.
- Sin permiso INTERNET. Sin telemetría. Logs locales sin títulos/artistas/audio,
  exportables y borrables desde Diagnóstico.

## Compatibilidad
`minSdk 29` (Android 10), `targetSdk 34`, objetivo Android 12+.
Captura depende de la app reproductora y la ROM: ver `KNOWN_LIMITATIONS.md`.
Detalles técnicos: `ARCHITECTURE.md`. Uso: `USER_GUIDE.md`. Pruebas: `TEST_REPORT.md`.
