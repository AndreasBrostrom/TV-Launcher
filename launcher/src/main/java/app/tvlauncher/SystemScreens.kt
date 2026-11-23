package app.tvlauncher

import android.accounts.AccountManager
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.tvshared.toast

/** Opens Android's and Philips' own screens. */
class SystemScreens(private val activity: Activity) {

    /** Starts the first intent this TV can handle. */
    fun start(vararg intents: Intent) {
        if (intents.none { runCatching { activity.startActivity(it) }.isSuccess }) {
            activity.toast(R.string.not_available)
        }
    }

    fun openSources() =
        start(Intent("org.droidtv.intent.action.SOURCE_LIST"), Intent("com.android.tv.action.VIEW_INPUTS"))

    fun openTvSettings() = start(Intent(Settings.ACTION_SETTINGS))

    fun openAllApps() = start(
        Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
    )

    fun openStore() {
        val packages = activity.packageManager
        val store = packages.getLeanbackLaunchIntentForPackage(PLAY_STORE)
            ?: packages.getLaunchIntentForPackage(PLAY_STORE)
        if (store != null) start(store) else activity.toast(R.string.not_available)
    }

    fun openInfo(app: AppEntry) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uriOf(app)))

    fun uninstall(app: AppEntry) = start(Intent(Intent.ACTION_DELETE, uriOf(app)))

    fun openForceStop(app: AppEntry) {
        activity.toast(R.string.force_stop_hint)
        openInfo(app)
    }

    fun openRestart() {
        activity.toast(R.string.reboot_hint)
        start(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
    }

    /** This TV has no direct link to Usage access, so fall back to the Apps settings. */
    fun openUsageAccess() {
        activity.toast(R.string.usage_hint)
        start(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        )
    }

    /** The system picker needs no account permission. Its pick comes back to [openAccount]. */
    @Suppress("DEPRECATION") // the replacement needs androidx
    fun chooseAccount(requestCode: Int) {
        val picker = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
        runCatching { activity.startActivityForResult(picker, requestCode) }
            .onFailure { activity.toast(R.string.not_available) }
    }

    fun openAccount(picked: Intent?) {
        val name = picked?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return
        val page = ComponentName("com.android.tv.settings", "com.android.tv.settings.accounts.AccountSyncActivity")
        start(Intent().setComponent(page).putExtra("account_name", name), Intent(Settings.ACTION_SETTINGS))
    }

    private fun uriOf(app: AppEntry): Uri = Uri.fromParts("package", app.pkg, null)
}
