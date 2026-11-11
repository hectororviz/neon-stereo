package com.neonstereo.profiles

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neonstereo.elements.ElementInstance
import com.neonstereo.elements.ElementTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

private val Context.prefs by preferencesDataStore("neon_prefs")

@Serializable
data class AppProfile(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Dividido",
    val refWidth: Int = 1280,
    val refHeight: Int = 720,
    val palette: List<String> = listOf("#00E5FF", "#FF2BD6", "#0A0A12"),
    val backgroundHex: String = "#000000",
    val elements: List<ElementInstance> = emptyList(),
    val extras: JsonObject = buildJsonObject {},
)

object ProfileStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val ACTIVE = stringPreferencesKey("active_profile")
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val SCHEMA = 1

    fun dir(ctx: Context): File = File(ctx.filesDir, "profiles").apply { mkdirs() }

    fun factoryProfile(): AppProfile {
        fun el(type: String, name: String, x: Float, y: Float, w: Float, h: Float, z: Int, extra: JsonObject = buildJsonObject {}) =
            ElementInstance(type = type, displayName = name, x = x, y = y, width = w, height = h, zIndex = z, specific = extra)
        return AppProfile(
            schemaVersion = SCHEMA,
            id = "factory-dividido",
            name = "Dividido",
            elements = listOf(
                el(ElementTypes.BACKGROUND, "Fondo", 0f, 0f, 1f, 1f, -100,
                    buildJsonObject { put("mode", "blur_cover"); put("blur", 0.75f); put("dim", 0.8f); put("pulse", 0.5f) }),
                el(ElementTypes.CLOCK, "Reloj LED", 0.55f, 0.08f, 0.4f, 0.3f, 10,
                    buildJsonObject { put("font", "led"); put("h24", true); put("seconds", false); put("blink", true) }),
                el(ElementTypes.DATE, "Fecha", 0.55f, 0.36f, 0.4f, 0.08f, 10),
                el(ElementTypes.COVER, "Portada", 0.04f, 0.1f, 0.34f, 0.6f, 10,
                    buildJsonObject { put("corner", 0.12f); put("fit", false) }),
                el(ElementTypes.TITLE, "Título", 0.04f, 0.72f, 0.34f, 0.09f, 10),
                el(ElementTypes.ARTIST, "Artista", 0.04f, 0.8f, 0.34f, 0.07f, 10),
                el(ElementTypes.CONTROLS, "Controles", 0.55f, 0.5f, 0.4f, 0.14f, 10),
                el(ElementTypes.PROGRESS, "Progreso", 0.55f, 0.66f, 0.4f, 0.05f, 10,
                    buildJsonObject { put("thickness", 6f) }),
                el(ElementTypes.TIME_CURRENT, "Transcurrido", 0.55f, 0.72f, 0.19f, 0.06f, 10),
                el(ElementTypes.TIME_TOTAL, "Duración", 0.76f, 0.72f, 0.19f, 0.06f, 10),
                el(ElementTypes.EDGE_BARS, "Barras bordes", 0f, 0f, 1f, 0.12f, 20,
                    buildJsonObject { put("bands", 48f); put("sensitivity", 1f); put("smoothing", 0.6f); put("band", "all") }),
                el(ElementTypes.STAR_PARTICLES, "Estrellas", 0f, 0f, 1f, 1f, 5,
                    buildJsonObject { put("count", 120f); put("band", "all"); put("sensitivity", 1f) }),
            ),
        )
    }

    suspend fun list(ctx: Context): List<AppProfile> {
        ensureFactory(ctx)
        return dir(ctx).listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            try {
                if (f.length() > MAX_BYTES) return@mapNotNull null
                json.decodeFromString<AppProfile>(f.readText())
            } catch (_: Exception) { null }
        }?.sortedBy { it.name } ?: emptyList()
    }

    /** Plantillas de fábrica adicionales (Minimal / Nocturno / Bordes). */
    fun factoryMinimal(): AppProfile {
        fun el(type: String, name: String, x: Float, y: Float, w: Float, h: Float, z: Int, extra: JsonObject = buildJsonObject {}) =
            ElementInstance(type = type, displayName = name, x = x, y = y, width = w, height = h, zIndex = z, specific = extra)
        return AppProfile(
            schemaVersion = SCHEMA, id = "factory-minimal", name = "Minimal",
            elements = listOf(
                el(ElementTypes.BACKGROUND, "Fondo", 0f, 0f, 1f, 1f, -100,
                    buildJsonObject { put("mode", "black"); put("blur", 0f); put("dim", 1f); put("pulse", 0f) }),
                el(ElementTypes.CLOCK, "Reloj", 0.25f, 0.15f, 0.5f, 0.4f, 10,
                    buildJsonObject { put("font", "seven"); put("fontSize", 120f); put("h24", true); put("seconds", false); put("blink", false) }),
                el(ElementTypes.CONTROLS, "Controles", 0.3f, 0.6f, 0.4f, 0.2f, 10),
            ),
        )
    }

    fun factoryNocturno(): AppProfile {
        fun el(type: String, name: String, x: Float, y: Float, w: Float, h: Float, z: Int, extra: JsonObject = buildJsonObject {}) =
            ElementInstance(type = type, displayName = name, x = x, y = y, width = w, height = h, zIndex = z, specific = extra)
        return AppProfile(
            schemaVersion = SCHEMA, id = "factory-nocturno", name = "Nocturno",
            elements = listOf(
                el(ElementTypes.BACKGROUND, "Fondo", 0f, 0f, 1f, 1f, -100,
                    buildJsonObject { put("mode", "black"); put("blur", 0f); put("dim", 1f); put("pulse", 0.4f) }),
                el(ElementTypes.CLOCK, "Reloj LED", 0.05f, 0.05f, 0.5f, 0.35f, 10,
                    buildJsonObject { put("font", "led"); put("fontSize", 84f); put("h24", true); put("seconds", true); put("blink", true) }),
                el(ElementTypes.RADIAL, "Radial", 0.58f, 0.05f, 0.37f, 0.7f, 10,
                    buildJsonObject { put("bands", 48f); put("sensitivity", 1.2f) }),
                el(ElementTypes.TITLE, "Título", 0.05f, 0.78f, 0.5f, 0.1f, 10,
                    buildJsonObject { put("fontSize", 26f) }),
                el(ElementTypes.CONTROLS, "Controles", 0.05f, 0.6f, 0.4f, 0.14f, 10),
                el(ElementTypes.PROGRESS, "Progreso", 0.05f, 0.9f, 0.9f, 0.05f, 10,
                    buildJsonObject { put("thickness", 6f) }),
                el(ElementTypes.STAR_PARTICLES, "Estrellas", 0f, 0f, 1f, 1f, 5,
                    buildJsonObject { put("count", 150f); put("band", "mid"); put("sensitivity", 1.2f) }),
            ),
        )
    }

    fun factoryBordes(): AppProfile {
        fun el(type: String, name: String, x: Float, y: Float, w: Float, h: Float, z: Int, extra: JsonObject = buildJsonObject {}) =
            ElementInstance(type = type, displayName = name, x = x, y = y, width = w, height = h, zIndex = z, specific = extra)
        return AppProfile(
            schemaVersion = SCHEMA, id = "factory-bordes", name = "Bordes",
            elements = listOf(
                el(ElementTypes.BACKGROUND, "Fondo", 0f, 0f, 1f, 1f, -100,
                    buildJsonObject { put("mode", "blur_cover"); put("blur", 0.75f); put("dim", 0.8f); put("pulse", 0.5f) }),
                el(ElementTypes.EDGE_BARS, "Barras bordes", 0f, 0f, 1f, 0.14f, 20,
                    buildJsonObject { put("bands", 48f); put("sensitivity", 1f); put("smoothing", 0.6f); put("band", "all") }),
                el(ElementTypes.CLOCK, "Reloj", 0.3f, 0.2f, 0.4f, 0.3f, 10,
                    buildJsonObject { put("font", "led"); put("fontSize", 72f); put("h24", true); put("seconds", false); put("blink", true) }),
                el(ElementTypes.COVER, "Portada", 0.06f, 0.25f, 0.22f, 0.5f, 10,
                    buildJsonObject { put("corner", 0.15f); put("fit", false) }),
                el(ElementTypes.TITLE, "Título", 0.32f, 0.55f, 0.36f, 0.1f, 10,
                    buildJsonObject { put("fontSize", 24f) }),
                el(ElementTypes.ARTIST, "Artista", 0.32f, 0.65f, 0.36f, 0.08f, 10,
                    buildJsonObject { put("fontSize", 18f) }),
                el(ElementTypes.CONTROLS, "Controles", 0.7f, 0.4f, 0.25f, 0.2f, 10),
                el(ElementTypes.PROGRESS, "Progreso", 0.32f, 0.78f, 0.36f, 0.05f, 10,
                    buildJsonObject { put("thickness", 8f) }),
                el(ElementTypes.SPECTRUM_BARS, "Espectro", 0.32f, 0.85f, 0.6f, 0.12f, 10,
                    buildJsonObject { put("bands", 32f); put("sensitivity", 1f); put("smoothing", 0.6f); put("band", "all") }),
            ),
        )
    }

    fun factories(): List<AppProfile> = listOf(factoryProfile(), factoryMinimal(), factoryNocturno(), factoryBordes())

    /** Nuevo perfil a partir de una plantilla (siempre ID nuevo, §6). */
    suspend fun newFromTemplate(ctx: Context, templateId: String): AppProfile {
        val t = factories().firstOrNull { it.id == templateId } ?: factoryProfile()
        var name = t.name
        val existing = list(ctx).map { it.name }.toSet()
        var i = 2
        while (existing.contains(name)) { name = "${t.name} ($i)"; i++ }
        val np = t.copy(id = UUID.randomUUID().toString(), name = name, schemaVersion = SCHEMA)
        save(ctx, np)
        return np
    }

    suspend fun load(ctx: Context, id: String): AppProfile? {
        val f = File(dir(ctx), "$id.json")
        val bak = File(dir(ctx), "$id.json.bak")
        fun read(file: File): AppProfile? = try {
            if (!file.exists() || file.length() > MAX_BYTES) null
            else json.decodeFromString<AppProfile>(file.readText())
        } catch (_: Exception) { null }
        return read(f) ?: read(bak)?.also { save(ctx, it) } ?: factoryProfile()
    }

    /** Escritura atómica tmp+rename + backup (§6). */
    suspend fun save(ctx: Context, p: AppProfile) {
        val d = dir(ctx)
        val target = File(d, "${p.id}.json")
        if (target.exists()) target.copyTo(File(d, "${p.id}.json.bak"), overwrite = true)
        val tmp = File(d, "${p.id}.json.tmp")
        tmp.writeText(json.encodeToString(p.copy(schemaVersion = SCHEMA)))
        require(tmp.length() <= MAX_BYTES) { "profile too large" }
        tmp.renameTo(target)
    }

    suspend fun importJson(ctx: Context, raw: String): AppProfile {
        require(raw.length <= MAX_BYTES) { "file too large" }
        val parsed = json.decodeFromString<AppProfile>(raw)
        // Siempre crear perfil nuevo con ID nuevo (§6).
        var name = parsed.name.ifBlank { "Importado" }
        val existing = list(ctx).map { it.name }.toSet()
        var i = 2
        while (existing.contains(name)) { name = "${parsed.name} ($i)"; i++ }
        val imported = parsed.copy(id = UUID.randomUUID().toString(), name = name, schemaVersion = SCHEMA)
        save(ctx, imported)
        return imported
    }

    fun exportJson(p: AppProfile): String = json.encodeToString(p)

    suspend fun activeId(ctx: Context): String = ctx.prefs.data.map { it[ACTIVE] ?: "factory-dividido" }.first()
    suspend fun setActive(ctx: Context, id: String) { ctx.prefs.edit { it[ACTIVE] = id } }
    fun activeFlow(ctx: Context): Flow<String> = ctx.prefs.data.map { it[ACTIVE] ?: "factory-dividido" }

    private suspend fun ensureFactory(ctx: Context) {
        for (f in factories()) {
            val file = File(dir(ctx), "${f.id}.json")
            if (!file.exists()) save(ctx, f)
        }
    }
}
