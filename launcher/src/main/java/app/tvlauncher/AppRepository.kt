package app.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.provider.Settings

class AppEntry(
    val pkg: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val intent: Intent,
    val isSystem: Boolean,
)

const val PLAY_STORE = "com.android.vending"

const val F_DROID = "org.fdroid.fdroid"
const val FLICKY = "app.flicky"
const val FILES = "app.tvfiles"

/** System parts without a launcher entry, which we add ourselves. */
const val TV_SETTINGS = "org.droidtv.settings"
const val NETWORK = "org.droidtv.networkwizard"
val SYSTEM_PARTS = setOf(TV_SETTINGS, NETWORK)

/** "How to…": unwanted, but the TV's sources screen breaks when it is disabled. */
private const val PHILIPS_HELP = "org.droidtv.eum"

class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /** Every launchable app, sorted by name. Slow: call off the main thread. */
    fun load(): List<AppEntry> {
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        // Phone-only apps count if the user installed them; the preloaded ones
        // are vendor test screens.
        val phone = query(Intent.CATEGORY_LAUNCHER).filterNot { it.isSystem() }
        val hidden = setOf(context.packageName, PHILIPS_HELP)
        val installed = (tv + phone)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in hidden }
            .map(::toEntry)
        val systemParts = listOfNotNull(
            systemPart(TV_SETTINGS, R.string.settings, R.drawable.ic_tv_settings, Settings.ACTION_SETTINGS),
            systemPart(NETWORK, R.string.network, R.drawable.ic_network, Settings.ACTION_WIFI_SETTINGS),
        )
        return (installed + systemParts).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, AppEntry::label))
    }

    private fun systemPart(pkg: String, label: Int, icon: Int, action: String): AppEntry? {
        if (runCatching { pm.getApplicationInfo(pkg, 0) }.isFailure) return null
        return AppEntry(
            pkg = pkg,
            label = context.getString(label),
            banner = null,
            icon = context.getDrawable(icon),
            intent = Intent(action),
            isSystem = true,
        )
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun ResolveInfo.isSystem(): Boolean =
        activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun toEntry(info: ResolveInfo): AppEntry {
        val activity = info.activityInfo
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        return AppEntry(
            pkg = activity.packageName,
            label = info.loadLabel(pm).toString(),
            banner = banner,
            icon = if (banner == null) info.loadIcon(pm) else null,
            intent = Intent(Intent.ACTION_MAIN)
                .setClassName(activity.packageName, activity.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            isSystem = info.isSystem(),
        )
    }
}
