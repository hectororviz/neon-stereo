# NEON STEREO — Especificación maestra de producto y desarrollo

**Versión del documento:** 1.0 (consolidación de los módulos 1–28 y revisión del 29)  
**Idioma principal:** español (Argentina)  
**Destinatario:** OpenCode / agente de desarrollo Android  
**Estado:** instrucciones de implementación; los puntos identificados como *decisión propuesta* no fueron aprobados explícitamente en el módulo 29.  
**Producto:** aplicación independiente de visualización y control multimedia para estéreos Android en orientación horizontal.

> **Instrucción principal para OpenCode:** implementar un proyecto Android nativo completo, compilable, comprobable y documentado que cumpla este documento. No sustituir funcionalidades reales por demostraciones visuales, pantallas falsas, controles decorativos, análisis de audio ficticio o promesas de implementación posterior. Si una API, el dispositivo o una aplicación externa impiden una función, implementar el mecanismo oficial posible, detectar su disponibilidad, degradar *solo* esa capacidad, registrar el motivo y demostrarlo en el panel de diagnóstico. No afirmar que una prueba se ejecutó si no se ejecutó. Prioridad absoluta: funcionalidad, estabilidad y comportamiento honesto.

---

## 1. Visión y límites del producto

Neon Stereo es una **aplicación de visualización** que acompaña a la música reproducida por otras aplicaciones del dispositivo. Su pantalla muestra hora y fecha, portada, metadatos, progreso y controles de reproducción, además de varios visualizadores y efectos gráficos reactivos al **audio real capturable**.

- **Sí:** app Android autónoma, interfaz personalizable, multimedia mediante sesiones Android compatibles, análisis real del audio autorizado, perfiles, editor y diagnóstico.
- **No:** launcher, reemplazo del sistema del estéreo, reproductor musical propio, servicio de audio permanente en segundo plano, simulación de barras o partículas musicales cuando no hay audio capturable.
- **Orientación:** exclusivamente horizontal (landscape). Debe adaptarse a tamaños, densidades y relaciones de aspecto de estéreos y tablets Android.
- **Compatibilidad:** `minSdk` Android 10/API 29; dispositivo objetivo genérico con Android 12 o superior. Elegir `targetSdk` moderno y compatible con el entorno de compilación, teniendo en cuenta los cambios en permisos, MediaProjection y servicios de primer plano de las versiones recientes; documentar la versión elegida.
- **Tecnología:** Kotlin, Jetpack Compose; arquitectura modular interna; renderizado híbrido (Compose más un motor de dibujo especializado **solo si las mediciones lo justifican**).
- **Inicio:** manual, sin arranque automático ni obligación de servicios persistentes.
- **Idioma:** español e inglés; español predeterminado o según idioma del sistema, con cambio desde configuración.
- **Red:** funcionamiento local y sin permiso `INTERNET` en la versión inicial. Diseño extensible para futuras funciones online sin implementarlas ahora.
- **Una implementación completa:** entregar de una vez la solución funcional, no dividir artificialmente el trabajo en fases parciales o una maqueta seguida por una promesa.

## 2. Prioridades, invariantes y criterios de diseño

1. Funcionalidad y estabilidad por encima de la espectacularidad gráfica.
2. El reloj y el editor deben funcionar aunque no exista ningún reproductor o permiso de captura.
3. **Jamás inventar niveles, frecuencias, beats, ondas ni movimientos reactivos a música**: todo visualizador y efecto categorizado como musical depende de datos reales de audio. Una transición de interfaz, el cursor de progreso o el parpadeo configurable del reloj no son simulación musical.
4. No atribuir el audio mezclado capturado a una pista o aplicación particular cuando no se pueda verificar la relación.
5. Nunca modificar ni detener la reproducción musical por cerrar, ocultar o suspender Neon Stereo; solo liberar los recursos propios.
6. Cada elemento visual es una **instancia editable**, duplicable y serializable; las fuentes de datos (reloj, sesión de medios, captura) son centrales, no se duplican.
7. Los errores de un subsistema no deben bloquear el resto de la app. Priorizar fallos aislados y recuperables.
8. Objetivo de **30 FPS**, no garantía universal. Calidad automática y degradación progresiva si el hardware no alcanza ese objetivo.
9. Todos los controles visibles deben realizar su acción o indicar claramente que no están disponibles. No controles de mentira.
10. Persistencia transaccional/atómica y recuperación: evitar pérdida de personalizaciones aun con cierres inesperados.

## 3. Apariencia inicial y diseño de referencia

La inspiración es la imagen de referencia compartida por el usuario al inicio de la conversación: estética oscura, neón, reloj grande, portada y controles, barras en bordes y efectos estelares. **Si OpenCode recibe la imagen original, usarla como referencia visual, no como un fondo rasterizado que sustituya componentes interactivos.** Si no está disponible, ejecutar la especificación textual siguiente.

**Diseño de fábrica «Dividido»:** información/portada de un lado y reloj del otro, con tamaños y posiciones proporcionales. Composición ordenada y legible para consulta a distancia desde el vehículo.

