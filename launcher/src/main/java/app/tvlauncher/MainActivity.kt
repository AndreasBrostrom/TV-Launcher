package app.tvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import app.tvshared.addDivider
import app.tvshared.askName
import app.tvshared.confirm
import app.tvshared.dp
import app.tvshared.inBackground
import app.tvshared.showMenu
import app.tvshared.toast
import java.util.concurrent.Executors
import app.tvshared.R as SharedR

class MainActivity : Activity() {
    private enum class Screen { HOME, APPS }

    private val worker = Executors.newSingleThreadExecutor()
    private val screens = SystemScreens(this)
    private val adb = AdbActions(this, screens)
    private val prefs by lazy { getSharedPreferences("home", Context.MODE_PRIVATE) }

    private lateinit var rowsView: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var navHome: View
    private lateinit var navApps: View

    private var apps: List<AppEntry> = emptyList()
    private var layout = HomeLayout.DEFAULT
    private var screen = Screen.HOME

    /** Packages that get the corner badge. */
    private var recent: Set<String> = emptySet()

    /** Focusable views on screen, as rows of columns. Drives D-pad up/down. */
    private val grid = mutableListOf<List<View>>()

    /** Package being rearranged on Home, or null. */
    private var moving: String? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        rowsView = findViewById(R.id.rows)
        scroll = findViewById(R.id.scroll)
        navHome = nav(R.id.nav_home) { show(Screen.HOME) }
        navApps = nav(R.id.nav_apps) { show(Screen.APPS) }
        nav(R.id.nav_store) { screens.openStore() }
        nav(R.id.nav_sources) { screens.openSources() }
        nav(R.id.nav_settings) { screens.openTvSettings() }
        nav(R.id.nav_profile) { screens.chooseAccount(REQUEST_ACCOUNT) }

