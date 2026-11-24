package app.tvlauncher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Google's tvrecommendations app opens the old launcher by name a few seconds after the TV
 * wakes, whichever launcher is the default. Shows Home on top when that happens. Any other
 * app or input that comes up is left alone.
 */
class WakeGuard : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())

    /** SCREEN_ON only reaches running apps. As the home app, we normally are. */
    fun register(context: Context) {
        context.registerReceiver(this, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1 // apps hear of it one at a time
        })
    }

    override fun onReceive(context: Context, intent: Intent) {
        handler.removeCallbacksAndMessages(null)
        if (intent.action != Intent.ACTION_SCREEN_ON) return
        if (!HomeSwitch.isDefault(context) || !RecentApps.isAllowed(context)) return
        val old = OldLauncher.find(context) ?: return
        val since = System.currentTimeMillis() - LATE_MS
        watch(context.applicationContext, old, since, until = SystemClock.elapsedRealtime() + WATCH_MS)
    }

    private fun watch(context: Context, old: ComponentName, since: Long, until: Long) {
        if (RecentApps.lastOpened(context, since) == old) {
            context.startActivity(HomeSwitch.homeIntent)
        } else if (SystemClock.elapsedRealtime() < until) {
            handler.postDelayed({ watch(context, old, since, until) }, CHECK_MS)
        }
    }

    private companion object {
        const val WATCH_MS = 15_000L
        const val CHECK_MS = 500L

        /** How late we may hear of the wake. */
        const val LATE_MS = 5_000L
    }
}
