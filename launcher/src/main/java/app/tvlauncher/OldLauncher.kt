package app.tvlauncher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.File
import java.io.IOException

/**
 * The launcher that was there before us, whatever its vendor. It can be disabled, but only
 * while we are Home, and is always enabled again before anything opens or relies on it.
 */
object OldLauncher {
    /** Opens the old launcher on its own, and would fail without it, so they go together. */
    private const val COMPANION = "com.google.android.tvrecommendations"
    private const val KEY_NAME = "component"

    /** Also found while disabled, if we disabled it. */
    fun find(context: Context): ComponentName? {
        val enabled = context.packageManager.queryIntentActivities(HomeSwitch.homeIntent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .maxByOrNull { it.priority } // Settings' FallbackHome sits at -1000
            ?.takeIf { it.priority >= 0 }
            ?.let { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        return enabled ?: remembered(context)?.takeIf { isEnabled(context, it.packageName) != null }
    }

    fun intent(old: ComponentName): Intent = HomeSwitch.homeIntent.setComponent(old)

    fun isDisabled(context: Context): Boolean =
        find(context)?.let { old -> packages(context, old).any { isEnabled(context, it) == false } } ?: false

    /** Enables or disables it through [adb]. Blocks; throws if it did not work. */
    @SuppressLint("ApplySharedPref") // commit: without this we could not find it again
    fun setEnabled(context: Context, adb: LocalAdb, enabled: Boolean) {
        val old = find(context) ?: throw IOException("no old launcher")
        if (!enabled) {
            if (!HomeSwitch.isDefault(context)) throw IOException("TV Launcher is not the default")
            prefs(context).edit().putString(KEY_NAME, old.flattenToString()).commit()
        }
        val packages = packages(context, old)
        adb.run(command(packages, enabled), TIMEOUT_MS)
        packages.find { isEnabled(context, it) != enabled }?.let { throw IOException("$it did not change") }
    }

    /** For when no screen can: enables it if needed. False if that failed. Blocks. */
    fun ensureEnabled(context: Context): Boolean = !isDisabled(context) ||
        runCatching { setEnabled(context, LocalAdb(adbKey(context)), true) }.isSuccess

    fun adbKey(context: Context) = File(context.noBackupFilesDir, "adb_key") // never leaves this TV

    internal fun command(packages: List<String>, enabled: Boolean): String {
        require(packages.all { it.matches(Regex("[A-Za-z0-9_.]+")) }) // they go into a shell command
        val change = if (enabled) "pm enable --user 0" else "pm disable-user --user 0"
        return "shell:" + packages.joinToString("; ") { "$change $it" }
    }

    private fun packages(context: Context, old: ComponentName): List<String> =
        listOf(old.packageName, COMPANION).filter { isEnabled(context, it) != null }.distinct()

    /** Null if not installed. */
    private fun isEnabled(context: Context, pkg: String): Boolean? = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun remembered(context: Context): ComponentName? =
        prefs(context).getString(KEY_NAME, null)?.let(ComponentName::unflattenFromString)

    private fun prefs(context: Context) = context.getSharedPreferences("old_launcher", Context.MODE_PRIVATE)

    private const val TIMEOUT_MS = 20_000 // pm is slow on this TV
}
