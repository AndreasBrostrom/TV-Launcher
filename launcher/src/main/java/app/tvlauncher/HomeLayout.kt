package app.tvlauncher

import org.json.JSONArray
import org.json.JSONObject

data class Row(val name: String, val apps: List<String>)

/**
 * What is on the home screen, as package names. Packages that are not installed
 * stay in the layout (so they reappear when installed) but are skipped by moves.
 */
data class HomeLayout(val rows: List<Row>) {

    fun rowOf(pkg: String): Int = rows.indexOfFirst { pkg in it.apps }

    operator fun contains(pkg: String): Boolean = rowOf(pkg) >= 0

    fun add(pkg: String, row: Int): HomeLayout = insert(pkg, row, rows[row].apps.size)

    fun addToNewRow(pkg: String, name: String): HomeLayout =
        HomeLayout(rows + Row(name, emptyList())).add(pkg, rows.size)

    fun remove(pkg: String): HomeLayout =
        HomeLayout(rows.map { it.copy(apps = it.apps - pkg) })

    /** Swaps [pkg] past its nearest visible neighbour; [step] is -1 or 1. */
    fun moveInRow(pkg: String, step: Int, visible: (String) -> Boolean): HomeLayout {
        val row = rowOf(pkg)
        if (row < 0) return this
        val apps = rows[row].apps
        var target = apps.indexOf(pkg) + step
        while (target in apps.indices && !visible(apps[target])) target += step
        return if (target in apps.indices) insert(pkg, row, target) else this
    }

    /** Moves [pkg] to the row above (-1) or below (1), keeping its column. */
    fun moveToRow(pkg: String, step: Int, visible: (String) -> Boolean): HomeLayout {
        val from = rowOf(pkg)
        val to = from + step
        if (from < 0 || to !in rows.indices) return this
        val column = rows[from].apps.filter(visible).indexOf(pkg)
        val target = rows[to].apps
        val before = target.filter(visible).getOrNull(column)
        return insert(pkg, to, before?.let(target::indexOf) ?: target.size)
    }

    fun renameRow(row: Int, name: String): HomeLayout =
        HomeLayout(rows.mapIndexed { i, r -> if (i == row) r.copy(name = name) else r })

    fun deleteRow(row: Int): HomeLayout = HomeLayout(rows.filterIndexed { i, _ -> i != row })

    /** Swaps the row past its nearest shown neighbour; [step] is -1 or 1. */
    fun moveRow(row: Int, step: Int, visible: (String) -> Boolean): HomeLayout {
        var target = row + step
        while (target in rows.indices && rows[target].apps.none(visible)) target += step
        if (target !in rows.indices) return this
        return HomeLayout(rows.toMutableList().apply { add(target, removeAt(row)) })
    }

    private fun insert(pkg: String, row: Int, index: Int): HomeLayout =
        HomeLayout(remove(pkg).rows.mapIndexed { i, r ->
            if (i != row) r
            else r.copy(apps = r.apps.toMutableList().apply { add(index.coerceAtMost(size), pkg) })
        })

    fun toJson(): String = JSONArray(rows.map {
        JSONObject().put("name", it.name).put("apps", JSONArray(it.apps))
    }).toString()

    companion object {
        fun fromJson(json: String): HomeLayout {
            val array = JSONArray(json)
            return HomeLayout(List(array.length()) { i ->
                val row = array.getJSONObject(i)
                val apps = row.getJSONArray("apps")
                Row(row.getString("name"), List(apps.length(), apps::getString))
            })
        }

        val DEFAULT = HomeLayout(
            listOf(
                Row(
                    "Streaming",
                    listOf(
                        "com.google.android.youtube.tv",
                        "com.netflix.ninja",
                        "com.wbd.stream",
                        "com.crunchyroll.crunchyroid",
                        "com.disney.disneyplus",
                        "com.amazon.amazonvideo.livingroom",
                    ),
                ),
                Row("Media", listOf("org.videolan.vlc")),
                Row("TV", listOf("se.svt.android.svtplay", "se.tv4.tv4playtab")),
                Row("Music", listOf("com.spotify.tv.android", "com.google.android.youtube.tvmusic")),
                Row("Network", listOf("com.wireguard.android")),
                Row(
                    "Tools",
                    listOf(
                        "org.chromium.chrome",
                        "com.android.chrome",
                        FILES,
                        NETWORK,
                        TV_SETTINGS,
                    ),
                ),
            ),
        )
    }
}
