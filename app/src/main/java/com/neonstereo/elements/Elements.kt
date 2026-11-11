package com.neonstereo.elements

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/** Tipos estables de elementos (§4, §5.1). */
object ElementTypes {
    const val CLOCK = "clock"
    const val DATE = "date"
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val COVER = "cover"
    const val CONTROLS = "controls"
    const val PROGRESS = "progress"
    const val TIME_CURRENT = "time_current"
    const val TIME_TOTAL = "time_total"
    const val SPECTRUM_BARS = "spectrum_bars"
    const val EDGE_BARS = "edge_bars"
    const val WAVE = "wave"
    const val DOT_EQ = "dot_eq"
    const val RADIAL = "radial"
    const val STAR_PARTICLES = "star_particles"
    const val BACKGROUND = "background"
}

@Serializable
data class ElementInstance(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val displayName: String = type,
    val visible: Boolean = true,
    /** Proporciones 0..1 del área de referencia. */
    val x: Float = 0.05f,
    val y: Float = 0.05f,
    val width: Float = 0.3f,
    val height: Float = 0.2f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val zIndex: Int = 0,
    val colorHex: String = "#00E5FF",
    val glow: Float = 0.5f,
    val aspectRatioLocked: Boolean = false,
    val schemaVersion: Int = 1,
    val specific: JsonObject = buildJsonObject {},
)

fun ElementInstance.duplicate(): ElementInstance = copy(
    id = UUID.randomUUID().toString(),
    displayName = "$displayName 2",
    x = (x + 0.03f).coerceIn(0f, 0.95f),
    y = (y + 0.03f).coerceIn(0f, 0.95f),
)
