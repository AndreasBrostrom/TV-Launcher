package app.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OldLauncherTest {
    @Test fun disablesForTheUserOnly() = assertEquals(
        "shell:pm disable-user --user 0 a.b; pm disable-user --user 0 c.d",
        OldLauncher.command(listOf("a.b", "c.d"), enabled = false),
    )

    @Test fun enables() =
        assertEquals("shell:pm enable --user 0 a.b", OldLauncher.command(listOf("a.b"), enabled = true))

    @Test fun onlyPlainNamesReachTheShell() {
        assertThrows(IllegalArgumentException::class.java) { OldLauncher.command(listOf("a.b; reboot"), true) }
    }
}
