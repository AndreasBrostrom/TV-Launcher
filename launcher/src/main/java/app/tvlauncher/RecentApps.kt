package app.tvlauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/** Android hides running apps from us, so this uses Usage access: what was opened lately. */
object RecentApps {
    private const val WINDOW_MS = 2 * 60 * 60 * 1000L

    fun isAllowed(context: Context): Boolean {
        val mode = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT &&
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Packages opened within the last two hours and after [since]. Slow: call off the main thread. */
    fun load(context: Context, since: Long): Set<String> {
        val found = mutableSetOf<String>()
        forEachOpened(context, maxOf(System.currentTimeMillis() - WINDOW_MS, since)) { found += it.packageName }
        return found
    }

    /** The screen opened last, if any was after [since]. */
    fun lastOpened(context: Context, since: Long): ComponentName? {
        var last: ComponentName? = null
        forEachOpened(context, since) { last = ComponentName(it.packageName, it.className) }
        return last
    }

    private fun forEachOpened(context: Context, since: Long, action: (UsageEvents.Event) -> Unit) {
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(since, System.currentTimeMillis())
        val event = UsageEvents.Event()
        while (events?.getNextEvent(event) == true) {
            @Suppress("DEPRECATION") // its replacement needs API 29
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) action(event)
        }
    }
}
