package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.Composable

/**
 * Picks a font file (ttf / otf) from the system file picker, copies it into the app's
 * private storage and reports the absolute path. Returns the launcher to call.
 */
@Composable
expect fun rememberFontImport(onImported: (path: String) -> Unit): () -> Unit
