package com.gathin.tvhome

// NexTV Radio screen visual port for Glass TV. Based on NexTV RadioScreen.kt,
// RadioUi.kt and RadioFocus.kt (MIT, copyright 2026 Nexdata).
// Non-radio cards and NexTV Hub authentication have intentionally been removed.

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class NexTvStation(
    val id: String,
    val name: String,
    val stream: String,
    val tagline: String = "",
    val logo: String? = null,
    val group: String = "Others",
    val bitrate: Int? = null,
)

private val Bg = Color(0xFF050B1A)
private val GlassFill = Color(0x8C0B1A33)
private val GlassRim = Color(0x334FA3FF)
private val Cyan = Color(0xFF22D3EE)
private val CyanSoft = Color(0xFF8AEBFF)
private val Dim = Color(0xB3DCE2F6)
private val White = Color(0xFFEEF2F7)
private val KL = ZoneId.of("Asia/Kuala_Lumpur")
private val TimeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DateFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

private fun stationColor(group: String): Color = when (group) {
    "RTM" -> Color(0xFFE5C271)
    "Astro" -> Cyan
    "Media Prima" -> Color(0xFFF87171)
    else -> Color(0xFF66F796)
}

@Composable
internal fun NexTvRadioScreen(
    stations: List<NexTvStation>,
    current: NexTvStation?,
    playing: Boolean,
    songTitle: String?,
    songArtist: String?,
    message: String?,
    lyricLine: String?,
    lyricsEnabled: Boolean,
    onToggleLyrics: () -> Unit,
    favourites: Set<String>,
    recent: List<String>,
    sourceLabel: String,
    onPlay: (NexTvStation) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onFavourite: (NexTvStation) -> Unit,
    onHome: () -> Unit,
) {
    val menuReq = remember { FocusRequester() }
    val playReq = remember { FocusRequester() }
    val lyricsReq = remember { FocusRequester() }
    val favReq = remember { FocusRequester() }
    val recentReq = remember { FocusRequester() }
    val rowReqs = remember { mutableMapOf<String, FocusRequester>() }
    fun rowReq(id: String): FocusRequester = rowReqs.getOrPut(id) { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusedStation by remember { mutableStateOf<String?>(null) }
    var focusedControl by remember { mutableIntStateOf(1) }
    val favStations = remember(stations, favourites) { stations.filter { it.id in favourites } }
    val recentStations = remember(stations, recent) { recent.mapNotNull { id -> stations.firstOrNull { it.id == id } }.take(3) }
    var clock by remember { mutableStateOf(ZonedDateTime.now(KL)) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); clock = ZonedDateTime.now(KL) } }
    var initialStationFocus by remember { mutableStateOf(false) }
    LaunchedEffect(stations.isNotEmpty()) {
        withFrameNanos { }
        if (stations.isNotEmpty() && !initialStationFocus) {
            runCatching { rowReq(stations.first().id).requestFocus() }.onSuccess { initialStationFocus = true }
        } else if (stations.isEmpty() && !initialStationFocus) {
            runCatching { menuReq.requestFocus() }
        }
    }
    fun go(zone: NexTvRadioFocus.Zone): Boolean = runCatching {
        when (zone) {
            NexTvRadioFocus.Zone.Menu -> menuReq.requestFocus()
            NexTvRadioFocus.Zone.Controls -> playReq.requestFocus()
            NexTvRadioFocus.Zone.Favourites -> favReq.requestFocus()
            NexTvRadioFocus.Zone.Recent -> recentReq.requestFocus()
            NexTvRadioFocus.Zone.Stations -> {
                val id = (current?.id ?: focusedStation)?.takeIf { key -> stations.any { it.id == key } } ?: stations.first().id
                val index = stations.indexOfFirst { it.id == id }
                if (listState.layoutInfo.visibleItemsInfo.any { it.index == index }) rowReq(id).requestFocus()
                else scope.launch {
                    listState.scrollToItem(index)
                    withFrameNanos { }
                    runCatching { rowReq(id).requestFocus() }
                }
            }
        }
    }.isSuccess
    fun Modifier.zone(zone: NexTvRadioFocus.Zone): Modifier = onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val dir = when (e.key) {
            Key.DirectionUp -> NexTvRadioFocus.Dir.Up
            Key.DirectionDown -> NexTvRadioFocus.Dir.Down
            Key.DirectionLeft -> NexTvRadioFocus.Dir.Left
            Key.DirectionRight -> NexTvRadioFocus.Dir.Right
            else -> return@onPreviewKeyEvent false
        }
        val to = NexTvRadioFocus.next(
            zone, dir, stations.isNotEmpty(), favStations.isNotEmpty(),
            NexTvRadioFocus.onFirstStation(stations.map { it.id }, focusedStation), focusedControl,
            hasRecent = recentStations.isNotEmpty(),
        ) ?: return@onPreviewKeyEvent false
        to == zone || go(to)
    }

    Box(
        Modifier.fillMaxSize().background(Bg).onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.MediaNext, Key.ChannelUp -> { onNext(); true }
                Key.MediaPrevious, Key.ChannelDown -> { onPrevious(); true }
                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { onToggle(); true }
                else -> false
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050B1A), Color(0xFF0C1B3B), Color(0xFF050B1A)))))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Cyan, Color(0xFF1E66D0)))), contentAlignment = Alignment.Center) {
                    Label("N", 21.sp, FontWeight.ExtraBold, Bg)
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Row { Label("NexTV", 20.sp, FontWeight.ExtraBold); Label(" Radio", 20.sp, FontWeight.SemiBold, Cyan) }
                    Label(sourceLabel, 8.sp, FontWeight.Medium, Dim)
                }
                Row(Modifier.weight(1f).zone(NexTvRadioFocus.Zone.Menu), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    MenuPill("Home", false, Modifier.focusRequester(menuReq), onHome)
                    MenuPill("Radio", true, Modifier, {})
                    MenuPill(if (lyricsEnabled) "Lyrics: ON" else "Lyrics: OFF", false,
                        Modifier.focusRequester(lyricsReq), onToggleLyrics)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label(clock.format(TimeFmt), 16.sp, FontWeight.SemiBold)
                    Label(clock.format(DateFmt), 10.sp, color = Dim)
                }
            }
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Glass(Modifier.width(190.dp).fillMaxHeight().zone(NexTvRadioFocus.Zone.Stations)) {
                    Column {
                        Label("Radio Stations", 15.sp, FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        if (stations.isEmpty()) {
                            Label(message ?: "Radio: no stations yet", 11.sp, color = Dim, lines = 3)
                        } else {
                            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(stations, key = { _, s -> s.id }) { _, station ->
                                    StationTile(
                                        station, current?.id == station.id && playing,
                                        station.id in favourites,
                                        Modifier.focusRequester(rowReq(station.id))
                                            .onFocusChanged { if (it.isFocused) focusedStation = station.id },
                                        onPlay = { onPlay(station) },
                                        onFavourite = { onFavourite(station) },
                                    )
                                }
                            }
                        }
                    }
                }
                NowPanel(
                    current, playing, songTitle, songArtist, message, lyricLine, lyricsEnabled, onToggleLyrics,
                    Modifier.weight(1f).fillMaxHeight().zone(NexTvRadioFocus.Zone.Controls),
                    playReq, onToggle, onNext, onPrevious,
                ) { focusedControl = it }
                Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glass(Modifier.fillMaxWidth().weight(1f).zone(NexTvRadioFocus.Zone.Recent)) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Label("◷  Recently Played", 13.sp, FontWeight.SemiBold)
                            if (recentStations.isEmpty()) Label("Nothing played yet", 10.sp, color = Dim)
                            recentStations.forEachIndexed { index, station ->
                                var recentFocused by remember(station.id) { mutableStateOf(false) }
                                Row(
                                    (if (index == 0) Modifier.focusRequester(recentReq) else Modifier)
                                        .fillMaxWidth().onFocusChanged { recentFocused = it.isFocused }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (recentFocused) Color(0x331E66D0) else Color.Transparent)
                                        .border(if (recentFocused) 1.5.dp else 0.dp, if (recentFocused) Cyan else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { onPlay(station) }.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    StationLogo(station, 30.dp, 6.dp)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Label(station.name, 11.sp, FontWeight.SemiBold)
                                        Label(station.tagline, 9.sp, color = Dim)
                                    }
                                }
                            }
                        }
                    }
                    Glass(Modifier.fillMaxWidth().weight(1f).zone(NexTvRadioFocus.Zone.Favourites)) {
                        Column {
                            Label("♥  Favourite Stations", 13.sp, FontWeight.SemiBold, Color(0xFFFF6B8A))
                            if (favStations.isEmpty()) {
                                Label("Hold OK on a station to add it", 9.sp, color = Dim, modifier = Modifier.padding(top = 5.dp), lines = 2)
                            } else Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                favStations.take(4).forEachIndexed { index, station ->
                                    var focused by remember { mutableStateOf(false) }
                                    Box(
                                        (if (index == 0) Modifier.focusRequester(favReq) else Modifier)
                                            .onFocusChanged { focused = it.isFocused }
                                            .border(if (focused) 2.dp else 0.dp, if (focused) Cyan else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { onPlay(station) },
                                    ) { StationLogo(station, 36.dp) }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(34.dp).background(Color(0xCC050B1A)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Label("NexTV Radio", 12.sp, FontWeight.Bold)
                Label("OK  Play / Pause", 10.sp, color = Dim)
                Label("▲▼  Stations", 10.sp, color = Dim)
                Label("◀▶  Move", 10.sp, color = Dim)
                Label("CH±  Change station", 10.sp, color = Dim)
                Label("Hold OK  ♥ Favourite", 10.sp, color = Dim)
                Spacer(Modifier.weight(1f))
                Label("Back  Home", 10.sp, color = Dim)
            }
        }
    }
}

@Composable
private fun Glass(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0x991A3A6B), GlassFill)), shape)
            .border(1.dp, GlassRim, shape).padding(10.dp),
        content = content,
    )
}

