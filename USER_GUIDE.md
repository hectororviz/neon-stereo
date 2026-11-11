# USER_GUIDE (Español)

## 1. Qué ves al abrir
Reloj LED grande, fecha, portada, título/artista, controles neón, barra de progreso,
barras en bordes + estrellas. Todo funciona sin música salvo visualizadores (detenidos con aviso).

## 2. Abrir configuración
Mantener 2 segundos sobre zona libre. Panel lateral con pestañas Diseño / Aplicación
+ botón Editar/Ver + X. Se cierra solo tras 60 s sin tocar.

## 3. Permisos (Aplicación → asistente)
1. "Notif": abre Ajustes del sistema → permitir Neon Stereo. Volver.
2. "Audio": conceder RECORD_AUDIO (no graba ambiente; habilita APIs de captura).
3. "Proyección": lanzar intent del sistema y Aceptar. Vale por sesión; al ocultar la app
   hay que re-autorizar (restricción Android).
4. "Probar Visualizer": alternativa sin proyección (sesión 0). En muchos estéreos da silencio.

Si dice "sin señal" con música sonando: tu reproductor/ROM bloquea captura → reloj,
portada y controles siguen andando; es limitación documentada, no bug.

## 4. Editar diseño
Botón "Editar" → tocá un elemento en la vista previa (se selecciona la capa superior).
Sliders aplican **al soltar** (+ valor provisional mientras arrastrás), botones −/+ para fino.
**Tamaño de texto por elemento:** reloj, fecha, título, artista y tiempos tienen `fontSize`
propio (reloj hasta 240sp, resto hasta 160sp) en Básicas — útil en estéreos grandes.
Duplicar (+Dup) crea copia desplazada; Del borra (con Undo en la sesión);
Bajar/Subir cambia zIndex; Reset devuelve ese elemento a fábrica.
Todo se autoguardada al confirmar. Superposición permitida con aviso visual.

## 5. Perfiles
Lista en Diseño: Ver = previsualizar sin aplicar, OK = confirmar. Duplicar/Borrar/Exportar
por perfil. **Nuevo desde plantilla:** Dividido / Minimal / Nocturno / Bordes
(siempre crean perfil con ID nuevo). Importar JSON abre SAF (siempre crea perfil nuevo).
Fábrica restaura "Dividido" con respaldo previo. Si un JSON viene dañado, se recupera
el `.bak` y avisa.

## 6. Fondo
Elemento Fondo: modo `black` (negro, decisión), `blur_cover` (portada + scrim),
`custom` (botón "Fondo custom" → elegir imagen local, permiso persistido).

## 7. Gestos
Swipe horizontal fuera de botones/barra = anterior/siguiente. Desactivado editando.
Tocar portada abre la app reproductora solo si el sistema lo permite. Seek solo si
la sesión publica `ACTION_SEEK_TO`; si no, la barra es solo lectura.

## 8. Problemas comunes
- No aparece qué suena → falta acceso notificaciones o reproductor sin MediaSession.
- Botones grises → la sesión no publica esa acción; no se finge éxito.
- Visualizadores quietos con música → captura bloqueada; ver Diagnóstico → motivo + sugerencia.
- Todo negro tras importar → JSON inválido; se restauró backup; reimportar archivo válido.
- Batería/fluidez → la calidad baja sola; objetivo 30 FPS, no promesa.