- Reloj predeterminado: LED de puntos (dot matrix), grande.
- Portada: esquinas redondeadas.
- Fondo: la portada ampliada y difuminada, con **desenfoque inicial 75 %** y **oscurecimiento inicial 80 %**. Esos porcentajes representan valores de controles normalizados; elegir una equivalencia reproducible a unidades físicas (p. ej., sigma/radio de blur y capa de oscurecimiento), documentarla y mantenerla estable entre versiones.
- Paleta «Original» como tema de fábrica; colores globales personalizables y posibilidad de colores automáticos derivados de la portada.
- Barras iniciales: borde superior e inferior, simultáneas.
- Partículas iniciales: estrellas; también pulso del fondo y glow.
- Controles multimedia neón agrupados.
- Estilo inicial de barra de progreso: neón.
- Balance inicial de animación/sensibilidad: moderado; sin sobrecarga visual.
- Transiciones musicales: desplazamiento/deslizamiento **de portada, fondo y datos musicales**; reloj y controles inmóviles.
- Si no existe portada: fondo sólido del tema, **sin reutilizar una portada anterior como sustituto**.
- Modo día/noche: sin automatismo horario; regulación manual del brillo visual de la composición. No controlar brillo del sistema sin necesidad.
- No ocultar forzosamente las barras de sistema mediante modo inmersivo. Respetar insets/áreas seguras.

## 4. Catálogo de componentes y estado visual

Todos los elementos deben admitir múltiples instancias, nombres automáticos editables y propiedades generales cuando correspondan. La duplicación copia los valores del original y produce una instancia nueva ligeramente desplazada, con ID diferente y fuente de datos central compartida.

### 4.1 Reloj y fecha

- Hora obtenida del reloj del sistema; formatos **12/24 horas** y **segundos opcionales**.
- Estilos de fuente: **LED de puntos**, **siete segmentos**, fuentes normales finas, negritas y monoespaciadas; ofrecer varias familias reales y disponibles legalmente, usando alternativas del sistema si faltan recursos.
- LED de puntos: diámetro, forma, separación, brillo y espaciado entre caracteres.
- Siete segmentos: grosor, separación, apariencia de segmentos desactivados/tenues y brillo.
- Separador `:` fijo, parpadeante u oculto.
- Fecha configurable como elemento separado cuando tenga sentido; formato localizado; no depender de reproducción.
- Actualización a cadencia necesaria: por minuto o segundo, no cada fotograma.

### 4.2 Datos musicales

- Título y artista como elementos individuales, configurables en posición y tipografía.
- **No** mostrar por defecto nombre de álbum ni nombre de la app reproductora como campos de la composición (pueden aparecer en diagnóstico técnico, según privacidad).
- Los campos de metadatos faltantes se **ocultan individualmente**, sin placeholders obligatorios.
- Títulos extensos: comportamiento **global** configurable para campos de texto (desplazamiento tipo marquee, recorte o ajuste automático); por defecto desplazamiento suave del título musical.
- En ausencia temporal de sesión, conservar la **última canción conocida** y mostrar un pequeño indicador de estado «detenida / sesión no disponible», intentando reconectar automáticamente cuando sea posible.
- Al iniciar nuevamente, mostrar rápidamente último perfil y datos visuales en caché, sin presentar esta caché como sesión activa verificada.

### 4.3 Portada

- Esquinas configurables hasta contorno circular; borde (grosor y color), sombra, glow y opacidad.
- Modos de ajuste: recorte (`crop`) o mostrar completa (`fit`).
- Tocar la portada abre la aplicación reproductora **solo si Android y la sesión permiten una acción segura**; si no, sin crash ni intentos indiscriminados.
- **No** habilitar por defecto rotación animada continua de la portada (opción no seleccionada en el módulo 20).
- Cambio de canción: transición de deslizamiento junto con fondo y textos musicales; preservar diseño estable.

### 4.4 Controles multimedia

- **Grupo** reproducir/pausar, anterior y siguiente, con estilo neón.
- Personalizar separación, tamaño y **orden interno** de botones; grupo como unidad seleccionable, escalable, rotativa, movible y duplicable.
- Todos los grupos duplicados actúan sobre la **misma sesión multimedia seleccionada**; no crean sesiones distintas.
- Área táctil mínima objetivo **56 dp** por control, aun si el icono visual es menor.
- Actualización correcta de estado (play/pause) según MediaSession; habilitar/deshabilitar botones según acciones realmente disponibles.
- Desplazamientos horizontales de dedo para anterior/siguiente **solo fuera de botones y barra de progreso**; bloquear esos gestos mientras el editor esté abierto.
- Retroalimentación táctil cuando el dispositivo la soporte, sin exigir vibrador.

### 4.5 Progreso y tiempos

- Barra neón, longitud, posición, grosor, color, glow, opacidad y aspecto configurables.
- Tiempo transcurrido y duración total como instancias editables independientes.
- Seek tocando/arrastrando la barra **solo si la sesión permite buscar posición**; bloquear con estado claro si no lo permite.
- El progreso debe actualizarse a frecuencia razonable y basarse en estado/posición de reproducción, no simular avance cuando está pausado.

### 4.6 Cinco familias de visualizadores

Se pueden activar varios **simultáneamente, sin límite arbitrario de cantidad de instancias**; mostrar advertencias de coste y aplicar reducción de calidad si el equipo no soporta la carga. Cada visualizador tiene configuración independiente de sensibilidad, bandas y suavizado; utilizan la misma captura real compartida.

1. **Barras de espectro:** cantidad, ancho, separación, orientación/dirección, altura, respuesta y gradiente.
2. **Barras de bordes:** superior/inferior, simetría, márgenes, tamaño y cantidad; ambas zonas activas inicialmente.
3. **Onda continua:** amplitud, grosor, número de puntos, interpolación/suavidad y orientación.
4. **Ecualizador de puntos:** filas, columnas, diámetro, espaciado, llenado y respuesta.
5. **Espectro circular/radial:** radio, arco/cobertura, cantidad de barras, orientación, ancho y reacción.

