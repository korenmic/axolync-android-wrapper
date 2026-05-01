package com.axolync.android.bridge

data class StatusBarSongSignal(
    val title: String,
    val artist: String?,
    val sourcePackage: String,
    val capturedAtMs: Long
)

data class StatusBarNotificationDebugEntry(
    val sourcePackage: String,
    val titleRaw: String?,
    val textRaw: String?,
    val subTextRaw: String?,
    val bigTextRaw: String?,
    val tickerRaw: String?,
    val category: String?,
    val capturedAtMs: Long,
    val parseReasonCode: String,
    val parsedTitle: String?,
    val parsedArtist: String?
)

data class ParsedStatusBarSongSignal(
    val signal: StatusBarSongSignal?,
    val reasonCode: String
)

object StatusBarSongSignalStore {
    private const val MAX_SIGNAL_AGE_MS = 5 * 60 * 1000L

    @Volatile
    private var latest: StatusBarSongSignal? = null
    @Volatile
    private var debugCaptureEnabled: Boolean = false
    private val debugEntries: MutableList<StatusBarNotificationDebugEntry> = mutableListOf()
    private const val MAX_DEBUG_ENTRIES = 500

    @Synchronized
    fun update(signal: StatusBarSongSignal) {
        pruneExpiredLocked()
        if (!isWithinRetentionWindow(signal.capturedAtMs)) return
        val current = latest
        if (current == null || signal.capturedAtMs >= current.capturedAtMs) {
            latest = signal
        }
    }

    @Synchronized
    fun latestSignal(): StatusBarSongSignal? {
        pruneExpiredLocked()
        return latest
    }

    @Synchronized
    fun clear() {
        latest = null
        debugEntries.clear()
    }

    @Synchronized
    fun setDebugCaptureEnabled(enabled: Boolean) {
        debugCaptureEnabled = enabled
    }

    fun isDebugCaptureEnabled(): Boolean = debugCaptureEnabled

    @Synchronized
    fun appendDebugEntry(entry: StatusBarNotificationDebugEntry) {
        if (!debugCaptureEnabled) return
        pruneExpiredLocked()
        if (!isWithinRetentionWindow(entry.capturedAtMs)) return
        if (debugEntries.size >= MAX_DEBUG_ENTRIES) {
            debugEntries.removeAt(0)
        }
        debugEntries.add(entry)
    }

    @Synchronized
    fun debugEntriesSnapshot(limit: Int = 200): List<StatusBarNotificationDebugEntry> {
        pruneExpiredLocked()
        if (limit <= 0) return emptyList()
        return debugEntries.takeLast(limit)
    }

    @Synchronized
    fun clearDebugEntries() {
        debugEntries.clear()
    }

    @Synchronized
    fun pruneExpired(nowMs: Long = System.currentTimeMillis()) {
        pruneExpiredLocked(nowMs)
    }

    fun isWithinRetentionWindow(capturedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (capturedAtMs <= 0L) return false
        val ageMs = nowMs - capturedAtMs
        return ageMs in 0..MAX_SIGNAL_AGE_MS
    }

    private fun pruneExpiredLocked(nowMs: Long = System.currentTimeMillis()) {
        val current = latest
        if (current != null && !isWithinRetentionWindow(current.capturedAtMs, nowMs)) {
            latest = null
        }
        debugEntries.removeAll { entry -> !isWithinRetentionWindow(entry.capturedAtMs, nowMs) }
    }
}

fun parseStatusBarSongSignalDetailed(
    titleRaw: String?,
    textRaw: String?,
    subTextRaw: String?,
    sourcePackage: String,
    capturedAtMs: Long
): ParsedStatusBarSongSignal {
    val title = titleRaw.orEmpty().trim()
    val text = textRaw.orEmpty().trim()
    val subText = subTextRaw.orEmpty().trim()
    if (title.isEmpty() && text.isEmpty()) {
        return ParsedStatusBarSongSignal(signal = null, reasonCode = "empty_payload")
    }

    val shazamish = sourcePackage.contains("shazam", ignoreCase = true)
    if (!shazamish) {
        // SongSense status-bar adapter is currently Shazam-specific to reduce false positives.
        return ParsedStatusBarSongSignal(signal = null, reasonCode = "non_shazam_package")
    }

    val nowPlayingMatch = Regex("^\\s*now\\s+playing\\s+(.+?)\\s+by\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        .find(text)
    if (nowPlayingMatch != null) {
        val songTitle = nowPlayingMatch.groupValues[1].trim()
        val artist = nowPlayingMatch.groupValues[2].trim()
        if (songTitle.isNotEmpty()) {
            return ParsedStatusBarSongSignal(
                signal = StatusBarSongSignal(
                    title = songTitle,
                    artist = artist.ifEmpty { null },
                    sourcePackage = sourcePackage,
                    capturedAtMs = capturedAtMs
                ),
                reasonCode = "matched_now_playing_by"
            )
        }
    }

    // Shazam "song card" notifications are often: title=<song>, text=<artist>.
    val titleIsAutoShazamLabel = title.equals("Auto Shazam is on", ignoreCase = true)
        || title.equals("Auto Shazam", ignoreCase = true)
        || title.equals("Shazam", ignoreCase = true)
    if (title.isNotEmpty() && !titleIsAutoShazamLabel) {
        val artist = when {
            text.isNotEmpty() && !text.startsWith("Now playing", ignoreCase = true) -> text
            subText.isNotEmpty() -> subText
            else -> ""
        }
        return ParsedStatusBarSongSignal(
            signal = StatusBarSongSignal(
                title = title,
                artist = artist.ifEmpty { null },
                sourcePackage = sourcePackage,
                capturedAtMs = capturedAtMs
            ),
            reasonCode = "matched_title_artist_card"
        )
    }

    // Fallback for single-line payloads that use separators.
    val separators = listOf(" - ", " – ", " — ", " • ")
    for (sep in separators) {
        if (text.contains(sep)) {
            val parts = text.split(sep, limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                val leftWords = parts[0].split(Regex("\\s+")).size
                val rightWords = parts[1].split(Regex("\\s+")).size
                // Heuristic: short-left + long-right is usually artist - title.
                val leftLooksLikeArtist = leftWords <= 3 && rightWords >= 4
                val songTitle = if (leftLooksLikeArtist) parts[1] else parts[0]
                val artist = if (leftLooksLikeArtist) parts[0] else parts[1]
                return ParsedStatusBarSongSignal(
                    signal = StatusBarSongSignal(
                        title = songTitle,
                        artist = artist,
                        sourcePackage = sourcePackage,
                        capturedAtMs = capturedAtMs
                    ),
                    reasonCode = "matched_separator_pair"
                )
            }
        }
    }

    return ParsedStatusBarSongSignal(signal = null, reasonCode = "shazam_payload_not_song_like")
}

fun parseStatusBarSongSignal(
    titleRaw: String?,
    textRaw: String?,
    subTextRaw: String?,
    sourcePackage: String,
    capturedAtMs: Long
): StatusBarSongSignal? {
    return parseStatusBarSongSignalDetailed(
        titleRaw = titleRaw,
        textRaw = textRaw,
        subTextRaw = subTextRaw,
        sourcePackage = sourcePackage,
        capturedAtMs = capturedAtMs
    ).signal
}
