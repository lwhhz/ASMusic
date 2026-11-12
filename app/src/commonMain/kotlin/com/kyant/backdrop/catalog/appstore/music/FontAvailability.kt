package com.kyant.backdrop.catalog.appstore.music

import androidx.compose.runtime.Composable

/**
 * Indices of [SystemFontLabels] that this device actually provides. Android only ships
 * Noto Sans CJK / Noto Serif CJK, and some ROMs drop `casual` / `cursive`, so the
 * unavailable entries are hidden instead of silently falling back.
 */
@Composable
expect fun rememberAvailableFontFamilies(): Set<Int>