Los visualizadores deben utilizar información efectivamente derivada del audio disponible (FFT/espectro, forma de onda o niveles calculados). No presentar un espectro de frecuencias inventado a partir de un simple estado «reproduciendo». Si un proveedor solo suministra amplitud y no suficientes bandas, reflejar la capacidad disponible con honestidad.

### 4.7 Efectos visuales musicales

- **Partículas de estrellas:** cantidad, tamaño, velocidad/reacción, opacidad, color, distribución espacial y **banda de frecuencia seleccionable**: graves, medios, agudos o energía global.
- **Pulsación del fondo:** intensidad, rapidez de ataque y relajación, banda de frecuencia, límites de brillo.
- **Glow musical:** intensidad, color, sensibilidad y banda/fuente cuando proceda.
- Sin audio capturable: efectos musicales detenidos, sin movimiento residual continuo ni pulsos simulados.
- Pausa: caída gradual de barras/ondas y efectos hasta cero, luego detener renderizado continuo.
- No añadir otras categorías de efectos geométricos como requisito obligatorio.

## 5. Propiedades comunes y editor de diseño

### 5.1 Esquema base por instancia

Cada elemento debe guardar al menos: `id` estable único, `type` estable, `displayName` autogenerado editable, `visible`, `x`, `y`, `width`, `height`, `scale`, `rotationDegrees` (rotación libre para **todos** los tipos), `opacity`, `zIndex`, `color`/estilo cuando aplique, `glow` cuando aplique, `aspectRatioLocked`, propiedades específicas tipadas y versión de esquema. Identificar claramente coordenadas de referencia/ánclajes y transformaciones para garantizar adaptación entre resoluciones.

- X/Y y dimensiones expresadas preferentemente en proporciones de un área de referencia; conservar la intención al adaptar pantalla.
- Ancho y alto editables individualmente; **bloqueo de proporciones por instancia**, activable/desactivable.
- Colores mediante paletas y selector libre RGB/HEX; no exigir degradados universales para todos los componentes (sí para visualizadores donde corresponda).
- Texto: espaciado de letras/caracteres, interlineado, alineación y grosor disponible de fuente.
- Orden de capas ajustable mediante **subir/bajar**; permitir superposición intencional con advertencia, **sin zonas de exclusión**.
- Al mover/rotar/escalar, impedir solo que una instancia quede **totalmente fuera de la superficie visible**. Calcular límites con geometría realmente transformada; permitir elementos parcialmente cortados.
- Selección de un elemento por **toque en la previsualización**, sin arrastrar elementos para posicionarlos. Si se solapan, seleccionar **solo el de la capa superior** bajo el toque.
- Edición de X/Y, dimensiones, rotación y demás mediante **deslizadores y botones +/− de ajuste fino**.
- **Aplicar un cambio de slider al soltarlo**, no durante el movimiento; ofrecer indicación provisional del valor mientras se mueve si es útil, sin sobrescribir el valor confirmado.
- Vista previa dentro de la pantalla principal y panel lateral con cambios aplicados visibles; no reestructurar todo el layout.
- Configuración dividida en **Diseño** (elementos, perfiles, temas y efectos) y **Aplicación** (permisos, captura, idioma, rendimiento y diagnóstico).
- Propiedades de un componente presentadas en secciones **Básicas** y **Avanzadas**.
- Duplicar conserva las propiedades del original y desplaza ligeramente la instancia para hacerla distinguible; todos los datos multimedia y la señal de audio permanecen centrales.
- Eliminar permite **Deshacer**. Además, historial de Deshacer para cambios de propiedades y duplicaciones durante la **sesión actual de edición**; no es necesario guardar ese historial tras cerrar la app.
- Restablecer una sola instancia devuelve exclusivamente sus valores de fábrica, sin afectar otras instancias.
- No permitir controles interactivos de reproducción ni gestos de cambio de tema cuando se está editando si pueden provocar acciones accidentales.
- Abrir configuración mediante **pulsación larga de 2 segundos** sobre la superficie libre de la pantalla (definir detección sin interferir con botones). Cerrar configuración luego de **60 segundos de inactividad**; cualquier uso real reinicia el contador.
- El estado se guarda automáticamente cuando cada operación queda confirmada. No depender de un botón «Guardar».
- Desplazamiento opcional y muy leve de elementos estáticos para mitigar retención de imagen, con interruptor y límites que preserven accesibilidad y toque.

### 5.2 Modos de uso

**Modo presentación:** reloj y multimedia, efectos y gestos permitidos según contexto.  
**Modo edición:** tocar selecciona capa superior; panel lateral; sliders, +/−, duplicar, eliminar, restablecer; reproducción no se modifica accidentalmente; avisos de incompatibilidad y superposición.

La configuración debe conservar áreas táctiles de 56 dp y ser manejable en pantalla de estéreo, no solamente en un teléfono de desarrollo.

## 6. Perfiles, temas y persistencia

