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
            var current by remember { mutableStateOf<NexTvStation?>(null) }
            var playing by remember { mutableStateOf(false) }
            var songTitle by remember { mutableStateOf<String?>(null) }
            var songArtist by remember { mutableStateOf<String?>(null) }
            var lyricsEnabled by remember { mutableStateOf(prefs.getBoolean("lyrics_enabled", true)) }
            var lyricLine by remember { mutableStateOf<String?>(null) }
            var lyricTimeline by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
            var lyricsStartedAt by remember { mutableLongStateOf(0L) }
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
            fun play(s: NexTvStation) {
                current = s
                songTitle = null
                songArtist = null
                lyricLine = null
                lyricTimeline = emptyList()
                message = "Connecting…"
                recentIds = (listOf(s.id) + recentIds.filter { it != s.id }).take(12)
                prefs.edit().putString("recent", recentIds.joinToString("\n")).apply()
                engine?.apply {
                    stop()
                    setMediaItem(MediaItem.fromUri(s.stream))
                    prepare()
                    play()
                }
            }
            fun step(delta: Int) {
                if (stations.isEmpty()) return
                val index = stations.indexOfFirst { it.id == current?.id }
                val target = if (index < 0) 0 else Math.floorMod(index + delta, stations.size)
                play(stations[target])
            }
            fun toggle() {
                if (current == null) stations.firstOrNull()?.let(::play)
                else if (playing) engine?.pause() else engine?.play()
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
                        songTitle = metadata.title?.toString()?.takeIf { it.isNotBlank() }
                        songArtist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
                        lyricsStartedAt = android.os.SystemClock.elapsedRealtime()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        message = "Station unavailable"
                        playing = false
                    }
                }
                engine?.addListener(listener)
                onDispose { engine?.removeListener(listener) }
            }

            LaunchedEffect(Unit) {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { parseDirectory(File(cacheDir, "nexradio-directory.json").readText()) }.getOrDefault(emptyList())
                }
                if (cached.isNotEmpty()) {
                    stations = mergeFeatured(cached)
                    message = null
                }
                val fresh = withContext(Dispatchers.IO) {
                    fetchDirectory(File(cacheDir, "nexradio-directory.json"))
                }
                if (fresh.isNotEmpty()) {
                    stations = mergeFeatured(fresh)
                    if (current == null) message = null
                } else if (cached.isEmpty()) {
                    message = "Radio directory unavailable. Check internet connection."
                }
            }

            // LRC timestamps are estimated from metadata arrival; radio streams do not
            // provide an exact song-start position. Hide lyrics when metadata is absent.
            LaunchedEffect(songTitle, songArtist, lyricsEnabled) {
                lyricTimeline = emptyList()
                lyricLine = null
                val title = songTitle?.trim().orEmpty()
                val artist = songArtist?.trim().orEmpty()
                if (lyricsEnabled && title.isNotBlank() && artist.isNotBlank()) {
                    lyricTimeline = withContext(Dispatchers.IO) {
                        runCatching {
                            val url = URL("https://lrclib.net/api/get?track_name=" +
                                URLEncoder.encode(title, "UTF-8") + "&artist_name=" +
                                URLEncoder.encode(artist, "UTF-8"))
                            val conn = url.openConnection().apply {
                                connectTimeout = 5000
                                readTimeout = 5000
                                setRequestProperty("User-Agent", "GlassTV/1.0")
                            }
                            val raw = JSONObject(conn.getInputStream().bufferedReader().use { it.readText() })
                                .optString("syncedLyrics")
                            NexTvLyrics.parseSyncedLrc(raw)
                        }.getOrDefault(emptyList())
                    }
                }
            }
            LaunchedEffect(lyricTimeline, lyricsStartedAt, playing, lyricsEnabled) {
                while (playing && lyricsEnabled && lyricTimeline.isNotEmpty()) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - lyricsStartedAt
                    lyricLine = lyricTimeline.lastOrNull { it.first <= elapsed }?.second
                    delay(250)
                }
            }

            // Earlier Glass TV builds stored station names, not stable Radio Browser UUIDs.
            LaunchedEffect(stations) {
                if (stations.isNotEmpty()) {
                    val migratedFavourites = favouriteIds.map { key ->
                        stations.firstOrNull { it.id == key || it.name == key }?.id ?: key
                    }.toSet()
                    val migratedRecent = recentIds.map { key ->
                        stations.firstOrNull { it.id == key || it.name == key }?.id ?: key
                    }.distinct()
                    if (migratedFavourites != favouriteIds) {
                        favouriteIds = migratedFavourites
                        prefs.edit().putStringSet("favourites", favouriteIds).apply()
                    }
                    if (migratedRecent != recentIds) {
                        recentIds = migratedRecent
                        prefs.edit().putString("recent", recentIds.joinToString("\n")).apply()
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
                lyricsEnabled = lyricsEnabled,
                onToggleLyrics = { lyricsEnabled = !lyricsEnabled; prefs.edit().putBoolean("lyrics_enabled", lyricsEnabled).apply() },
                favourites = favouriteIds,
                recent = recentIds,
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
                // Fetch popular stations by name too: the country top-300 list can omit ERA/IKIM.
                for (term in listOf("ERA", "IKIM")) {
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
        return mergeFeatured((0 until rows.length()).mapNotNull { index ->
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
            if (chineseLanguage || chineseStationName) return@mapNotNull null
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
        }.distinctBy { it.id })
    }

    private fun featuredStations() = listOf(
        NexTvStation("featured-era", "ERA FM",
            "https://stream.rcs.revma.com/crec9cmbv4uvv/12_7yx6mmm02au702/playlist.m3u8",
            tagline = "Muzik Hit Terkini", group = "Astro"),
        NexTvStation("featured-ikim", "IKIM FM",
            "https://ais-sa8.cdnstream1.com/5035",
            tagline = "Inspirasi Utama Anda", group = "Others"),
    )

    private fun stationKey(name: String): String {
        val cleaned = name.uppercase(java.util.Locale.ROOT)
            .replace(Regex("\\b(FM|RADIO|MALAYSIA)\\b"), " ")
            .replace(Regex("[^A-Z0-9]+"), " ").trim()
        return when (cleaned) {
            "ERA", "ERA JE" -> "ERA"
            "IKIM" -> "IKIM"
            "HOT" -> "HOT"
            "THR GEGAR", "GEGAR" -> "GEGAR"
            else -> cleaned
        }
    }

    private fun mergeFeatured(directory: List<NexTvStation>): List<NexTvStation> {
        val featured = featuredStations()
        val popular = listOf("HOT FM", "SINAR", "SURIA", "HITZ", "GEGAR", "RADIO KLASIK", "ZAYAN", "MIX", "FLY FM", "LITE", "BEST FM", "KOOL 101", "NASIONAL FM", "CATS FM", "RAAGA")
        val featuredKeys = featured.map { stationKey(it.name) }.toSet()
        val seen = mutableSetOf<String>()
        val sorted = directory.filterNot { stationKey(it.name) in featuredKeys }
            .sortedWith(compareBy<NexTvStation> { station ->
                val name = station.name.uppercase(java.util.Locale.ROOT)
                popular.indexOfFirst { name == it || name.startsWith("$it ") || name.startsWith("$it FM") }
                    .let { if (it < 0) Int.MAX_VALUE else it }
            }.thenBy { it.name })
        return featured + sorted.filter { station -> seen.add(stationKey(station.name)) }
    }
}
