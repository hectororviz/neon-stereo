package com.neonstereo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonstereo.diagnostics.DiagSnapshot
import com.neonstereo.profiles.AppProfile

@Composable
fun AppSettingsPanel(
    profiles: List<AppProfile>,
    activeId: String,
    onSelectPreview: (AppProfile) -> Unit,
    onConfirmProfile: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onResetFactory: () -> Unit,
    onNew: (String) -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onPickBackground: () -> Unit,
    diag: DiagSnapshot,
    notifAccess: Boolean,
    recordGranted: Boolean,
    captureInfo: String,
    onRequestNotif: () -> Unit,
    onRequestRecord: () -> Unit,
    onRequestProjection: () -> Unit,
    onStartVisualizer: () -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
    onLanguage: (String) -> Unit,
    previewId: String?,
) {
    LazyColumn(Modifier.fillMaxWidth().padding(12.dp)) {
        item {
            Text("Diseño — perfiles", color = Color.Cyan, fontSize = 15.sp)
            Text("Cambio con previsualización: tocá para previsualizar, confirmar para aplicar.", color = Color.Gray, fontSize = 12.sp)
        }
        items(profiles, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(p.name + if (p.id == activeId) " ●" else "" + if (p.id == previewId) " (vista previa)" else "",
                        color = Color.White, fontSize = 14.sp)
                    Text("${p.elements.size} elementos · ${p.refWidth}x${p.refHeight}", color = Color.Gray, fontSize = 11.sp)
                }
                Button(onClick = { onSelectPreview(p) }) { Text("Ver", fontSize = 12.sp) }
                Button(onClick = { onConfirmProfile(p.id) }) { Text("OK", fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { onDuplicate(p.id) }) { Text("Duplicar", fontSize = 11.sp) }
                Button(onClick = { onDelete(p.id) }) { Text("Borrar", fontSize = 11.sp) }
                Button(onClick = { onExport(p.id) }) { Text("Exportar", fontSize = 11.sp) }
            }
            Divider()
        }
        item {
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = onImport) { Text("Importar JSON") }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = onResetFactory) { Text("Fábrica") }
            }
            Text("Nuevo desde plantilla:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row {
                Button(onClick = { onNew("factory-dividido") }) { Text("Dividido", fontSize = 11.sp) }
                Button(onClick = { onNew("factory-minimal") }) { Text("Minimal", fontSize = 11.sp) }
            }
            Row {
                Button(onClick = { onNew("factory-nocturno") }) { Text("Nocturno", fontSize = 11.sp) }
                Button(onClick = { onNew("factory-bordes") }) { Text("Bordes", fontSize = 11.sp) }
            }
            Button(onClick = onPickBackground, modifier = Modifier.padding(top = 4.dp)) { Text("Fondo custom (imagen local)") }
            Spacer(Modifier.height(12.dp))
            Text("Aplicación — permisos y captura", color = Color.Cyan, fontSize = 15.sp)
            Text("Explicación antes de solicitar (§10): la app necesita acceso a notificaciones para ver qué suena, micrófono/RECORD para APIs de captura (no graba ambiente), y proyección para captura de reproducción. Todo local, sin internet.", color = Color.Gray, fontSize = 12.sp)
            Text("Notificaciones: ${if (notifAccess) "concedido" else "denegado"}", color = Color.White, fontSize = 13.sp)
            Text("RECORD_AUDIO: ${if (recordGranted) "concedido" else "denegado"}", color = Color.White, fontSize = 13.sp)
            Text("Captura: $captureInfo", color = Color.White, fontSize = 13.sp)
            Row {
                Button(onClick = onRequestNotif) { Text("Notif", fontSize = 11.sp) }
                Button(onClick = onRequestRecord) { Text("Audio", fontSize = 11.sp) }
                Button(onClick = onRequestProjection) { Text("Proyección", fontSize = 11.sp) }
            }
            Button(onClick = onStartVisualizer, modifier = Modifier.padding(top = 4.dp)) { Text("Probar Visualizer (sesión 0)") }
            Spacer(Modifier.height(12.dp))
            Text("Diagnóstico", color = Color.Cyan, fontSize = 15.sp)
            DiagnosticsView(diag)
            Row {
                Button(onClick = onClearLogs) { Text("Limpiar logs") }
                Button(onClick = onExportLogs) { Text("Exportar logs") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Idioma", color = Color.Cyan, fontSize = 15.sp)
            Row {
                Button(onClick = { onLanguage("es") }) { Text("ES") }
                Button(onClick = { onLanguage("en") }) { Text("EN") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun DiagnosticsView(d: DiagSnapshot) {
    val lines = listOf(
        "App ${d.appVersion} · Android SDK ${d.androidSdk} · ${d.device}",
        "Notif: ${d.notifAccess} · RECORD: ${d.recordGranted}",
        "Captura: ${d.captureMethod} · ${d.captureReason}",
        "Sesión: ${d.sessionPkg.ifBlank { "(ninguna)" }} playing=${d.sessionPlaying}",
        "Señal: energy=${String.format("%.4f", d.signalEnergy)} bins=${d.binsActive} vínculo=${d.linkage}",
        "FPS: ${String.format("%.1f", d.fps)}",
        "Nota: mezcla capturada no se atribuye a una pista salvo prueba (§8).",
    )
    Column {
        lines.forEach { Text(it, color = Color.White, fontSize = 12.sp) }
        Text("Eventos (${d.events.size}):", color = Color.Gray, fontSize = 12.sp)
        d.events.takeLast(20).forEach { Text("· $it", color = Color.Gray, fontSize = 11.sp) }
    }
}
