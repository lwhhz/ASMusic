package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.Composable

/** Desktop builds have no font import; the launcher does nothing. */
@Composable
actual fun rememberFontImport(onImported: (path: String) -> Unit): () -> Unit = {}
