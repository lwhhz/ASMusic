package com.kyant.backdrop.catalog.appstore

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

enum class IconKey {
    TikTok, YouTube, Instagram, SlapKings, CrossyRoad, SortIt, SevenSins,
    Altos, CutPaint, OverTake, Mowing, SlapThat, Arcade
}

enum class ArtKey { Joystick, VoxelRoad, ColorMist, Ring, Phone, Avatar }

enum class Tone { Light, Dark }

data class Review(
    val author: String,
    val date: String,
    val handle: String,
    val rating: Int,
    val body: String
)

data class Shot(val headline: String, val art: ArtKey)

data class WhatsNew(val version: String, val date: String, val notes: String)

data class AppItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val developer: String,
    val category: String,           // "game" | "app"
    val tags: List<String>,
    val icon: IconKey,
    val accent: Pair<Color, Color>,
    val rating: Double,
    val ratingCount: Int,
    val age: String,
    val chart: Int?,
    val iap: Boolean,
    val description: String,
    val whatsNew: WhatsNew,
    val reviews: List<Review>,
    val shots: List<Shot>
)

data class Story(
    val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String?,
    val art: ArtKey,
    val appId: String,
    val tone: Tone
)

private val john = Review(
    "John", "2d ago", "johndoe", 5,
    "Lorem ipsum dolor sit amet consectetur adipisicing elit. Ratione laborum debitis natus " +
        "cum quae est rerum cupiditate cumque delectus eaque ipsa, accusamus facilis deleniti " +
        "consequuntur, aliquam soluta minima, eos exercitationem."
)

private val genericReviews = listOf(
    john,
    Review("Emily", "5d ago", "emily.k", 5, "Exactly what I was looking for. Simple, fast and genuinely fun to use every day."),
    Review("Marco", "1w ago", "marco_88", 4, "Great update. A few rough edges remain but the experience keeps getting better.")
)

private fun defaultWhatsNew(version: String) = WhatsNew(
    version, "Mar 4, 2020",
    "As always, we've made some performance improvements to make your in-app experience even better."
)

private fun defaultShots(headline: String) = listOf(
    Shot(headline, ArtKey.Phone),
    Shot("Made for every moment", ArtKey.ColorMist),
    Shot("Discover something new", ArtKey.Ring)
)

private fun app(
    id: String, name: String, subtitle: String, developer: String,
    category: String, tags: List<String>, icon: IconKey,
    accent: Pair<Color, Color>, rating: Double, ratingCount: Int,
    age: String, chart: Int?, description: String,
    version: String = "15.1.0", shotsHeadline: String = "Videos for every mood."
) = AppItem(
    id, name, subtitle, developer, category, tags, icon, accent,
    rating, ratingCount, age, chart, true, description,
    defaultWhatsNew(version), genericReviews, defaultShots(shotsHeadline)
)

object Catalog {

