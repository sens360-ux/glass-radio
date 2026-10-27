package com.gathin.tvhome

import org.junit.Assert.*
import org.junit.Test

/** Regression tests for the NexTV remote-navigation contract. */
class NexTvRadioFocusTest {
    private val f = NexTvRadioFocus

    @Test fun stationsRightOpensControls() {
        assertEquals(NexTvRadioFocus.Zone.Controls, f.next(NexTvRadioFocus.Zone.Stations, NexTvRadioFocus.Dir.Right, true, false))
    }

    @Test fun stationsUpReturnsMenuOnlyFromFirstRow() {
        assertEquals(NexTvRadioFocus.Zone.Menu, f.next(NexTvRadioFocus.Zone.Stations, NexTvRadioFocus.Dir.Up, true, false, atTop = true))
        assertNull(f.next(NexTvRadioFocus.Zone.Stations, NexTvRadioFocus.Dir.Up, true, false, atTop = false))
    }

    @Test fun controlsRightReachesFavouritesOnlyAtEnd() {
        assertNull(f.next(NexTvRadioFocus.Zone.Controls, NexTvRadioFocus.Dir.Right, true, true, control = 1))
        assertEquals(NexTvRadioFocus.Zone.Favourites, f.next(NexTvRadioFocus.Zone.Controls, NexTvRadioFocus.Dir.Right, true, true, control = 2))
        assertEquals(NexTvRadioFocus.Zone.Controls, f.next(NexTvRadioFocus.Zone.Controls, NexTvRadioFocus.Dir.Right, true, false, control = 2))
    }

    @Test fun controlsLeftReturnsStationsOnlyAtStart() {
        assertNull(f.next(NexTvRadioFocus.Zone.Controls, NexTvRadioFocus.Dir.Left, true, false, control = 1))
        assertEquals(NexTvRadioFocus.Zone.Stations, f.next(NexTvRadioFocus.Zone.Controls, NexTvRadioFocus.Dir.Left, true, false, control = 0))
    }

    @Test fun emptyStationListRoutesMenuToPlayer() {
        assertEquals(NexTvRadioFocus.Zone.Controls, f.next(NexTvRadioFocus.Zone.Menu, NexTvRadioFocus.Dir.Down, false, false))
    }

    @Test fun firstStationDetectionUsesFocusedId() {
        assertTrue(f.onFirstStation(listOf("one", "two"), "one"))
        assertFalse(f.onFirstStation(listOf("one", "two"), "two"))
    }
}
