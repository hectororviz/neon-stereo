# KNOWN_LIMITATIONS

1. **Captura no garantizada.** `Visualizer(0)` en Android 12+ suele devolver silencio
   o requerir privilegios según ROM. `AudioPlaybackCapture` depende de que
   Spotify / YouTube Music marquen su audio como capturable (`allowCapture`) y del
   consentimiento MediaProjection vigente. Probar en estéreo real; en emulador casi
   siempre FAIL por falta de sesión y de proyección.
2. **Sesión vs audio desacoplados.** Si hay mezcla capturada no se puede asegurar que
   pertenezca a la pista mostrada. El diagnóstico dice `vínculo=MIX/UNKNOWN` a propósito.
3. **MediaProjection expira.** Al ocultar/apagar se libera (§8.2); al volver hay que
   aceptar de nuevo. No se reutilizan tokens vencidos.
4. **`RECORD_AUDIO` ≠ acceso mezcla.** Solo habilita APIs; sin proyección/captura
   permitida no hay señal. Nunca se usa micrófono como sustituto.
5. **FPS de hardware.** 30 FPS es objetivo con degradación (partículas → FFT → barras
   simples → stop). Estéreos low-end pueden quedarse en barras simples o detenidos.
6. **Foco de sesión.** Con varias apps sonando se prioriza la que reproduce (determinista);
   cambios rápidos de foco dependen del sistema.
7. **Fuentes.** `lcddot_tr.ttf` / `Seven Segment.ttf` provistas por el usuario;
   verificar licencia para distribución.
8. **Sin red.** v1 no declara INTERNET: sin telemetría, sin carátulas remotas, sin updates.
9. **No probado aún en hardware.** Ver `TEST_REPORT.md`: E02/E04/E08 y matriz Spotify/YT
   quedan `BLOCKED/NOT_RUN` hasta prueba del usuario en estéreo Android 12+.