    val all: List<AppItem> = listOf(
        app(
            "tiktok", "TikTok - Make Your Day", "Real People. Real Videos.", "TikTok Inc.",
            "app", listOf("Photo & Video", "Entertainment", "Social Networking"), IconKey.TikTok,
            Color(0xFF25F4EE) to Color(0xFFFE2C55), 4.8, 930, "12+", 9,
            "TikTok is THE destination for mobile videos. On TikTok, short-form videos are " +
                "exciting, spontaneous, and genuine. Whether you're a sports fanatic, a pet " +
                "enthusiast, or just looking for a laugh, there's something for everyone."
        ),
        app(
            "youtube", "YouTube: Watch, Listen, Stream", "Watch and discover videos", "Google LLC",
            "app", listOf("Photo & Video", "Entertainment"), IconKey.YouTube,
            Color(0xFFFF4B4B) to Color(0xFFCC0000), 4.6, 12400, "12+", 3,
            "Enjoy the videos and music you love, upload original content, and share it all " +
                "with friends, family, and the world on YouTube.",
            version = "19.09.3", shotsHeadline = "Everything you love, in one place"
        ),
        app(
            "instagram", "Instagram", "Capture and share the moments", "Meta Platforms, Inc.",
            "app", listOf("Photo & Video", "Social Networking"), IconKey.Instagram,
            Color(0xFFFEDA75) to Color(0xFFC13584), 4.7, 8800, "12+", 5,
            "Bringing you closer to the people and things you love. Connect with friends, " +
                "share what you're up to, or see what's new from others all over the world.",
            version = "302.0", shotsHeadline = "Share the moment"
        ),
        app(
            "slap-kings", "Slap Kings", "Become world's best slapper", "Lion Studios",
            "game", listOf("Indie", "Puzzle", "Action"), IconKey.SlapKings,
            Color(0xFF8BD450) to Color(0xFF2C7A2C), 4.5, 1200, "9+", 1,
            "Slap the whole world one face at a time! Travel the globe, challenge everyone " +
                "you meet and prove you are the undisputed slap champion.",
            version = "2.4.1", shotsHeadline = "Slap your way to the top"
        ),
        app(
            "crossy-road", "Crossy Road", "Endless Arcade Hopper", "Hipster Whale",
            "game", listOf("Arcade", "Adventure", "Puzzle"), IconKey.CrossyRoad,
            Color(0xFF63C6E8) to Color(0xFFE8544F), 4.8, 2600, "4+", 2,
            "Hop your way through endless roads, cross rivers full of logs and dodge " +
                "oncoming traffic in this endless arcade hopper.",
            version = "5.6.0", shotsHeadline = "Hop your way through endless roads"
        ),
        app(
            "sort-it-3d", "Sort It 3D", "Organise everything", "Rollic Games",
            "game", listOf("Puzzle", "Indie"), IconKey.SortIt,
            Color(0xFF4FC3F7) to Color(0xFF1565C0), 4.4, 760, "4+", null,
            "Sort, stack and match colourful objects in a satisfying 3D puzzle world.",
            version = "1.9.2", shotsHeadline = "Everything in its place"
        ),
        app(
            "seven-sins", "The Seven Deadly Sins", "Grand Cross", "Netmarble",
            "game", listOf("Strategy", "RPG"), IconKey.SevenSins,
            Color(0xFFFFC46B) to Color(0xFF7B1FA2), 4.5, 3400, "12+", null,
            "Assemble a team of legendary heroes and save the kingdom of Britannia in this " +
                "cinematic turn-based RPG.",
            version = "3.12.0", shotsHeadline = "A legend reborn"
        ),
        app(
            "altos", "Alto's Odyssey", "Endless sandboarding", "Snowman",
            "game", listOf("Adventure", "Arcade", "Indie"), IconKey.Altos,
            Color(0xFFFFB56B) to Color(0xFF6442A6), 4.9, 1900, "4+", 3,
            "Soar above windswept dunes, traverse twisting canyons and explore a majestic " +
                "desert in this endless sandboarding odyssey.",
            version = "1.9.4", shotsHeadline = "A journey beyond the dunes"
        ),
        app(
            "cut-and-paint", "Cut and Paint", "Slice, paint, repeat", "VOODOO",
            "game", listOf("Puzzle", "Indie"), IconKey.CutPaint,
            Color(0xFF7ED8F6) to Color(0xFFE9C46A), 4.3, 540, "4+", null,
            "Cut the shapes, dip them in paint and build the picture. Relaxing and oddly satisfying.",
            version = "1.4.0", shotsHeadline = "Slice, paint, repeat"
        ),
        app(
            "overtake", "OverTake", "Avoid head-on collisions!", "SayGames",
            "game", listOf("Racing", "Arcade"), IconKey.OverTake,
            Color(0xFFFFE14D) to Color(0xFF1565C0), 4.2, 410, "4+", null,
            "Weave through traffic at breakneck speed. One wrong move and it's game over.",
            version = "2.1.3", shotsHeadline = "Avoid head-on collisions!"
        ),
        app(
            "mowing", "It's Literally Just Mowing", "Calm down and cut nice...", "Protostar",
            "game", listOf("Simulation", "Indie"), IconKey.Mowing,
            Color(0xFF9CCC65) to Color(0xFF2E7D32), 4.6, 320, "4+", null,
            "It's literally just mowing. No timers, no scores. Trim the lawn at your own pace.",
            version = "1.2.0", shotsHeadline = "Calm down and cut nice grass"
        ),
        app(
            "slap-that", "Slap That - Winner Slaps All", "That slap game", "Kwalee",
            "game", listOf("Action", "Arcade"), IconKey.SlapThat,
            Color(0xFFFF5252) to Color(0xFF212121), 4.1, 280, "9+", null,
            "Line up your slap, time it perfectly and send your opponent flying.",
            version = "1.6.1", shotsHeadline = "That slap game"
        ),
        app(
            "arcade", "Apple Arcade", "Play the best games", "Apple Inc.",
            "game", listOf("Arcade"), IconKey.Arcade,
            Color(0xFF0A84FF) to Color(0xFF5E5CE6), 4.9, 5200, "4+", null,
            "Unlimited access to over 200 premium games, with no ads and no in-app purchases.",
            version = "2.0", shotsHeadline = "Play without limits"
        )
    )

