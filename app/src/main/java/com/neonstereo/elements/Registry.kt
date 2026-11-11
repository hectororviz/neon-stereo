package com.neonstereo.elements

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Descriptor genérico para editor basado en metadatos (§11). */
data class PropDef(
    val key: String,
    val labelEs: String,
    val labelEn: String,
    val kind: Kind,
    val min: Float = 0f,
    val max: Float = 1f,
    val advanced: Boolean = false,
) {
    enum class Kind { SLIDER, SWITCH, COLOR, TEXT, SELECT }
}

object ElementRegistry {
    fun commonProps(): List<PropDef> = listOf(
        PropDef("x", "X", "X", PropDef.Kind.SLIDER, 0f, 1f),
        PropDef("y", "Y", "Y", PropDef.Kind.SLIDER, 0f, 1f),
        PropDef("width", "Ancho", "Width", PropDef.Kind.SLIDER, 0.02f, 1f),
        PropDef("height", "Alto", "Height", PropDef.Kind.SLIDER, 0.02f, 1f),
        PropDef("scale", "Escala", "Scale", PropDef.Kind.SLIDER, 0.2f, 3f),
        PropDef("rotationDegrees", "Rotación", "Rotation", PropDef.Kind.SLIDER, -180f, 180f, advanced = true),
        PropDef("opacity", "Opacidad", "Opacity", PropDef.Kind.SLIDER, 0f, 1f),
        PropDef("colorHex", "Color", "Color", PropDef.Kind.COLOR),
        PropDef("glow", "Glow", "Glow", PropDef.Kind.SLIDER, 0f, 1f, advanced = true),
        PropDef("visible", "Visible", "Visible", PropDef.Kind.SWITCH),
        PropDef("aspectRatioLocked", "Bloquear proporción", "Lock aspect", PropDef.Kind.SWITCH, advanced = true),
    )

    fun specificProps(type: String): List<PropDef> = when (type) {
        ElementTypes.CLOCK -> listOf(
            PropDef("font", "Fuente (led/seven/sans/mono)", "Font", PropDef.Kind.TEXT),
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 240f),
            PropDef("h24", "24 horas", "24h", PropDef.Kind.SWITCH),
            PropDef("seconds", "Segundos", "Seconds", PropDef.Kind.SWITCH),
            PropDef("blink", "Parpadeo :", "Blink :", PropDef.Kind.SWITCH),
            PropDef("sensitivity", "Brillo puntos", "Dot brightness", PropDef.Kind.SLIDER, 0f, 1f, advanced = true),
        )
        ElementTypes.COVER -> listOf(
            PropDef("corner", "Esquinas 0..1", "Corners", PropDef.Kind.SLIDER, 0f, 1f),
            PropDef("fit", "Mostrar completa (fit)", "Fit", PropDef.Kind.SWITCH),
        )
        ElementTypes.PROGRESS -> listOf(
            PropDef("thickness", "Grosor", "Thickness", PropDef.Kind.SLIDER, 1f, 24f),
        )
        ElementTypes.SPECTRUM_BARS, ElementTypes.EDGE_BARS -> listOf(
            PropDef("bands", "Bandas", "Bands", PropDef.Kind.SLIDER, 8f, 64f),
            PropDef("sensitivity", "Sensibilidad", "Sensitivity", PropDef.Kind.SLIDER, 0f, 2f),
            PropDef("smoothing", "Suavizado", "Smoothing", PropDef.Kind.SLIDER, 0f, 0.95f, advanced = true),
            PropDef("band", "Banda (low/mid/high/all)", "Band", PropDef.Kind.TEXT, advanced = true),
        )
        ElementTypes.WAVE -> listOf(
            PropDef("amplitude", "Amplitud", "Amplitude", PropDef.Kind.SLIDER, 0f, 1f),
            PropDef("smoothing", "Suavizado", "Smoothing", PropDef.Kind.SLIDER, 0f, 0.95f, advanced = true),
            PropDef("sensitivity", "Sensibilidad", "Sensitivity", PropDef.Kind.SLIDER, 0f, 2f),
        )
        ElementTypes.DOT_EQ -> listOf(
            PropDef("rows", "Filas", "Rows", PropDef.Kind.SLIDER, 4f, 24f),
            PropDef("cols", "Columnas", "Cols", PropDef.Kind.SLIDER, 8f, 48f),
            PropDef("sensitivity", "Sensibilidad", "Sensitivity", PropDef.Kind.SLIDER, 0f, 2f),
        )
        ElementTypes.RADIAL -> listOf(
            PropDef("bands", "Barras", "Bars", PropDef.Kind.SLIDER, 12f, 96f),
            PropDef("sensitivity", "Sensibilidad", "Sensitivity", PropDef.Kind.SLIDER, 0f, 2f),
        )
        ElementTypes.STAR_PARTICLES -> listOf(
            PropDef("count", "Cantidad", "Count", PropDef.Kind.SLIDER, 10f, 400f),
            PropDef("band", "Banda (low/mid/high/all)", "Band", PropDef.Kind.TEXT),
            PropDef("sensitivity", "Sensibilidad", "Sensitivity", PropDef.Kind.SLIDER, 0f, 2f),
        )
        ElementTypes.BACKGROUND -> listOf(
            PropDef("mode", "Modo (black/blur_cover/custom)", "Mode", PropDef.Kind.TEXT),
            PropDef("blur", "Desenfoque 0..1 (=75% fábrica)", "Blur", PropDef.Kind.SLIDER, 0f, 1f),
            PropDef("dim", "Oscurecido 0..1 (=80% fábrica)", "Dim", PropDef.Kind.SLIDER, 0f, 1f),
            PropDef("pulse", "Pulso musical 0..1", "Pulse", PropDef.Kind.SLIDER, 0f, 1f),
            PropDef("customUri", "Imagen custom (uri)", "Custom uri", PropDef.Kind.TEXT, advanced = true),
        )
        ElementTypes.TITLE -> listOf(
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 160f),
        )
        ElementTypes.ARTIST -> listOf(
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 160f),
        )
        ElementTypes.DATE -> listOf(
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 160f),
        )
        ElementTypes.TIME_CURRENT -> listOf(
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 160f),
        )
        ElementTypes.TIME_TOTAL -> listOf(
            PropDef("fontSize", "Tamaño texto (sp)", "Font size (sp)", PropDef.Kind.SLIDER, 8f, 160f),
        )
        else -> emptyList()
    }
}

fun JsonObject.floatOr(key: String, def: Float): Float =
    get(key)?.jsonPrimitive?.floatOrNull ?: def
fun JsonObject.boolOr(key: String, def: Boolean): Boolean =
    get(key)?.jsonPrimitive?.booleanOrNull ?: def
fun JsonObject.stringOr(key: String, def: String): String =
    get(key)?.jsonPrimitive?.contentOrNull ?: def
