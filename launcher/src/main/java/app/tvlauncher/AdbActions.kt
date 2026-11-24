package app.tvlauncher

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import app.tvshared.inBackground
import app.tvshared.toast
import java.util.concurrent.Executors

/** Everything that goes through adb, because Android reserves it for system apps. */
class AdbActions(private val activity: Activity, private val screens: SystemScreens) {
    private val worker = Executors.newSingleThreadExecutor() // may wait a minute for approval

    fun shutDown() = run("shell:reboot -p")

    fun reboot() = run("reboot:", fallback = screens::openRestart)

    fun suspendTv() = run("shell:input keyevent ${KeyEvent.KEYCODE_SLEEP}")

    fun forceStop(app: AppEntry) {
        val isPlainName = app.pkg.matches(Regex("[A-Za-z0-9_.]+")) // it goes into a shell command
        if (!isPlainName || app.pkg in SYSTEM_PARTS) return screens.openForceStop(app)
        run("shell:am force-stop ${app.pkg}", fallback = { screens.openForceStop(app) }, done = {
            activity.toast(activity.getString(R.string.force_stopped, app.label))
        })
    }

    /** [done] runs only once it has really changed. */
    fun setOldLauncherEnabled(enabled: Boolean, done: () -> Unit) {
        val context = activity.applicationContext
        run("old launcher enabled=$enabled", done = done) { OldLauncher.setEnabled(context, it, enabled) }
    }

    fun close() {
        worker.shutdownNow()
    }

    private fun run(
        service: String,
        fallback: () -> Unit = {},
        done: () -> Unit = {},
        action: (LocalAdb) -> Unit = { it.run(service) },
    ) {
        val key = OldLauncher.adbKey(activity)
        if (!key.exists()) activity.toast(R.string.adb_hint)
        activity.inBackground(worker, { runCatching { action(LocalAdb(key)) }.exceptionOrNull() }) { error ->
            if (error == null) return@inBackground done()
            Log.w("TvLauncher", "adb $service failed", error)
            activity.toast(activity.getString(R.string.adb_failed, error.message ?: error.javaClass.simpleName))
            fallback()
        }
    }
}
