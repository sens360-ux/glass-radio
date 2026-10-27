package com.gathin.tvhome

import org.junit.Assert.*
import org.junit.Test

/** Regression tests for the NexTV remote-navigation contract. */
class NexTvRadioFocusTest {
    private val f = NexTvRadioFocus

    @Test fun stationsRightOpensControls() {
        assertEquals(f.Zone.Controls, f.next(f.Zone.Stations, f.Dir.Right, true, false))
    }

    @Test fun stationsUpReturnsMenuOnlyFromFirstRow() {
        assertEquals(f.Zone.Menu, f.next(f.Zone.Stations, f.Dir.Up, true, false, atTop = true))
        assertNull(f.next(f.Zone.Stations, f.Dir.Up, true, false, atTop = false))
    }

    @Test fun controlsRightReachesFavouritesOnlyAtEnd() {
        assertNull(f.next(f.Zone.Controls, f.Dir.Right, true, true, control = 1))
        assertEquals(f.Zone.Favourites, f.next(f.Zone.Controls, f.Dir.Right, true, true, control = 2))
        assertEquals(f.Zone.Controls, f.next(f.Zone.Controls, f.Dir.Right, true, false, control = 2))
    }

    @Test fun controlsLeftReturnsStationsOnlyAtStart() {
        assertNull(f.next(f.Zone.Controls, f.Dir.Left, true, false, control = 1))
        assertEquals(f.Zone.Stations, f.next(f.Zone.Controls, f.Dir.Left, true, false, control = 0))
    }

    @Test fun emptyStationListRoutesMenuToPlayer() {
        assertEquals(f.Zone.Controls, f.next(f.Zone.Menu, f.Dir.Down, false, false))
    }

    @Test fun firstStationDetectionUsesFocusedId() {
        assertTrue(f.onFirstStation(listOf("one", "two"), "one"))
        assertFalse(f.onFirstStation(listOf("one", "two"), "two"))
    }
}
