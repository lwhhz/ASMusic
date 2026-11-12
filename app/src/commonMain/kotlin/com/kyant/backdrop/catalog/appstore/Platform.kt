package com.kyant.backdrop.catalog.appstore

/**
 * Platform services are injected from the Android application module at startup.
 * This keeps commonMain free of Android APIs and avoids expect/actual plumbing.
 */
interface PlatformBridge {
    fun share(text: String)
    fun loadInstalled(): Set<String>
    fun saveInstalled(ids: Set<String>)
}

object Platform {
    var bridge: PlatformBridge = object : PlatformBridge {
        override fun share(text: String) {}
        override fun loadInstalled(): Set<String> = emptySet()
        override fun saveInstalled(ids: Set<String>) {}
    }
}