- Crear, renombrar, duplicar, seleccionar, eliminar, exportar e importar **perfiles individuales**.
- Cambio de perfil: **previsualización antes de confirmar**, conservando el activo si se cancela.
- Importación: **siempre crear perfil nuevo**. No sobrescribir automáticamente ningún perfil existente, aunque coincidan los nombres; resolver colisiones asignando nombre distinto y ID nuevo.
- Exportación/importación: archivo JSON legible, **versionado**, validado y documentado; un perfil por archivo; mediante Storage Access Framework (`ACTION_OPEN_DOCUMENT`/`ACTION_CREATE_DOCUMENT` o equivalentes), sin permisos de lectura total de almacenamiento.
- Reescalar posiciones y dimensiones de un perfil importado al tamaño/relación de aspecto del equipo, preservando la intención visual, rotaciones y capas.
- Datos desconocidos de versiones más nuevas: **interpretar lo conocido y preservar lo desconocido si puede hacerse de forma segura**, sin permitir que datos no confiables ejecuten acciones. Mantener `extras` o campos desconocidos para futura reexportación cuando sea posible.
- Preferencias de aplicación: DataStore. Perfiles: archivos locales estructurados con operaciones atómicas, versión de esquema, validación y respaldo. No se exige Room inicialmente.
- Recuperación: guardar última configuración válida y copia de respaldo; si el archivo activo queda corrupto, intentar recuperar la copia válida y notificar sin bloquear la app.
- Restablecer perfil: pedir confirmación y **crear respaldo automático antes**.
- Guardado automático no debe perder las últimas operaciones confirmadas si se cierra la configuración o hay una interrupción; usar escritura segura y operaciones coherentes con el historial Deshacer.
- Comportamiento ante fallo abrupto: recuperar los **últimos cambios correctamente guardados**, no inventar/restaurar cambios que nunca se confirmaron.
- Disponer de un perfil predeterminado de fábrica y de una forma de recuperar el sistema si no hay perfiles válidos.

## 7. Integración multimedia Android

- Descubrir y escuchar sesiones multimedia activas por vías **oficiales y permitidas** por Android, cuando exista consentimiento/acceso correspondiente; no basarse en técnicas privadas, rooting ni APIs ocultas.
- Selección automática: **priorizar sesión en reproducción**. Mantener la sesión elegida mientras continúe activa y reproduciendo; si hay varias, evitar saltos de foco innecesarios. Reaccionar oportunamente a una nueva sesión realmente activa y a cambios de estado; resolver empates de forma determinista.
- Leer título, artista, carátula, duración, posición, estado y acciones soportadas. Adaptarse a campos ausentes sin excepciones.
- Comandos play/pause, anterior, siguiente y seek únicamente cuando el `PlaybackState`/transport controls indique que pueden aplicarse; identificar comportamiento fallido o control no compatible.
- Ante sesión perdida: mostrar últimos metadatos con indicador discreto y reconectar cuando aparezca nueva sesión; sin bucles agresivos ni flicker.
- En ausencia de portada válida: tema sólido, no imagen obsoleta.
- Abrir la app de música al tocar portada solo cuando exista acción explícita o `PendingIntent`/intent seguro y permitido.
- No controlar ni pausar apps ajenas al salir de Neon Stereo.
- El control multimedia externo **no implica** disponibilidad de captura de sonido. Son dos subsistemas independientes.

## 8. Captura de audio real: requisitos no negociables

### 8.1 Principios

- Investigar e implementar las alternativas **oficiales** viables en la versión Android real y bajo los permisos aplicables, con **detección automática de capacidades** y degradación independiente.
- Métodos candidatos a evaluar conforme a documentación oficial y restricciones vigentes: `Visualizer` y la captura de reproducción con `AudioPlaybackCaptureConfiguration` + `MediaProjection` y `AudioRecord`, cuando corresponda; no asumir que alguno funcionará con todas las aplicaciones o dispositivos.
- El uso de `Visualizer` con sesión 0/global o sesiones externas puede estar restringido o variar por dispositivo. No presentar su funcionamiento como garantía.
- La captura de reproducción de otras aplicaciones depende de su política de captura, uso de audio, perfil de usuario, versión del sistema, consentimiento y posibles requisitos de servicio de primer plano; cumplir restricciones de Android 14+ y posteriores.
- Los permisos/autorizaciones pueden requerirse de nuevo por sesión; no reutilizar tokens de proyección vencidos ni iniciar capturas sin consentimiento. Si el usuario rechaza, no bombardearlo con diálogos.
- **Sin usar el micrófono como sustituto oculto o automático** de la captura de salida. Evitar cualquier modo que dependa de grabar sonido ambiente salvo una nueva decisión explícita del usuario en el futuro.
- Procesar las muestras temporalmente **en memoria**; no guardar audio, no transferirlo, no generar archivos de audio ni conservar muestras para diagnóstico.
- Captura y renderizado continuo únicamente cuando Neon Stereo está **visible** y la pantalla está encendida. Si se oculta o apaga, cerrar/liberar toda proyección, grabador, visualizador, trabajos y recursos correspondientes. Al regresar, revalidar autorizaciones y reconectar sin asumir que siguen vigentes.
- Mostrar diagnóstico honesto de si se está capturando la mezcla de reproducción, su origen verificable o **vinculación desconocida**. No afirmar que las frecuencias pertenecen al tema cuyos metadatos se ven si no está probado.

### 8.2 Estados y fallos

