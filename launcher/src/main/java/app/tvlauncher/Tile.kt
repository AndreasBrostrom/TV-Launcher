package app.tvlauncher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.tvshared.dp

/** Our own tiles: icon on top and the name below. */
private val STACKED = SYSTEM_PARTS + FILES

/** The view of one app, tagged with its package. */
fun createTile(parent: ViewGroup, app: AppEntry): View {
    val tile = LayoutInflater.from(parent.context).inflate(R.layout.tile, parent, false)
    tile.clipToOutline = true // the XML attribute needs API 31
    tile.tag = app.pkg
    tile.contentDescription = app.label
    if (app.banner != null) {
        tile.findViewById<ImageView>(R.id.banner).setImageDrawable(app.banner)
        tile.findViewById<View>(R.id.fallback).visibility = View.GONE
    } else {
        tile.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
        tile.findViewById<TextView>(R.id.label).text = app.label
        if (app.pkg in STACKED) stack(tile)
    }
    tile.setOnFocusChangeListener { view, hasFocus ->
        val scale = if (hasFocus) 1.1f else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
        view.z = if (hasFocus) 8f else 0f
    }
    return tile
}

/** Shows the corner badge if the tile's app is in [recent]. Ignores views that are not tiles. */
fun updateBadge(tile: View, recent: Set<String>) {
    tile.findViewById<View>(R.id.recent)?.visibility = if (tile.tag in recent) View.VISIBLE else View.GONE
}

private fun stack(tile: View) {
    val context = tile.context
    tile.findViewById<LinearLayout>(R.id.fallback).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        context.dp(4).let { setPadding(it, it, it, it) }
    }
    tile.findViewById<View>(R.id.icon).layoutParams = context.dp(38).let { LinearLayout.LayoutParams(it, it) }
    tile.findViewById<TextView>(R.id.label).apply {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = context.dp(4) }
        maxLines = 1
    }
}
