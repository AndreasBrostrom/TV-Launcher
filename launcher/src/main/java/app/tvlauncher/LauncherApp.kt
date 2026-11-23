package app.tvlauncher

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.SystemClock

class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
        val now = SystemClock.elapsedRealtime()
        val recent = prefs.getString("times", "").orEmpty()
            .split(',').mapNotNull(String::toLongOrNull)
            .filter { now - it in 0..WINDOW_MS } + now
        if (recent.size >= LIMIT) {
            HomeSwitch.disable(this)
            prefs.edit().clear().commit()
        } else {
            prefs.edit().putString("times", recent.joinToString(",")).commit()
        }
    }

    private companion object {
        const val WINDOW_MS = 60_000L
        const val LIMIT = 3
    }
}