    fun byId(id: String?): AppItem? = if (id == null) null else all.firstOrNull { it.id == id }

    /** Case-insensitive, multi-field match. Empty query -> empty list (Suggested is shown instead). */
    fun filter(query: String): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter {
            it.name.contains(q, true) || it.subtitle.contains(q, true) ||
                it.developer.contains(q, true) || it.category.contains(q, true) ||
                it.tags.any { tag -> tag.contains(q, true) }
        }
    }

    fun formatCount(n: Int): String = when {
        n >= 1000 -> "${(n / 100.0).roundToInt() / 10.0}K"
        else -> n.toString()
    }

    // ---- Today ----
    val todayStories = listOf(
        Story(
            "today-arcade", "APPLE ARCADE", "5 awesome Apple Arcade puzzle games", null,
            ArtKey.Joystick, "crossy-road", Tone.Light
        ),
        Story(
            "today-crossy", "", "Hop your way through endless roads", "CROSSY ROAD",
            ArtKey.VoxelRoad, "crossy-road", Tone.Dark
        )
    )

    // ---- Games ----
    val gamesChips = listOf("\uD83D\uDC8E Indie", "\uD83E\uDDE9 Puzzle", "\uD83C\uDFF0 Strategy", "\uD83C\uDFAE Arcade")
    val gamesChipsTags = listOf("Indie", "Puzzle", "Strategy", "Arcade")
    val gamesReleases = listOf(
        Story("rel-slap", "NEW RELEASE", "Slap the whole world one face at a time", "Become world's best slapper", ArtKey.Ring, "slap-kings", Tone.Dark),
        Story("rel-crossy", "NEW RELEASE", "Hop your way through endless roads", "Endless Arcade Hopper", ArtKey.VoxelRoad, "crossy-road", Tone.Dark),
        Story("rel-sort", "NEW RELEASE", "Everything in its place", "Sort It 3D", ArtKey.ColorMist, "sort-it-3d", Tone.Dark)
    )
    val gamesPlaying = listOf("slap-kings", "sort-it-3d", "seven-sins")

    // ---- Apps ----
    val appsChips = listOf("\uD83D\uDCF7 Photo & Video", "\uD83C\uDF7F Entertainment", "\uD83D\uDCAC Social Networking")
    val appsChipsTags = listOf("Photo & Video", "Entertainment", "Social Networking")
    val appsUpdates = listOf(
        Story("upd-tiktok", "NEW UPDATE", "TikTok - Make Your Day", "Real People. Real Videos.", ArtKey.ColorMist, "tiktok", Tone.Dark),
        Story("upd-youtube", "NEW UPDATE", "YouTube: Watch, Listen, Stream", "Everything you love", ArtKey.Phone, "youtube", Tone.Dark),
        Story("upd-instagram", "NEW UPDATE", "Instagram", "Share the moment", ArtKey.Ring, "instagram", Tone.Dark)
    )
    val appsUsingNow = listOf("tiktok", "youtube", "instagram")

    // ---- Arcade ----
    val arcadeSlides = listOf(
        Story("arc-1", "SHOWCASE", "Hop Your Way Through Endless Roads", "Crossy Road / Endless Arcade Hopper", ArtKey.VoxelRoad, "crossy-road", Tone.Dark),
        Story("arc-2", "SHOWCASE", "Slap Your Way To The Top", "Slap Kings / Become world's best slapper", ArtKey.Ring, "slap-kings", Tone.Dark),
        Story("arc-3", "SHOWCASE", "A Journey Beyond The Dunes", "Alto's Odyssey / Endless sandboarding", ArtKey.ColorMist, "altos", Tone.Dark)
    )
    val arcadeChips = listOf("▦ All Games", "\uD83D\uDDFA Adventure", "\uD83C\uDFC1 Racing", "\uD83E\uDDE9 Puzzle")
    val arcadeTop = listOf("slap-kings", "crossy-road", "altos")

    // ---- Search ----
    val searchSuggested = listOf("cut-and-paint", "overtake", "mowing", "slap-that")
    val browseCategories = listOf("Top Downloaded Games", "Top Downloaded Apps")

    fun appsForTag(tag: String?, ids: List<String>): List<AppItem> {
        val items = ids.mapNotNull(::byId)
        if (tag == null) return items
        val matched = items.filter { it.tags.any { t -> t.equals(tag, true) } }
        return matched.ifEmpty { items }
    }
}
