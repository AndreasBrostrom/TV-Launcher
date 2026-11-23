package app.tvlauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(maxOf(now - WINDOW_MS, since), now)
        val event = UsageEvents.Event()
        val found = mutableSetOf<String>()
        while (events?.getNextEvent(event) == true) {
            @Suppress("DEPRECATION") // its replacement needs API 29
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) found += event.packageName
        }
        return found
    }
}
