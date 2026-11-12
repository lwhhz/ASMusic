package com.kyant.backdrop.catalog

/** Bridges the activity-owned player to the MediaSession service. */
internal object PlaybackHolder {
    @Volatile var player: MusicPlayer? = null
}
