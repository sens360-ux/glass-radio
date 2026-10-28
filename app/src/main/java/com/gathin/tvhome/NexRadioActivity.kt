package com.gathin.tvhome

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder
import org.json.JSONArray
import java.io.File
import java.net.URL

/**
 * Glass TV adapter for the original NexTV RadioScreen visual port.
 * NexTV's authenticated Hub is replaced with the public Malaysian Radio Browser directory.
 */
class NexRadioActivity : ComponentActivity() {
    private var engine: ExoPlayer? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        engine = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val prefs = getSharedPreferences("nexradio", MODE_PRIVATE)
        val legacy = getSharedPreferences("RadioActivity", MODE_PRIVATE)

        setContent {
            var stations by remember { mutableStateOf<List<NexTvStation>>(featuredStations()) }
            var directoryStations by remember { mutableStateOf<List<NexTvStation>>(emptyList()) }
            var activeStreamUrls by remember { mutableStateOf<List<String>>(emptyList()) }
            var current by remember { mutableStateOf<NexTvStation?>(null) }
            var playing by remember { mutableStateOf(false) }
            var streamAttempt by remember { mutableIntStateOf(0) }
            var songTitle by remember { mutableStateOf<String?>(null) }
            var songArtist by remember { mutableStateOf<String?>(null) }
            var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", true)) }
            var lyricLine by remember { mutableStateOf<String?>(null) }
            var lyricTimeline by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
            var plainLyrics by remember { mutableStateOf<String?>(null) }
            var message by remember { mutableStateOf<String?>("Loading Malaysian stations…") }
            var favouriteIds by remember {
                mutableStateOf((prefs.getStringSet("favourites", null)
                    ?: legacy.getStringSet("favorites", emptySet()) ?: emptySet()).toSet())
            }
            var recentIds by remember {
                mutableStateOf(
                    (prefs.getString("recent", null) ?: legacy.getString("recent", "") ?: "")
                        .split("\n").filter { it.isNotBlank() },
                )
            }

            fun toggleFavourite(s: NexTvStation) {
                favouriteIds = if (s.id in favouriteIds) favouriteIds - s.id else favouriteIds + s.id
                prefs.edit().putStringSet("favourites", favouriteIds).apply()
            }
            fun updateSong(rawTitle: String, rawArtist: String = "") {
                val title = rawTitle.trim()
                if (title.isBlank() || title.equals(current?.name, ignoreCase = true)) return
                val artist = rawArtist.trim()
                val parts = title.split(" - ", limit = 2)
                val nextArtist = (if (artist.isBlank() && parts.size == 2) parts[0].trim() else artist)
                    .takeIf { it.isNotBlank() }
                val nextTitle = (if (artist.isBlank() && parts.size == 2) parts[1].trim() else title)
                    .takeIf { it.isNotBlank() }
                if (songTitle != nextTitle || songArtist != nextArtist) {
                    songTitle = nextTitle
                    songArtist = nextArtist
                }
            }
            fun startStream(url: String) {
                engine?.apply {
                    stop()
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    play()
                }
            }
            fun play(s: NexTvStation) {
                current = s
                streamAttempt = 0
                songTitle = null
                songArtist = null
                lyricLine = null
                lyricTimeline = emptyList()
                plainLyrics = null
                message = "Connecting…"
                recentIds = (listOf(s.id) + recentIds.filter { it != s.id }).take(12)
                prefs.edit().putString("recent", recentIds.joinToString("\n")).apply()
                activeStreamUrls = streamCandidates(s).distinct()
                startStream(activeStreamUrls.first())
            }
            fun tryNextStream(reason: String) {
                if (streamAttempt + 1 < activeStreamUrls.size) {
                    streamAttempt++
                    message = "Trying alternate stream…"
                    startStream(activeStreamUrls[streamAttempt])
                } else {
                    // A timed-out stream can remain BUFFERING without reporting an error.
                    // Stop the exhausted attempt so it cannot unexpectedly start later.
                    engine?.stop()
                    message = reason
                    playing = false
                }
            }
            fun step(delta: Int) {
                // Channel +/- follows the same favourites-first order shown on screen.
                val ordered = stations.sortedWith(compareByDescending<NexTvStation> { it.id in favouriteIds })
                if (ordered.isEmpty()) return
                val index = ordered.indexOfFirst { it.id == current?.id }
                val target = if (index < 0) 0 else Math.floorMod(index + delta, ordered.size)
                play(ordered[target])
            }
            fun toggle() {
                if (current == null) {
                    stations.firstOrNull { it.id in favouriteIds }?.let(::play)
                        ?: stations.firstOrNull()?.let(::play)
                } else {
                    val player = engine ?: return
                    when {
                        player.playbackState == Player.STATE_IDLE -> {
                            val url = activeStreamUrls.getOrNull(streamAttempt) ?: current?.stream ?: return
                            message = "Connecting…"
                            startStream(url)
                        }
                        player.playWhenReady -> player.pause()
                        else -> player.play()
                    }
                }
            }

            DisposableEffect(Unit) {
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> message = "Buffering…"
                            Player.STATE_READY -> message = null
                        }
                    }
                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        // Some stations send the song via MediaMetadata, others via timed ICY.
                        // Empty metadata must not clear a title already received from ICY.
                        val title = metadata.title?.toString().orEmpty()
                        if (title.isNotBlank()) updateSong(title, metadata.artist?.toString().orEmpty())
                    }
                    @androidx.annotation.OptIn(UnstableApi::class)
                    override fun onMetadata(metadata: Metadata) {
                        for (i in 0 until metadata.length()) {
                            val entry = metadata[i]
                            if (entry is IcyInfo) {
                                entry.title?.let { updateSong(it) }
                            }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        tryNextStream("Station unavailable (" + error.errorCodeName + ")")
                    }
                }
                engine?.addListener(listener)
                onDispose { engine?.removeListener(listener) }
            }

            // A stream can hang in BUFFERING without reporting an error on some TVs.
            LaunchedEffect(current?.id, streamAttempt) {
                if (current != null) {
                    delay(16_000)
                    if (engine?.playWhenReady == true && engine?.playbackState == Player.STATE_BUFFERING) {
                        tryNextStream("Station timed out")
                    }
                }
            }

            LaunchedEffect(Unit) {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { parseDirectory(File(cacheDir, "nexradio-directory.json").readText()) }.getOrDefault(emptyList())
                }
                if (cached.isNotEmpty()) {
                    directoryStations = cached
                    stations = mergeFeatured(cached)
                    message = null
                }
                val fresh = withContext(Dispatchers.IO) {
                    fetchDirectory(File(cacheDir, "nexradio-directory.json"))
                }
                if (fresh.isNotEmpty()) {
                    directoryStations = fresh
                    stations = mergeFeatured(fresh)
                    if (current == null) message = null
                } else if (cached.isEmpty()) {
                    message = "Radio directory unavailable. Check internet connection."
                }
            }

            // LRC timestamps are estimated from metadata arrival; radio streams do not
            // provide an exact song-start position. Hide lyrics when metadata is absent.
            // Radio metadata is not always timed; use synced LRC when available and
            // fall back to the first lines of plain lyrics for automatic display.
            LaunchedEffect(songTitle, songArtist, lyricsEnabled) {
                lyricTimeline = emptyList()
                lyricLine = null
                plainLyrics = null
                val title = songTitle?.trim().orEmpty()
                val artist = songArtist?.trim().orEmpty()
                if (lyricsEnabled && title.isNotBlank()) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            fun fetch(url: String): JSONObject {
                                val conn = URL(url).openConnection().apply {
                                    connectTimeout = 6000
                                    readTimeout = 6000
                                    setRequestProperty("User-Agent", "GlassTV/1.0")
                                }
                                return JSONObject(conn.getInputStream().bufferedReader().use { it.readText() })
                            }
                            val query = "track_name=" + URLEncoder.encode(title, "UTF-8") +
                                "&artist_name=" + URLEncoder.encode(artist, "UTF-8")
                            val exact = runCatching { fetch("https://lrclib.net/api/get?" + query) }.getOrNull()
                            val match = exact ?: run {
                                val conn = URL("https://lrclib.net/api/search?" + query).openConnection().apply {
                                    connectTimeout = 6000; readTimeout = 6000
                                    setRequestProperty("User-Agent", "GlassTV/1.0")
                                }
                                JSONArray(conn.getInputStream().bufferedReader().use { it.readText() }).optJSONObject(0)
                            }
                            val synced = match?.optString("syncedLyrics").orEmpty()
                            val plain = match?.optString("plainLyrics").orEmpty()
                            NexTvLyrics.parseSyncedLrc(synced) to plain
                        }.getOrDefault(emptyList<Pair<Long, String>>() to "")
                    }
                    lyricTimeline = result.first
                    plainLyrics = result.second.takeIf { it.isNotBlank() }
                }
            }
            // Earlier Glass TV builds stored station names, not stable Radio Browser UUIDs.
            LaunchedEffect(stations) {
                if (stations.isNotEmpty()) {
                    val migratedFavourites = favouriteIds.map { key ->
                        stations.firstOrNull { it.id == key || it.name == key }?.id
                            ?: directoryStations.firstOrNull { it.id == key || it.name == key }?.let { oldStation ->
                                stations.firstOrNull { stationKey(it.name) == stationKey(oldStation.name) }?.id
                            } ?: key
                    }.toSet()
                    val migratedRecent = recentIds.map { key ->
                        stations.firstOrNull { it.id == key || it.name == key }?.id
                            ?: directoryStations.firstOrNull { it.id == key || it.name == key }?.let { oldStation ->
                                stations.firstOrNull { stationKey(it.name) == stationKey(oldStation.name) }?.id
                            } ?: key
                    }.distinct()
                    if (migratedFavourites != favouriteIds) {
                        favouriteIds = migratedFavourites
                        prefs.edit().putStringSet("favourites", favouriteIds).apply()
                    }
                    if (migratedRecent != recentIds) {
                        recentIds = migratedRecent
                        prefs.edit().putString("recent", recentIds.joinToString("\n")).apply()
                    }
                    // Remember the last station selection without autoplaying on launch.
                    // OK resumes playback, with favourites and D-pad order unchanged.
                    if (current == null) {
                        recentIds.firstNotNullOfOrNull { id -> stations.firstOrNull { it.id == id } }?.let { last ->
                            current = last
                            activeStreamUrls = streamCandidates(last).distinct()
                            streamAttempt = 0
                        }
                    }
                }
            }

            NexTvRadioScreen(
                stations = stations,
                current = current,
                playing = playing,
                songTitle = songTitle,
                songArtist = songArtist,
                message = message,
                lyricLine = lyricLine,
                fullLyrics = plainLyrics ?: lyricTimeline.joinToString("\n") { it.second },
                lyricsEnabled = lyricsEnabled,
                onToggleLyrics = { lyricsEnabled = !lyricsEnabled; prefs.edit().putBoolean("lyrics_enabled", lyricsEnabled).apply() },
                favourites = favouriteIds,
                recent = recentIds,
                sourceLabel = "Public Radio",
                onPlay = ::play,
                onToggle = ::toggle,
                onNext = { step(1) },
                onPrevious = { step(-1) },
                onFavourite = ::toggleFavourite,
                onHome = { finish() },
            )
        }
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }

    private fun fetchDirectory(cacheFile: File): List<NexTvStation> {
        val hosts = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "nl1.api.radio-browser.info")
        for (host in hosts) {
            try {
                val url = URL("https://$host/json/stations/bycountrycodeexact/MY?hidebroken=true&order=votes&reverse=true&limit=300")
                val connection = url.openConnection().apply { connectTimeout = 8_000; readTimeout = 12_000 }
                val json = connection.getInputStream().bufferedReader().use { it.readText() }
                val combined = JSONArray(json)
                // Fetch ERA by name too: the country top-300 list can omit it.
                for (term in listOf("ERA")) {
                    try {
                        val searchUrl = URL("https://$host/json/stations/byname/${java.net.URLEncoder.encode(term, "UTF-8")}?hidebroken=true&limit=50")
                        val searchConnection = searchUrl.openConnection().apply { connectTimeout = 5_000; readTimeout = 7_000 }
                        val matches = JSONArray(searchConnection.getInputStream().bufferedReader().use { it.readText() })
                        for (i in 0 until matches.length()) {
                            val station = matches.getJSONObject(i)
                            if (station.optString("countrycode").equals("MY", true)) combined.put(station)
                        }
                    } catch (_: Exception) { /* Keep the main directory if a name lookup fails. */ }
                }
                val list = parseDirectory(combined.toString())
                if (list.isNotEmpty()) {
                    runCatching { cacheFile.writeText(combined.toString()) }
                    return list
                }
            } catch (_: Exception) {
                // Try another public mirror; the previous cache remains usable.
            }
        }
        return emptyList()
    }

    private fun parseDirectory(json: String): List<NexTvStation> {
        val rows = JSONArray(json)
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val name = row.optString("name").trim()
            // Exclude Chinese-language stations from the Malaysian radio directory.
            // Radio Browser language metadata is not always populated, so check common
            // Chinese-language station names as a fallback.
            val languages = row.optString("language").lowercase(java.util.Locale.ROOT)
            val languageCodes = row.optString("languagecodes").lowercase(java.util.Locale.ROOT)
            val chineseLanguage = languages.split(',', ';').any {
                val value = it.trim()
                value == "chinese" || value == "mandarin" || value == "cantonese" ||
                    value == "hakka" || value == "hokkien" || value == "min nan" ||
                    value == "yue" || value == "zh"
            } || languageCodes.split(',', ';').any {
                val code = it.trim()
                code == "zh" || code.startsWith("zh-") || code == "yue" || code == "nan" || code == "hak"
            }
            val chineseStationName = listOf("988", "MY FM", "MYFM", "ONE FM", "ONEFM",
                "EIGHT FM", "8FM", "MELODY FM", "MELODY", "GOXUAN", "CITYPLUS",
                "AI FM", "AIFM", "TEA FM", "TEAFM").any { keyword ->
                name.equals(keyword, true) || name.startsWith("$keyword ", true) ||
                    name.startsWith("$keyword-", true)
            }
            if (chineseLanguage || chineseStationName || isIkim(name)) return@mapNotNull null
            val stream = row.optString("url_resolved").ifBlank { row.optString("url") }.trim()
            if (name.isBlank() || !stream.startsWith("http", ignoreCase = true)) return@mapNotNull null
            val group = when {
                name.contains("RTM", true) || name.contains("Nasional", true) -> "RTM"
                name.contains("Astro", true) || name.contains("Hitz", true) || name.contains("Mix FM", true) -> "Astro"
                name.contains("Hot FM", true) || name.contains("Fly FM", true) || name.contains("8FM", true) -> "Media Prima"
                else -> "Others"
            }
            NexTvStation(
                id = row.optString("stationuuid").ifBlank { stream },
                name = name,
                stream = stream,
                tagline = row.optString("tags").split(",").firstOrNull()?.trim().orEmpty().take(40),
                logo = row.optString("favicon").takeIf { it.startsWith("http", true) },
                group = group,
                bitrate = row.optInt("bitrate").takeIf { it > 0 },
            )
        }.distinctBy { it.id }
    }

    private fun featuredStations() = listOf(
        NexTvStation("featured-era", "ERA FM",
            "https://audio1.syok.my/era",
            tagline = "Muzik Hit Terkini", group = "Astro"),
    )

    /** Fixed ERA endpoints; other stations use their directory stream. */
    private fun streamCandidates(station: NexTvStation): List<String> = when (station.id) {
        "featured-era" -> listOf(
            "https://audio1.syok.my/era",
            "https://stream.rcs.revma.com/crec9cmbv4uvv",
        )
        else -> listOf(station.stream)
    }

    private fun isIkim(name: String): Boolean = stationKey(name) == "IKIM" ||
        name.contains(Regex("(?i)\\bIKIM\\s*FM\\b"))

    private fun stationKey(name: String): String {
        val cleaned = name.uppercase(java.util.Locale.ROOT)
            .replace(Regex("\\b(FM|RADIO|MALAYSIA)\\b"), " ")
            .replace(Regex("[^A-Z0-9]+"), " ").trim()
        return when {
            cleaned == "ERA" || cleaned == "ERA JE" || cleaned == "ERA KUALA LUMPUR" || cleaned == "ERA 103 3" -> "ERA"
            cleaned == "IKIM" || cleaned == "IKIM KUALA LUMPUR" || cleaned == "IKIMFM" -> "IKIM"
            cleaned == "HOT" -> "HOT"
            Regex("(?:^|\\s)(?:THR\\s+)?GEGAR(?:\\s|$)").containsMatchIn(cleaned) -> "GEGAR"
            else -> cleaned
        }
    }

    private fun mergeFeatured(directory: List<NexTvStation>): List<NexTvStation> {
        val featured = featuredStations()
        val popular = listOf("HOT FM", "SINAR", "SURIA", "HITZ", "GEGAR", "RADIO KLASIK", "ZAYAN", "MIX", "FLY FM", "LITE", "BEST FM", "KOOL 101", "NASIONAL FM", "CATS FM", "RAAGA")
        val featuredKeys = featured.map { stationKey(it.name) }.toSet()
        val seen = featuredKeys.toMutableSet()
        val sorted = directory.filterNot { stationKey(it.name) in featuredKeys }
            .sortedWith(compareBy<NexTvStation> { station ->
                val name = station.name.uppercase(java.util.Locale.ROOT)
                popular.indexOfFirst { name == it || name.startsWith("$it ") || name.startsWith("$it FM") }
                    .let { if (it < 0) Int.MAX_VALUE else it }
            }.thenBy { it.name })
        return featured + sorted.filter { station -> !isIkim(station.name) && seen.add(stationKey(station.name)) }
    }
}
