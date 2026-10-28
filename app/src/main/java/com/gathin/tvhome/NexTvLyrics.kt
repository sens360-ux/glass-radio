package com.gathin.tvhome

/**
 * Parses synchronized LRC text returned by lyrics providers.
 * One lyric line may have several timestamps; metadata lines and blank lyrics are ignored.
 */
internal object NexTvLyrics {
    private val timestamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")

    fun parseSyncedLrc(raw: String): List<Pair<Long, String>> =
        raw.lineSequence().flatMap { line ->
            val lyric = line.substringAfterLast(']').trim()
            if (lyric.isEmpty()) emptySequence() else timestamp.findAll(line).mapNotNull { match ->
                val minutes = match.groupValues[1].toLongOrNull()
                val seconds = match.groupValues[2].toDoubleOrNull()
                if (minutes == null || seconds == null) null
                else (minutes * 60_000L + (seconds * 1000).toLong()) to lyric
            }
        }.sortedBy { it.first }.toList()
}