        layout = loadLayout()

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        navHome.isSelected = true
        navHome.requestFocus()
        reload()
    }

    /** Home and the app icon can run as two instances, so pick up the other's edits. */
    override fun onResume() {
        super.onResume()
        val saved = loadLayout()
        if (saved != layout) {
            moving = null
            layout = saved
            render()
        }
        refreshRecent()
        if (screen == Screen.APPS) render() // Usage access may have been switched
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        worker.shutdownNow()
        adb.close()
        super.onDestroy()
    }

    /** The Home button lands here when we are already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        moving = null
        show(Screen.HOME)
        focusFirstTile()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        moving?.let { handleMoveKey(it, event) } ?: super.dispatchKeyEvent(event)

    @Deprecated("Still the only back hook on API 26")
    override fun onBackPressed() {
        when {
            screen == Screen.APPS -> {
                show(Screen.HOME)
                navHome.requestFocus()
            }
            HomeSwitch.isDefault(this) -> Unit // nothing behind the home screen
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    @Deprecated("The replacement needs androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ACCOUNT && resultCode == RESULT_OK) screens.openAccount(data)
    }

    // region Data

    private fun reload() = inBackground(worker, { AppRepository(this).load() }) { loaded ->
        val first = apps.isEmpty()
        apps = loaded
        render()
        if (first) focusFirstTile()
    }

    private fun loadLayout(): HomeLayout =
        prefs.getString(KEY_LAYOUT, null)
            ?.let { runCatching { HomeLayout.fromJson(it) }.getOrNull() }
            ?: HomeLayout.DEFAULT

    private fun save(updated: HomeLayout) {
        layout = updated
        prefs.edit().putString(KEY_LAYOUT, updated.toJson()).apply()
        render()
    }

    private fun isInstalled(pkg: String): Boolean = apps.any { it.pkg == pkg }

    private fun refreshRecent() {
        if (!RecentApps.isAllowed(this)) return showRecent(emptySet())
        inBackground(worker, { RecentApps.load(this, since = prefs.getLong(KEY_STOPPED, 0)) }, ::showRecent)
    }

    private fun showRecent(found: Set<String>) {
        recent = found
        grid.flatten().forEach { updateBadge(it, recent) }
    }

    /** Stops the listed apps. Android spares what is in active use, such as playing music. */
    private fun stopAll() {
        val listed = apps.map(AppEntry::pkg) - SYSTEM_PARTS
        val manager = getSystemService(ActivityManager::class.java)
        inBackground(worker, { listed.forEach(manager::killBackgroundProcesses) }) {
            prefs.edit().putLong(KEY_STOPPED, System.currentTimeMillis()).apply()
            toast(R.string.stopped_all)
            refreshRecent()
        }
    }

    // endregion

    // region Rendering

    private fun show(target: Screen) {
        screen = target
        navHome.isSelected = target == Screen.HOME
        navApps.isSelected = target == Screen.APPS
        render()
        scroll.scrollTo(0, 0)
    }

    /** Rebuilds the rows, keeping focus on the tile or position that had it. */
    private fun render() {
        val focused = currentFocus?.takeIf { f -> grid.any { f in it } }
        val row = grid.indexOfFirst { focused in it }
        val column = grid.getOrNull(row)?.indexOf(focused) ?: 0

        rowsView.removeAllViews()
        grid.clear()
        if (screen == Screen.HOME) renderHome() else renderApps()

        if (focused != null) {
            val restored = grid.flatten().firstOrNull { it.tag == focused.tag }
                ?: grid.getOrNull(row.coerceAtMost(grid.lastIndex))?.let { it[column.coerceAtMost(it.lastIndex)] }
                ?: navHome
            restored.requestFocus()
        }
    }

    private fun renderHome() {
        val byPkg = apps.associateBy(AppEntry::pkg)
        for (row in layout.rows) {
            val entries = row.apps.mapNotNull(byPkg::get)
            if (entries.isEmpty()) continue
            addTitle(row.name)
            addRow(entries.map(::tile))
        }
        if (grid.isEmpty() && apps.isNotEmpty()) addTitle(getString(R.string.home_empty))
    }

    private fun renderApps() {
        val pinned = PINNED.mapNotNull { pkg -> apps.find { it.pkg == pkg } }
        (pinned + (apps - pinned.toSet())).chunked(COLUMNS).forEach { addRow(it.map(::tile)) }

        val system = mutableListOf(
            button(R.string.all_apps) { screens.openAllApps() },
            button(R.string.stop_all) { stopAll() },
        )
        if (!RecentApps.isAllowed(this)) system += button(R.string.allow_usage) { screens.openUsageAccess() }
        addRow(system)
        rowsView.addDivider(side = 48, top = 4, bottom = 4)

        val power = listOf(
            button(R.string.shut_down) { confirm(getString(R.string.shut_down_confirm)) { adb.shutDown() } },
            button(R.string.reboot) { confirm(getString(R.string.reboot_confirm)) { adb.reboot() } },
            button(R.string.suspend) { adb.suspendTv() },
        )
        val launcher = mutableListOf(button(R.string.open_old_launcher) { openOldLauncher() })
        if (!HomeSwitch.isEnabled(this)) launcher += button(R.string.use_as_home) { becomeHome() }
        addRow(power, launcher)
    }

    private fun addTitle(text: String) {
        rowsView.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(getColor(SharedR.color.text_dim))
            setPadding(dp(48), dp(4), dp(48), 0)
        })
    }

    /** Adds a row; [right] goes to the far right edge. */
    private fun addRow(views: List<View>, right: List<View> = emptyList()) {
        val strip = LinearLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(48), dp(8), dp(32), dp(12))
        }
        views.forEach(strip::addView)
        if (right.isNotEmpty()) {
            strip.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
            right.forEach(strip::addView)
        }
        val scroller = HorizontalScrollView(this).apply {
            isFocusable = false
            isHorizontalScrollBarEnabled = false
            isFillViewport = true // lets the gap before [right] stretch
            clipChildren = false
            addView(strip)
        }
        rowsView.addView(
            scroller,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        grid += views + right
    }

    private fun tile(app: AppEntry): View = createTile(rowsView, app).apply {
        isActivated = app.pkg == moving
        updateBadge(this, recent)
        setOnClickListener { screens.start(app.intent) }
        setOnLongClickListener { showAppMenu(app); true }
        setOnKeyListener { view, key, event ->
            if (key == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
                showAppMenu(app)
                true
            } else {
                navigate(view, key, event)
            }
        }
    }

    private fun button(text: Int, onClick: () -> Unit): TextView {
        val view = LayoutInflater.from(this).inflate(R.layout.button, rowsView, false) as TextView
        view.setText(text)
        view.tag = "button:$text"
        view.isSelected = true // filled pill
        view.setOnClickListener { onClick() }
        view.setOnKeyListener(::navigate)
        return view
    }

    private fun nav(id: Int, onClick: () -> Unit): View = findViewById<View>(id).apply {
        setOnClickListener { onClick() }
        setOnKeyListener { _, key, event ->
            val down = key == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN
            if (down && grid.isNotEmpty()) focusFirstTile()
            down && grid.isNotEmpty()
        }
    }

    // endregion

    // region D-pad

    private fun focusFirstTile() {
        grid.firstOrNull()?.firstOrNull()?.requestFocus()
    }

    /** Rows have different lengths, so geometric focus search skips rows. Step exactly instead. */
    private fun navigate(view: View, key: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val row = grid.indexOfFirst { view in it }
        if (row < 0) return false
        val column = grid[row].indexOf(view)
        val target = when (key) {
            KeyEvent.KEYCODE_DPAD_UP ->
                if (row == 0) (if (screen == Screen.HOME) navHome else navApps) else grid[row - 1].nearest(column)
            KeyEvent.KEYCODE_DPAD_DOWN -> grid.getOrNull(row + 1)?.nearest(column)
            KeyEvent.KEYCODE_DPAD_LEFT -> grid[row].getOrNull(column - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> grid[row].getOrNull(column + 1)
            else -> return false
        }
        target?.requestFocus()
        if (row <= 1 && key == KeyEvent.KEYCODE_DPAD_UP) scroll.smoothScrollTo(0, 0) // reveal the first title
        return true
    }

    private fun List<View>.nearest(column: Int): View = this[column.coerceAtMost(lastIndex)]

    private fun handleMoveKey(pkg: String, event: KeyEvent): Boolean? {
        val updated = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> layout.moveInRow(pkg, -1, ::isInstalled)
            KeyEvent.KEYCODE_DPAD_RIGHT -> layout.moveInRow(pkg, 1, ::isInstalled)
            KeyEvent.KEYCODE_DPAD_UP -> layout.moveToRow(pkg, -1, ::isInstalled)
            KeyEvent.KEYCODE_DPAD_DOWN -> layout.moveToRow(pkg, 1, ::isInstalled)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                // Finish on release so the press does not also open the app or go back.
                if (event.action == KeyEvent.ACTION_UP) {
                    moving = null
                    render()
                }
                return true
            }
            else -> return null
        }
        if (event.action == KeyEvent.ACTION_DOWN) save(updated)
        return true
    }

    // endregion

    // region Menus

    private fun showAppMenu(app: AppEntry) {
        val items = mutableListOf(getString(R.string.open) to { screens.start(app.intent) })
        if (app.pkg in layout) {
            if (screen == Screen.HOME) {
                items += getString(R.string.move) to { startMoving(app) }
                items += getString(R.string.edit_row) to { showRowMenu(layout.rowOf(app.pkg)) }
            }
            items += getString(R.string.remove_from_home) to { save(layout.remove(app.pkg)) }
        } else {
            items += getString(R.string.add_to_home) to { showRowPicker(app) }
        }
        items += getString(R.string.info) to { screens.openInfo(app) }
        items += getString(R.string.force_stop) to { adb.forceStop(app) }
        if (!app.isSystem) items += getString(R.string.uninstall) to { screens.uninstall(app) }
        showMenu(app.label, items)
    }

    private fun startMoving(app: AppEntry) {
        moving = app.pkg
        render()
        grid.flatten().firstOrNull { it.tag == app.pkg }?.requestFocus()
        toast(R.string.move_hint)
    }

    private fun showRowPicker(app: AppEntry) {
        val rows = layout.rows.mapIndexed { index, row -> row.name to { save(layout.add(app.pkg, index)) } }
        val newRow = getString(R.string.new_row) to {
            askName(R.string.row_name, "") { save(layout.addToNewRow(app.pkg, it)) }
        }
        showMenu(getString(R.string.choose_row), rows + newRow)
    }

    private fun showRowMenu(row: Int) {
        val name = layout.rows[row].name
        showMenu(
            name,
            listOf(
                getString(R.string.rename) to { askName(R.string.row_name, name) { save(layout.renameRow(row, it)) } },
                getString(R.string.move_row_up) to { save(layout.moveRow(row, -1, ::isInstalled)) },
                getString(R.string.move_row_down) to { save(layout.moveRow(row, 1, ::isInstalled)) },
                getString(R.string.delete_row) to { save(layout.deleteRow(row)) },
            ),
        )
    }

    private fun becomeHome() {
        HomeSwitch.enable(this)
        screens.start(HomeSwitch.homeIntent) // Android asks which launcher to use
    }

    private fun openOldLauncher() {
        val old = HomeSwitch.oldLauncher(this) ?: return toast(R.string.not_available)
        if (!HomeSwitch.isEnabled(this)) return screens.start(old)
        showMenu(
            getString(R.string.open_old_launcher),
            listOf(
                getString(R.string.open_once) to { screens.start(old) },
                getString(R.string.restore_home) to {
                    HomeSwitch.disable(this)
                    screens.start(old)
                },
            ),
        )
    }

    // endregion

    private companion object {
        const val KEY_LAYOUT = "layout"
        const val KEY_STOPPED = "stopped_at"
        const val REQUEST_ACCOUNT = 1
        const val COLUMNS = 6

        /** First on the Apps screen, in this order. */
        val PINNED = listOf(PLAY_STORE, F_DROID)
    }
}
