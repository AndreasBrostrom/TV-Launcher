package app.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Turns our HOME alias on and off. Off means Android falls back to the stock launcher. */
object HomeSwitch {
    val homeIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun isDefault(context: Context): Boolean =
        context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName

    fun enable(context: Context) = set(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

    fun disable(context: Context) = set(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

    /** The launcher that was there before us, whatever its vendor. */
    fun oldLauncher(context: Context): Intent? =
        context.packageManager.queryIntentActivities(homeIntent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .maxByOrNull { it.priority } // Settings' FallbackHome sits at -1000
            ?.takeIf { it.priority >= 0 }
            ?.let { homeIntent.setClassName(it.activityInfo.packageName, it.activityInfo.name) }

    private fun set(context: Context, state: Int) =
        context.packageManager.setComponentEnabledSetting(
            alias(context), state, PackageManager.DONT_KILL_APP,
        )

    private fun alias(context: Context) =
        ComponentName(context.packageName, "app.tvlauncher.HomeAlias")
}
