package app.tvlauncher

/**
 * Recent crash times, in milliseconds since the TV started. That clock begins again at
 * every start, so times from another [boot] cannot be compared and are dropped.
 */
data class CrashLog(val boot: Int, val times: List<Long> = emptyList()) {
    val isLooping: Boolean get() = times.size >= LIMIT

    fun add(currentBoot: Int, now: Long): CrashLog {
        val earlier = if (currentBoot == boot) times.filter { now - it in 0..WINDOW_MS } else emptyList()
        return CrashLog(currentBoot, earlier + now)
    }

    private companion object {
        const val WINDOW_MS = 60_000L
        const val LIMIT = 3
    }
}