| Contexto | Comportamiento requerido |
|---|---|
| Reproducción y señal capturable | Analizar datos reales; representar espectro/onda y efectos autorizados. |
| Reproducción sin captura disponible | Mantener reloj, portada, metadatos y controles; visualizadores detenidos; indicador pequeño que se puede ocultar. |
| Audio pausado | Reducir visualizadores/efectos suavemente a cero y detener su animación. |
| No hay sesión | Mantener perfil y reloj; última canción marcada como detenida/no disponible; sin efectos musicales. |
| Sesión cambia | Adaptar metadatos; transiciones solo de portada, fondo y texto; evaluar fuente de audio por separado. |
| App oculta/pantalla apagada | Liberar captura y renderizado; dejar intacta reproducción externa. |
| Permiso rechazado | Seguir con capacidades disponibles; indicador discreto y guía para conceder permiso más tarde. |

### 8.3 Procesado de señal

- Datos de dominio temporal y/o FFT según soporte; mapear correctamente bins/frecuencias y amplitud con escalas justificadas.
- Sensibilidad global y por bandas graves/medios/agudos; cada instancia puede tener ganancia, filtros y suavizado propios.
- Ataque y decaimiento parametrizables, evitando congelamientos artificiales; ajustar para 30 FPS sin latencias innecesarias.
- La presencia de «play» **no es señal de audio**: no convertir metadata/progreso en valores de espectro falsos.
- Si solo hay información parcial real, identificar la limitación y no representar valores inexistentes como espectro completo.

## 9. Rendimiento, gráficos y energía

- Objetivo de 30 FPS, con instrumento de medición y presentación de FPS reales en diagnóstico (idealmente métricas de promedio y caídas).
- Calidad gráfica automática adaptable según carga: reducir partículas, resolución de FFT/dibujo, frecuencia de repintado y complejidad de glow antes de deshabilitar visualizadores.
- Si la carga sigue siendo excesiva, sustituir los visualizadores complejos por **barras de espectro simples derivadas de datos reales**, mientras sea sostenible. **Si las barras simples también perjudican la estabilidad, detenerlas**.
- Sin límite artificial del número de visualizadores: advertir sobre sobrecarga, pero prevenir errores de memoria y recursos.
- Separar flujos de actualizaciones: reloj, metadatos, editor y señal de audio; no recomponer toda la interfaz a cada muestra de audio.
- Separar intensidades de animaciones **de interfaz** de intensidades **musicales**.
- No hacer trabajo de alta frecuencia cuando no hay audio real o la app no está visible.
- Mantener pantalla encendida **solo mientras la interfaz sea visible** mediante mecanismos de ventana adecuados; no forzar encendido del dispositivo ni ignorar apagado del coche.
- No exigir modo pantalla completa/inmersivo. Detectar insets y tamaños útiles.
- Protegerse de OOM, pérdida de contexto gráfico, cambios de configuración y capas duplicadas.

## 10. Configuración, permisos y privacidad

- Ajustes accesibles con pulsación larga de 2 segundos; panel lateral; cierre a los 60 s sin interacción.
- Asistente de permisos que **explica antes de solicitar**: acceso a sesiones multimedia/notificaciones, grabación/captura si la API lo exige y autorización de proyección de pantalla/audio cuando corresponda. No solicitar todos de golpe ni permisos innecesarios.
- Captura automática **cuando sea técnicamente posible y esté autorizada**; solicitar consentimiento cuando sea necesario. Respetar negativas sin solicitudes repetitivas.
- `RECORD_AUDIO` puede ser necesario para APIs concretas, pero no habilita por sí solo la captura de otras apps. No usar micrófono de forma implícita.
- No declarar `INTERNET` en la primera versión. Ningún envío de telemetría, audio, perfiles, carátulas, nombres de canciones o logs a servidores.
- Registros locales mínimos, opcionalmente exportables: versión Android/app, método de captura, estado y tipo de permisos, errores categorizados, FPS y señales de disponibilidad. **Excluir contenido del audio, títulos, artistas, texto íntegro de notificaciones e identificadores personales**.
- Panel de **prueba de compatibilidad visual**: permiso concedido/denegado, sesión seleccionada, método de captura probado, resultado, nivel de señal, bandas reales disponibles, origen (mezcla o asociado si se puede demostrar), motivo de falla, FPS y sugerencias razonables de acciones sin prometer éxito.
- Logs exportados localmente bajo acción explícita; opción de limpiar registros.
- Idiomas español/inglés con textos externos a componentes y pruebas básicas de localización.

## 11. Arquitectura interna propuesta (decidida en módulo 25)

**Arquitectura modular interna y desacoplada**, no necesariamente múltiples módulos Gradle. Debe ser legible, testeable y apta para futuras ampliaciones, sin sobreingeniería.

Subsistemas separados mediante interfaces, repositorios y flujo de estado:

1. `app/ui`: Activity Compose, modo presentación, editor y menús.
2. `elements`: registro de tipos y factorías de instancias, esquemas de propiedades y renderizadores.
3. `media`: descubrimiento/selección de sesiones, metadatos, `PlaybackState`, envío seguro de comandos.
4. `audio`: autorización, estrategias de captura, estados, buffer temporal, FFT/bandas, análisis y liberación.
5. `visuals`: renderizado musical a 30 FPS objetivo, calidad adaptable y animaciones de interfaz.
6. `profiles`: esquemas versionados, adaptador de resolución, JSON seguro, backups, historial de sesión y DataStore.
7. `diagnostics`: pruebas de capacidades, FPS, fallas, logs locales filtrados.
8. `localization`: recursos de cadenas y soporte ES/EN.

**Registro de tipos:** cada familia visual define su `typeId`, propiedades comunes, propiedades específicas y representación. Todos los elementos deben ser editables desde un editor genérico basado en metadatos del tipo, con validaciones por propiedad; evitar ramas ad hoc para cada instancia fija.

