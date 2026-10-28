package com.gathin.tvhome

/**
 * Ported from NexTV RadioFocus.kt (MIT, copyright 2026 Nexdata).
 * TV remote focus travels between the menu, stations, playback controls and favourites.
 */
internal object NexTvRadioFocus {
    enum class Zone { Menu, Stations, Controls, Recent, Favourites }
    enum class Dir { Up, Down, Left, Right }

    fun next(
        zone: Zone, dir: Dir, hasStations: Boolean, hasFavourites: Boolean,
        atTop: Boolean = true, control: Int = 1, hasRecent: Boolean = false
    ): Zone? = when (zone) {
        Zone.Menu -> if (dir == Dir.Down) (if (hasStations) Zone.Stations else Zone.Controls) else null
        Zone.Stations -> when (dir) {
            Dir.Up -> if (atTop) Zone.Menu else null
            Dir.Right -> Zone.Controls
            Dir.Left -> Zone.Stations
            Dir.Down -> null
        }
        Zone.Controls -> when (dir) {
            Dir.Up -> Zone.Menu
            Dir.Left -> if (control > 0) null else if (hasStations) Zone.Stations else Zone.Controls
            Dir.Right -> if (control < 2) null else if (hasFavourites) Zone.Favourites else if (hasRecent) Zone.Recent else Zone.Controls
            Dir.Down -> Zone.Controls
        }
        Zone.Recent -> when (dir) {
            Dir.Up -> if (hasFavourites) Zone.Favourites else Zone.Menu
            Dir.Down -> null
            Dir.Left -> Zone.Controls
            Dir.Right -> null
        }
        Zone.Favourites -> when (dir) {
            Dir.Up -> Zone.Menu
            Dir.Down -> if (hasRecent) Zone.Recent else null
            Dir.Left -> Zone.Controls
            else -> null
        }
    }
    fun onFirstStation(ids: List<String>, focused: String?): Boolean =
        focused == null || ids.isEmpty() || focused == ids.first()
}
