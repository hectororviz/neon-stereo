package com.neonstereo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonstereo.elements.ElementInstance
import com.neonstereo.elements.ElementRegistry
import com.neonstereo.elements.PropDef

@Composable
fun EditorPanel(
    elements: List<ElementInstance>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onUpdate: (ElementInstance) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: (String) -> Unit,
    onMoveLayer: (String, Int) -> Unit,
) {
    Row(Modifier.fillMaxHeight().width(380.dp).background(Color(0xFF111118)).padding(8.dp)) {
        // Lista capas ordenada por zIndex
        LazyColumn(Modifier.weight(0.9f).fillMaxHeight()) {
            items(elements.sortedBy { it.zIndex }, key = { it.id }) { el ->
                Text(
                    "${el.displayName} [${el.type}]",
                    color = if (el.id == selectedId) Color.Cyan else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(el.id) }.padding(6.dp)
                        .background(if (el.id == selectedId) Color(0xFF222233) else Color.Transparent),
                )
            }
        }
        val sel = elements.firstOrNull { it.id == selectedId }
        Column(Modifier.weight(1.4f).padding(start = 8.dp)) {
            if (sel == null) {
                Text("Tocá un elemento en vista previa (capa superior).", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onUndo) { Text("Deshacer") }
                return@Column
            }
            Text(sel.displayName, color = Color.White, fontSize = 16.sp)
            Text(sel.type, color = Color.Gray, fontSize = 12.sp)
            LazyColumn {
                item {
                    Text("Básicas", color = Color.Cyan, fontSize = 13.sp)
                    CommonEditors(sel, onUpdate, advanced = false)
                    Text("Avanzadas", color = Color.Cyan, fontSize = 13.sp)
                    CommonEditors(sel, onUpdate, advanced = true)
                    SpecificEditors(sel, onUpdate)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Button(onClick = { onDuplicate(sel.id) }) { Text("+Dup") }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { onDelete(sel.id) }) { Text("Del") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Button(onClick = { onMoveLayer(sel.id, -1) }) { Text("Bajar") }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { onMoveLayer(sel.id, 1) }) { Text("Subir") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Button(onClick = onUndo) { Text("Undo") }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { onReset(sel.id) }) { Text("Reset") }
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun CommonEditors(el: ElementInstance, onUpdate: (ElementInstance) -> Unit, advanced: Boolean) {
    for (p in ElementRegistry.commonProps().filter { it.advanced == advanced }) {
        PropRow(p, el, onUpdate)
    }
}

@Composable
private fun SpecificEditors(el: ElementInstance, onUpdate: (ElementInstance) -> Unit) {
    for (p in ElementRegistry.specificProps(el.type)) {
        PropRow(p, el, onUpdate)
    }
    // displayName + type-specific text handled generically abajo
}

@Composable
private fun PropRow(p: PropDef, el: ElementInstance, onUpdate: (ElementInstance) -> Unit) {
    when (p.kind) {
        PropDef.Kind.SLIDER -> {
            var tmp by remember(el.id + p.key) { mutableStateOf(currentFloat(el, p.key, p)) }
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("${p.labelEs}: ${String.format("%.2f", tmp)}", color = Color.White, fontSize = 12.sp)
                Row {
                    Button(onClick = { val v = (tmp - step(p)).coerceIn(p.min, p.max); tmp = v; onUpdate(setFloat(el, p.key, v)) }) { Text("−") }
                    Slider(
                        value = tmp, onValueChange = { tmp = it },
                        onValueChangeFinished = { onUpdate(setFloat(el, p.key, tmp)) },
                        valueRange = p.min..p.max, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { val v = (tmp + step(p)).coerceIn(p.min, p.max); tmp = v; onUpdate(setFloat(el, p.key, tmp)) }) { Text("+") }
                }
            }
        }
        PropDef.Kind.SWITCH -> {
            val cur = currentBool(el, p.key)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(p.labelEs, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(checked = cur, onCheckedChange = { onUpdate(setBool(el, p.key, it)) })
            }
        }
        PropDef.Kind.TEXT, PropDef.Kind.SELECT -> {
            var v by remember(el.id + p.key) { mutableStateOf(currentText(el, p.key)) }
            TextField(value = v, onValueChange = { v = it }, label = { Text(p.labelEs, fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onUpdate(setText(el, p.key, v)) }, modifier = Modifier.padding(top = 2.dp)) { Text("Aplicar", fontSize = 12.sp) }
        }
        PropDef.Kind.COLOR -> {
            var v by remember(el.id + p.key) { mutableStateOf(el.colorHex) }
            TextField(value = v, onValueChange = { v = it }, label = { Text("#HEX", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onUpdate(el.copy(colorHex = v)) }) { Text("Aplicar", fontSize = 12.sp) }
        }
    }
}

private fun step(p: PropDef): Float = (p.max - p.min) / 100f

private fun currentFloat(el: ElementInstance, key: String, p: PropDef): Float = when (key) {
    "x" -> el.x; "y" -> el.y; "width" -> el.width; "height" -> el.height
    "scale" -> el.scale; "rotationDegrees" -> el.rotationDegrees
    "opacity" -> el.opacity; "glow" -> el.glow
    else -> el.specific[key]?.toString()?.trim('"')?.toFloatOrNull() ?: p.min
}
private fun setFloat(el: ElementInstance, key: String, v: Float): ElementInstance = when (key) {
    "x" -> el.copy(x = v); "y" -> el.copy(y = v)
    "width" -> el.copy(width = v); "height" -> el.copy(height = v)
    "scale" -> el.copy(scale = v); "rotationDegrees" -> el.copy(rotationDegrees = v)
    "opacity" -> el.copy(opacity = v); "glow" -> el.copy(glow = v)
    else -> el.copy(specific = withJson(el, key, v.toString()))
}
private fun currentBool(el: ElementInstance, key: String): Boolean = when (key) {
    "visible" -> el.visible
    "aspectRatioLocked" -> el.aspectRatioLocked
    else -> el.specific[key]?.toString() == "true"
}
private fun setBool(el: ElementInstance, key: String, v: Boolean): ElementInstance = when (key) {
    "visible" -> el.copy(visible = v)
    "aspectRatioLocked" -> el.copy(aspectRatioLocked = v)
    else -> el.copy(specific = withJson(el, key, v.toString()))
}
private fun currentText(el: ElementInstance, key: String): String =
    el.specific[key]?.toString()?.trim('"') ?: ""
private fun setText(el: ElementInstance, key: String, v: String): ElementInstance =
    el.copy(specific = withJson(el, key, "\"$v\""))

private fun withJson(el: ElementInstance, key: String, rawJson: String): kotlinx.serialization.json.JsonObject {
    return try {
        val parsed: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.Json.parseToJsonElement(rawJson)
        kotlinx.serialization.json.buildJsonObject {
            el.specific.forEach { (k, e) -> put(k, e) }
            put(key, parsed)
        }
    } catch (_: Exception) {
        kotlinx.serialization.json.buildJsonObject {
            el.specific.forEach { (k, e) -> put(k, e) }
            put(key, kotlinx.serialization.json.JsonPrimitive(rawJson))
        }
    }
}