**Estado reactivo:** flujos independientes (p. ej., Kotlin Flow/StateFlow) para audio, multimedia, reloj, editor y preferencias. Mantener carga en hilos adecuados y garantizar limpieza por ciclo de vida.

**Render híbrido:** priorizar Compose y Canvas para escenas y controles; incorporar un renderer especializado únicamente donde haga falta para frame pacing/eficiencia. No inventar dependencia de OpenGL/Vulkan obligatoria.

**Dependencias:** elegir versiones compatibles y estables en el entorno de desarrollo; no prometer versiones futuras o librerías no verificadas. Mantener dependencias mínimas, documentar repositorios y versiones, y no requerir red en ejecución de la app.

## 12. Diseño detallado de datos e interoperabilidad

El formato de perfiles debe incluir como mínimo: versión del documento/esquema; ID y nombre del perfil; dimensiones/relación de aspecto de referencia; ajustes de fondo/paleta; lista ordenada de instancias con propiedades comunes y específicas; configuración visual/audio por instancia; información de compatibilidad y `extras` para propiedades desconocidas seguras.

**Condiciones de seguridad y robustez:**

- Validar tipos, rangos, tamaños máximos de archivos, cantidades de elementos (sin imponer límite bajo arbitrario), colores HEX, identificadores, números finitos y estructura de perfiles.
- No cargar rutas arbitrarias, código, URIs externas o contenido ejecutable desde el JSON.
- Conservar datos desconocidos de manera segura y documentada; si no se pueden preservar, avisar antes de importar y mantener intacto el original.
- IDs únicos para perfiles e instancias; colisiones resueltas con nuevos IDs.
- Las duplicaciones preservan todas las propiedades iniciales y asignan IDs nuevos.
- Adaptación proporcional por dimensiones de referencia, considerando insets y rotación; garantizar que al menos una parte del elemento siga visible.
- Establecer operaciones atómicas y recuperación desde último respaldo válido; probar corrupción y corte de escritura.

## 13. Experiencia de uso y flujos principales

### 13.1 Primer inicio

Mostrar composición predeterminada inmediatamente, sin exigir que haya música. Ofrecer una guía breve de permisos, permitiendo omitir lo que no se quiera autorizar. Mostrar diagnóstico disponible, no un error de pantalla completa.

### 13.2 Apertura de configuración

Pulsación larga de 2 s en espacio libre; panel lateral con categorías **Diseño** y **Aplicación**. Seleccionar elemento por toque, con preferencia a la capa superior; ajustar con sliders (aplican al soltar) y +/−; autosalvado. Cerrar manualmente o luego de 60 s sin interacción.

### 13.3 Cambiar perfil

Mostrar previsualización y confirmación explícita; al cancelar conservar perfil actual sin alteraciones. Importación siempre crea perfil nuevo. Restablecimiento pide confirmación y guarda respaldo.

### 13.4 Reproducción

Detectar sesión que realmente reproduce, mostrar metadatos y carátula; al cambio de tema deslizar únicamente elementos musicales. Seek y transporte si se soportan. Gestos horizontales fuera de controles/barra, deshabilitados en editor.

### 13.5 Sin captura autorizada

Mostrar pantalla musical y reloj sin visualizadores falsos; indicador pequeño, ocultable, con acceso a detalles de compatibilidad. No bloquear el uso del editor.

## 14. Validación, pruebas y aceptación

**La entrega no será aceptable como «terminada» si solo contiene apariencia.** Todos los requisitos implementables deben existir, y los condicionados deben tener camino funcional, prueba y limitación registrada. No se obliga a que un estéreo arbitrario permita capturar cualquier reproductor.

### 14.1 Escenarios funcionales obligatorios (módulo 27)

| ID | Escenario | Resultado verificable |
|---|---|---|
| E01 | Inicio sin música | Perfil y reloj visibles; efecto musical detenido; sin crash. |
| E02 | Reproducción compatible | Sesión y comandos compatibles; audio real activa visualizadores solo con captura válida. |
| E03 | Cambio de canción | Se actualizan datos; transicionan portada, fondo y textos; controles/reloj fijos. |
| E04 | Captura bloqueada | Diagnóstico explica motivo; efectos detenidos; resto operativo. |
| E05 | Edición de elementos | Selección superior; duplicar, editar, rotar, capas, undo y autosave. |
| E06 | Cambio de resolución | Diseño y perfil importado se adaptan proporcionalmente; elementos accesibles. |
| E07 | Perfil dañado | Respaldo recupera último estado válido; aviso; app no queda inservible. |
| E08 | Pantalla apagada | Recursos de captura/dibujo liberados; reproducción externa intacta. |

### 14.2 Casos adicionales imprescindibles

- Crear 2+ instancias de reloj, portada, controles y visualizador con propiedades diferentes; datos correctos y un solo origen compartido.
- Varias sesiones activas: elegir reproducción real, estabilidad del foco y ausencia de saltos inesperados.
- Permisos denegados/revocados, consentimiento MediaProjection cancelado, reproducción capturable/no capturable y reconexión.
- Comandos multimedia no soportados: controles desactivados, sin falsa confirmación de éxito.
- Ausencia de artista/portada/duración: ocultar campos; fondo sólido si no hay portada.
- Barra de progreso y seek: correcto en reproducción, pausa y fuentes sin búsqueda.
- Rotación libre, límites de pantalla, superposición, capa superior seleccionable, área táctil mínima.
- Undo de propiedad, duplicación y eliminación en la misma sesión de edición.
- Guardado de slider al soltar; ajuste fino y autosave confirmado.
- Importación inválida, esquema desconocido, campos preservables, perfil con mismo nombre, cancelación de selección, respaldo y restauración.
- Configuración de idioma y configuración en segundo plano/no activo.
- Gráficos intensivos: 30 FPS como objetivo, adaptación gradual, preservación de barras simples si viable.
- Validar que **ningún** visualizador o partícula musical emita actividad sin señal de audio válida.