@Composable
private fun Label(
    text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal,
    color: Color = White, modifier: Modifier = Modifier, lines: Int = 1,
) {
    androidx.compose.material3.Text(
        text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        maxLines = lines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MenuPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.onFocusChanged { focused = it.isFocused }.clip(shape)
            .background(if (selected) Color(0xFF164C99) else Color.Transparent, shape)
            .border(if (focused || selected) 1.5.dp else 0.dp, if (focused) Cyan else Color(0x996AB8FF), shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Label(text, 13.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (selected || focused) White else Dim) }
}

@Composable
private fun StationLogo(station: NexTvStation, size: Dp, corner: Dp = 8.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier.size(size).clip(shape).background(
            Brush.linearGradient(listOf(stationColor(station.group), stationColor(station.group).copy(alpha = 0.4f))), shape,
        ), contentAlignment = Alignment.Center,
    ) {
        Label(station.name.filter { it.isLetterOrDigit() }.take(2).uppercase(), (size.value * 0.34f).sp, FontWeight.ExtraBold, Bg)
        if (!station.logo.isNullOrBlank()) {
            AsyncImage(model = station.logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun StationTile(
    station: NexTvStation, playing: Boolean, favourite: Boolean, modifier: Modifier,
    onPlay: () -> Unit, onFavourite: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val holdScope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().height(46.dp).onFocusChanged { focused = it.isFocused; if (!it.isFocused) { holdJob?.cancel(); holdJob = null; longPressed = false } }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.Menu -> { onFavourite(); true }
                    event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    holdJob?.cancel()
                                    longPressed = false
                                    holdJob = holdScope.launch {
                                        delay(650)
                                        longPressed = true
                                        onFavourite()
                                    }
                                }
                                false
                            }
                            KeyEventType.KeyUp -> {
                                holdJob?.cancel()
                                holdJob = null
                                val consumed = longPressed
                                longPressed = false
                                consumed
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .clip(shape).background(if (playing) Color(0x4D1E66D0) else if (focused) Color(0x331E66D0) else Color.Transparent, shape)
            .border(if (focused || playing) 1.5.dp else 0.dp, if (focused) Cyan else Color(0x996AB8FF), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPlay)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(station, 34.dp)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Label(station.name, 13.sp, FontWeight.SemiBold)
            Label(station.tagline, 9.sp, color = Dim)
        }
        if (playing) Equaliser(true, Modifier.size(14.dp, 12.dp), Cyan, 4)
        Label(if (favourite) "♥" else "♡", 13.sp, color = if (favourite) Color(0xFFFF6B8A) else Dim, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun NowPanel(
    current: NexTvStation?, playing: Boolean, songTitle: String?, songArtist: String?, message: String?,
    lyricLine: String?, lyricsEnabled: Boolean, onToggleLyrics: () -> Unit,
    modifier: Modifier, playReq: FocusRequester,
    onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onControlFocus: (Int) -> Unit,
) {
    Glass(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Equaliser(playing, Modifier.size(16.dp, 14.dp), Cyan, 4)
                Label("Now Playing", 15.sp, FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                if (current != null) {
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x3322D3EE)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Label(if (playing) "● LIVE" else "❚❚ PAUSED", 9.sp, FontWeight.Bold, CyanSoft)
                    }
                    current.bitrate?.let { Label("  $it kbps · Streaming", 9.sp, color = Dim) }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(150.dp).border(2.dp, Brush.linearGradient(listOf(Cyan, Color(0xFF3B82F6))), RoundedCornerShape(16.dp)).padding(3.dp),
                ) {
                    if (current != null) StationLogo(current, 144.dp, 14.dp)
                    else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0x33223355)), contentAlignment = Alignment.Center) {
                        Label("♪", 48.sp, color = Dim)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    if (current != null) {
                        Label(songTitle ?: current.name, 22.sp, FontWeight.Bold, lines = 2)
                        Label(message ?: songArtist ?: current.tagline, 14.sp, color = if (message != null) Color(0xFFE5C271) else Dim, lines = 2)
                        Label(current.name, 17.sp, FontWeight.SemiBold, Cyan, Modifier.padding(top = 4.dp))
                    } else {
                        Label(if (message != null) message else "Pick a station", 20.sp, FontWeight.Bold, lines = 2)
                        Label("Choose one on the left and press OK.", 13.sp, color = Dim)
                    }
                    if (lyricsEnabled && !lyricLine.isNullOrBlank()) Label(lyricLine, 16.sp, FontWeight.Bold, CyanSoft,
                        modifier = Modifier.padding(top = 8.dp), lines = 2)
                    Equaliser(playing, Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp), Cyan.copy(alpha = 0.8f), 28)
                }
            }
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton("⏮", 40.dp, Modifier.onFocusChanged { if (it.isFocused) onControlFocus(0) }, onPrevious)
                RoundButton(if (playing) "❚❚" else "▶", 54.dp, Modifier.focusRequester(playReq).onFocusChanged { if (it.isFocused) onControlFocus(1) }, onToggle, true)
                RoundButton("⏭", 40.dp, Modifier.onFocusChanged { if (it.isFocused) onControlFocus(2) }, onNext)
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, size: Dp, modifier: Modifier, onClick: () -> Unit, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(size).graphicsLayer { val z = if (focused) 1.08f else 1f; scaleX = z; scaleY = z }
            .onFocusChanged { focused = it.isFocused }.clip(CircleShape)
            .background(
                if (primary) Brush.radialGradient(listOf(Color(0xFF1E66D0), Color(0xFF0B2A5C)))
                else Brush.radialGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))), CircleShape,
            )
            .border(if (focused) 2.dp else 1.dp, if (focused || primary) Cyan else GlassRim, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(label, (size.value * 0.36f).sp, FontWeight.Bold) }
}

@Composable
private fun Equaliser(active: Boolean, modifier: Modifier, color: Color, bars: Int) {
    val transition = rememberInfiniteTransition(label = "nexradio-equaliser")
    val phase by transition.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val w = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val h = if (active) 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(phase + i * 0.7f)) else 0.25f
            val bh = size.height * h
            drawRoundRect(color, Offset(i * w * 1.6f, size.height - bh), Size(w, bh), CornerRadius(w / 2f))
        }
    }
}
