package com.kyant.backdrop.catalog.appstore.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberFontImport(onImported: (path: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            // Keep a single imported font: drop the previous one(s) first.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "imported_${System.currentTimeMillis()}.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0L) onImported(target.absolutePath)
        }
    }
    return {
        // Some font files report as octet-stream, so accept everything.
        launcher.launch(arrayOf("*/*"))
    }
}