### 14.3 Estrategia de pruebas

- Pruebas unitarias de perfiles/validación, modelo de instancias, formato JSON, escalado, selección de sesiones, máquina de estados y suavizado de señal.
- Pruebas de interfaz / instrumentadas donde las herramientas del entorno lo permitan.
- Compilación reproducible en debug y release (firmas de release bajo control del usuario; si falta keystore no inventarlo).
- Prueba manual guiada en estéreo Android real: anexar checklist; no marcar como «pasado» si no hay equipo conectado.
- Matriz de compatibilidad por versión Android cuando pueda verificarse.
- Medición transparente de FPS y memoria; no fabricar benchmarks.
- **Informe con cada prueba realmente ejecutada**, comando/ambiente, resultado, evidencia o log y limitaciones; distinguir `PASS`, `FAIL`, `BLOCKED` y `NOT_RUN`.

### 14.4 Criterios mínimos para considerar la entrega completa

1. APK construido, instalable y comprobado en entorno disponible; si no puede construirse por ausencia de SDK/herramientas, declararlo expresamente y entregar proyecto reproducible, no afirmar que existe APK.
2. Interfaz horizontal funcional y adaptable.
3. Editor guarda/restaura todas las propiedades definidas y múltiples instancias.
4. Comandos multimedia correctamente integrados para sesiones compatibles.
5. Captura real implementada mediante métodos oficiales factibles y diagnósticos verificables; si el dispositivo o el reproductor bloquean captura, documentarlo.
6. Perfiles exportables, importables, duplicables y recuperables ante errores.
7. Se cumplen los escenarios E01–E08 hasta donde el entorno de prueba permita; documentar lo que no se pudo probar.

## 15. Entregables obligatorios para OpenCode

- **Repositorio/proyecto Android completo** en Kotlin, estructura clara, código fuente y recursos incluidos.
- Scripts Gradle wrapper y configuración de compilación reproducible.
- **APK debug instalable** si el entorno cuenta con SDK/Gradle/Java y la compilación termina correctamente. Indicar ruta real del APK generado, versión, SDK mínimo y cómo instalarlo.
- `README.md`: requisitos del entorno, compilación, instalación, primeros pasos, idioma, perfiles, permisos y compatibilidad.
- `ARCHITECTURE.md`: módulos internos, contrato de elementos, estado multimedia, estrategias de captura, render y persistencia.
- `USER_GUIDE.md`: configuración paso a paso, gestos, editor, importación/exportación, solución de problemas en el estéreo (preferiblemente español).
- `TEST_REPORT.md`: pruebas ejecutadas y no ejecutadas, comandos, resultados, evidencias y limitaciones.
- `KNOWN_LIMITATIONS.md`: incompatibilidades de captura y sesiones, restricciones del sistema, dispositivos aún no probados y riesgos técnicos.
- Ejemplo válido de JSON de perfil y documentación de su esquema/versionado.
- Logs exportables mínimos desde la propia aplicación, con datos sensibles excluidos.
- No incluir credenciales, keystores inventadas, permisos superfluos ni SDKs de seguimiento.

## 16. Instrucciones concretas de trabajo para OpenCode

1. Leer integralmente este documento antes de diseñar la estructura de código.
2. Crear una tabla interna de trazabilidad `ID de requisito -> componente -> prueba -> estado` para evitar omisiones.
3. Confirmar herramientas realmente disponibles (JDK, Android SDK, Gradle, emulador/dispositivo); elegir versiones compatibles y registrar las decisiones.
4. Implementar **la app completa** y todos los componentes descritos, no una demo estática de la imagen.
5. Separar MediaSession de captura real de audio y separar ambas del renderer/editor.
6. Mantener una única fuente de datos compartida por cada subsistema; múltiples instancias visuales independientes.
7. Implementar manejo de permisos legítimo y moderno según API real, incluyendo casos de revocación y expiración.
8. Compilar y corregir errores; ejecutar pruebas posibles; no concluir con solo pseudocódigo.
9. Mostrar el estado real de cada funcionalidad y las capacidades no verificables.
10. Entregar una explicación precisa de cómo abrir, instalar, ejecutar y probar en un estéreo Android 12+.
11. No agregar por iniciativa propia servicios de nube, publicidad, analíticas, login, launcher ni micrófono como fuente automática.
12. Si una instrucción parece técnicamente imposible en un dispositivo concreto, **no mentir ni simular**: implementar lo máximo permitido y dejar causa, versión Android, permisos y reproducción del fallo en diagnóstico y documentación.
13. Aplicar prioridad: **estabilidad > integración funcional > persistencia/editor > calidad visual accesoria** sin eliminar funcionalidades obligatorias.

## 17. Riesgos y restricciones técnicas explícitas

