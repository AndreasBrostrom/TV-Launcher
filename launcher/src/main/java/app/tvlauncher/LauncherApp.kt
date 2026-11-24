package app.tvlauncher

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlin.concurrent.thread

class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WakeGuard().register(this)
        val next = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { giveUpHomeIfCrashLooping() }
            next?.uncaughtException(thread, error)
        }
    }

    /** A home app that keeps crashing locks you out of the TV, so step aside. */
    @SuppressLint("ApplySharedPref") // commit: the process is about to die
    private fun giveUpHomeIfCrashLooping() {
        val prefs = getSharedPreferences("crashes", Context.MODE_PRIVATE)
        val log = CrashLog(
            prefs.getInt("boot", -1),
            prefs.getString("times", "").orEmpty().split(',').mapNotNull(String::toLongOrNull),
        ).add(
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0),
            SystemClock.elapsedRealtime(),
        )
        if (log.isLooping) {
            // Without the old launcher there would be no Home at all. Not on the main
            // thread, where networking is not allowed, not even to ourselves.
            var ready = false
            thread { runCatching { ready = OldLauncher.ensureEnabled(this) } }.join(ENABLE_MS)
            if (ready) HomeSwitch.disable(this)
            prefs.edit().clear().commit()
        } else {
            prefs.edit().putInt("boot", log.boot).putString("times", log.times.joinToString(",")).commit()
        }
    }

    private companion object {
        const val ENABLE_MS = 25_000L
    }
}
