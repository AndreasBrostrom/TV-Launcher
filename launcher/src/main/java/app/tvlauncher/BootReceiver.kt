package app.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Philips opens the last TV input on a cold boot, even an empty one. Shows Home on top of it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !HomeSwitch.isDefault(context)) return
        // Too late, and whoever is watching has already chosen something else.
        if (SystemClock.elapsedRealtime() > LATEST_MS) return
        context.startActivity(HomeSwitch.homeIntent)
    }

    private companion object {
        const val LATEST_MS = 90_000L
    }
}
