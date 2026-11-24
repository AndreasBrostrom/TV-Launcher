package app.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    private fun crashes(vararg bootAndTime: Pair<Int, Long>) =
        bootAndTime.fold(CrashLog(boot = -1)) { log, (boot, time) -> log.add(boot, time) }

    @Test fun threeWithinAMinuteIsALoop() =
        assertTrue(crashes(1 to 10_000, 1 to 30_000, 1 to 70_000).isLooping)

    @Test fun twoIsNot() = assertFalse(crashes(1 to 10_000, 1 to 30_000).isLooping)

    @Test fun oldCrashesAreForgotten() {
        val log = crashes(1 to 10_000, 1 to 30_000, 1 to 70_001)
        assertEquals(listOf(30_000L, 70_001L), log.times)
        assertFalse(log.isLooping)
    }

    @Test fun otherBootsDoNotCount() {
        val log = crashes(1 to 20_000, 2 to 21_000, 3 to 22_000)
        assertEquals(CrashLog(3, listOf(22_000L)), log)
        assertFalse(log.isLooping)
    }

    @Test fun loopAfterARestartStillCounts() =
        assertTrue(crashes(1 to 20_000, 2 to 20_000, 2 to 25_000, 2 to 30_000).isLooping)
}