- El acceso a sesiones multimedia de otros reproductores requiere autorizaciones y depende de APIs/implementaciones concretas; no es garantía universal.
- La captura de audio de apps externas está condicionada a políticas de captura, contenido protegido, versión de Android, usuario y consentimiento. Una autorización de MediaProjection no obliga a Spotify u otra app a permitir que se capture su audio.
- No confundir el permiso `RECORD_AUDIO` con acceso automático a la mezcla del dispositivo.
- Algunas APIs de captura global o sesión 0 pueden producir silencio, no estar soportadas o exigir privilegiados; jamás depender de ellas como única ruta sin advertencia.
- El número de FPS y la suavidad dependen del hardware; no prometer 30 FPS constantes en cualquier radio.
- Las pruebas automatizadas no sustituyen una verificación en el equipo físico real.
- Si hay audio mezclado, el diagnóstico debe indicar que no se puede asegurar la asociación a la canción mostrada.

## 18. Decisiones consolidadas y posibles puntos abiertos

Las selecciones de los **módulos 1 a 28 están confirmadas por el usuario**. El módulo 29 fue presentado, pero **sus tres elecciones no fueron respondidas explícitamente**. Para no bloquear el desarrollo, se sugieren los siguientes valores, identificados como propuestas y no como decisiones firmes:

1. **Propiedades desconocidas de perfiles:** conservar campos desconocidos de forma segura y mostrar **advertencia informativa** al importarlos; nunca ejecutarlos ni modificarlos arbitrariamente.
2. **Diagnóstico multimedia:** mostrar separados el reproductor seleccionado, la fuente de audio realmente capturada y el grado de vinculación verificable entre ambos.
3. **Informe de pruebas:** resultados **detallados por prueba, con evidencias disponibles**, no solo un resumen.

Estos valores son consistentes con los módulos 26–28 y se consideran **defaults de implementación propuestos**, sujetos a revisión del usuario. Si una futura indicación explícita los modifica, prevalece la modificación.

### Otras reglas de interpretación necesarias

- **«Sin música» vs «última canción»:** permitir última información cacheada con estado marcado; nunca hacerla pasar por reproducción activa.
- **«Visualizadores sin límite» vs rendimiento:** no imponer un cupo arbitrario al usuario; sí poner límites técnicos defensivos de memoria/buffers y adaptar/desactivar recursos peligrosos sin provocar un crash.
- **«Color configurable» vs degradados:** RGB/HEX y paletas para instancias aplicables; gradientes por visualizador cuando corresponda, sin obligar al editor universal a ofrecer propiedades absurdas para componentes que no las usan.
- **«30 FPS» vs reloj:** reloj y metadatos no se recalculan a 30 Hz; únicamente el renderer musical en actividad lo intenta.
- **«Deslizamiento de portada y fondo» vs ausencia de portada:** interpolar hacia color sólido del tema sin mostrar carátula previa como sustituto permanente.
- **«Rotación libre» vs controles táctiles:** transformar apariencia y geometría de hit-testing o utilizar un área de interacción coherente y accesible; no perder mínimo de 56 dp.
- **«Vista previa en tiempo real» vs aplicar slider al soltar:** previsualizar **cada cambio confirmado** inmediatamente; opcionalmente mostrar valor provisional durante el arrastre, sin aplicar hasta liberarlo.
- **«Grabación» de audio:** ninguna; procesar buffers transitorios únicamente para visualización autorizada, sin persistencia.
- **Prioridad de sesión:** elegir la que reproduce y evitar cambios de sesión arbitrarios; si todas están pausadas/no activas, conservar última conocida con estado explícito.
- **Compatibilidad con pantalla apagada:** liberar captura y render, aunque al reabrir sea necesaria nueva autorización de proyección.

## 19. Lista final de comprobación para el desarrollador

- [ ] Es un proyecto Android **nativo y completo**, no una maqueta.
- [ ] `minSdk 29`, landscape, UI ES/EN, sin modo inmersivo obligatorio.
- [ ] Reloj LED puntos, siete segmentos y fuentes normales configurables.
- [ ] Portada, artista, título, progreso, tiempos y controles agrupados reales.
- [ ] Transiciones solo de portada, fondo y textos; reloj y controles fijos.
- [ ] Cinco visualizadores, partículas, glow y pulso, con señal **real** o completamente detenidos.
- [ ] Editor genérico de instancias con slider al soltar, ajuste fino, duplicación, rotación, capas y undo.
- [ ] Configuración lateral a los 2 s; cierre por inactividad 60 s.
- [ ] Perfiles versionados, autosave, backup, importación siempre como nuevo y exportación individual.
- [ ] Audio/MediaSession completamente desacoplados; permisos y diagnóstico transparentes.
- [ ] Liberación de recursos al ocultar o apagar pantalla; no detener reproducción externa.
- [ ] Adaptación de calidad con 30 FPS objetivo y barras simples de respaldo si son sostenibles.
- [ ] Sin permiso de internet inicial; sin persistir audio ni registrar títulos/artistas.
- [ ] Pruebas E01–E08 y adicionales; resultados no inventados.
- [ ] APK real si es posible construir; README, guía, arquitectura, pruebas y limitaciones.

---

**Fin de la especificación maestra Neon Stereo.**  
**Regla de precedencia:** decisiones explícitas más recientes del usuario > decisiones consolidadas de este documento > defaults propuestos del módulo 29 > decisiones técnicas justificadas del equipo de desarrollo. Si hay conflictos irresolubles, conservar privacidad, APIs oficiales, estabilidad y honestidad de diagnóstico antes que una apariencia engañosa.
