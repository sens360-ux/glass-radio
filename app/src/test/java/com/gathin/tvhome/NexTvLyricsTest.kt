package com.gathin.tvhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexTvLyricsTest {
    @Test fun parsesTimestampedLyrics() {
        assertEquals(
            listOf(10_500L to "Hello", 72_000L to "World"),
            NexTvLyrics.parseSyncedLrc("[00:10.50]Hello\n[01:12.00]World"),
        )
    }

    @Test fun supportsMultipleTimestampsAndSorts() {
        assertEquals(
            listOf(1_000L to "Repeat", 2_000L to "Repeat", 3_250L to "Later"),
            NexTvLyrics.parseSyncedLrc("[00:03.25]Later\n[00:01.00][00:02.00]Repeat"),
        )
    }

    @Test fun ignoresMetadataAndBlankLines() {
        assertTrue(NexTvLyrics.parseSyncedLrc("[ar:Artist]\n[00:00.00]\n").isEmpty())
    }
}
