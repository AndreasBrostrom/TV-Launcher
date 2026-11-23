package app.tvfiles

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.tvshared.addDivider
import app.tvshared.askName
import app.tvshared.confirm
import app.tvshared.dp
import app.tvshared.inBackground
import app.tvshared.showMenu
import app.tvshared.toast
import java.io.File
import java.util.concurrent.Executors
import app.tvshared.R as SharedR

class FilesActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val imageWorker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = TreeAdapter()

    private lateinit var placesView: LinearLayout
    private lateinit var pathView: TextView
    private lateinit var treeView: ListView
    private lateinit var messageView: TextView
    private lateinit var previewView: ImageView
    private lateinit var fullView: ImageView

    private var places: List<Place> = emptyList()
    private lateinit var place: Place
    private var tree = FileTree(File("/"))
    private val rows get() = adapter.rows

    /** The image each view should end up showing; late results for others are dropped. */
    private var previewFile: File? = null
    private var fullFile: File? = null
    private val updatePreview = Runnable { showPreview() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        placesView = findViewById(R.id.places)
        pathView = findViewById(R.id.path)
        treeView = findViewById(R.id.tree)
        messageView = findViewById(R.id.message)
        previewView = findViewById(R.id.preview)
        fullView = findViewById(R.id.full)
        treeView.adapter = adapter
        treeView.setOnItemClickListener { _, _, position, _ -> activate(rows[position]) }
        treeView.setOnItemLongClickListener { _, _, position, _ -> showFileMenu(rows[position].entry); true }
        treeView.setOnKeyListener { _, key, event -> event.action == KeyEvent.ACTION_DOWN && onTreeKey(key) }
        treeView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = schedulePreview()
            override fun onNothingSelected(parent: AdapterView<*>?) = schedulePreview()
        }
        treeView.setOnFocusChangeListener { _, _ -> schedulePreview() }
        place = Places.home(this)
        showPlaces()
        if (hasAccess()) openPlace(place) else requestAccess()
    }

    override fun onRestart() {
        super.onRestart()
        showPlaces() // drives may have come or gone
        when {
            !hasAccess() -> showMessage(R.string.need_access)
            place !in places -> openPlace(places.first())
            else -> reload()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updatePreview)
        worker.shutdownNow()
        imageWorker.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Still the only back hook on API 26")
    override fun onBackPressed() {
        when {
            treeView.hasFocus() -> leaveTree()
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    // region Storage access

    private fun hasAccess() = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

    private fun requestAccess() = requestPermissions(
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_ACCESS,
    )

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        if (!hasAccess()) return showMessage(R.string.need_access)
        showPlaces()
        openPlace(places.first())
    }

    // endregion

    // region Places

    private fun showPlaces() {
        val hadFocus = placesView.hasFocus()
        val storage = listOf(Places.home(this), Places.drives(this)) + Places.otherStorage(this)
        val shortcuts = Places.shortcuts(this)
        places = storage + shortcuts
        placesView.removeAllViews()
        placesView.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 20f
            setTextColor(getColor(SharedR.color.text))
            setPadding(dp(16), dp(4), 0, dp(12))
        })
        storage.forEach(::addPlace)
        placesView.addDivider(side = 16, top = 8, bottom = 12)
        shortcuts.forEach(::addPlace)
        placesView.addDivider(side = 16, top = 8, bottom = 12)
        markPlace()
        if (hadFocus) focusPlace()
    }

    private fun addPlace(place: Place) {
        val view = LayoutInflater.from(this).inflate(R.layout.place, placesView, false) as TextView
        view.text = place.label
        view.tag = place
        view.setOnClickListener { if (hasAccess()) openPlace(place) else requestAccess() }
        view.setOnKeyListener { _, key, event ->
            val right = key == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN
            right && rows.isNotEmpty() && treeView.requestFocus()
        }
        placesView.addView(view)
    }

    private fun markPlace() {
        for (i in 0 until placesView.childCount) {
            placesView.getChildAt(i).let { it.isSelected = it.tag == place }
        }
    }

    private fun focusPlace() {
        (placesView.findViewWithTag<View>(place) ?: placesView.findViewWithTag(places.first()))?.requestFocus()
    }

    private fun openPlace(target: Place) {
        place = target
        markPlace()
        if (target.folder == null) showDrives() else openRoot(target.folder)
    }

    private fun openRoot(folder: File) {
        tree = FileTree(folder)
        pathView.text = folder.path
        adapter.rows = emptyList()
        expand(folder, ::focusTree)
    }

    private fun focusTree() {
        treeView.setSelection(0)
        if (rows.isEmpty()) focusPlace() else treeView.requestFocus()
    }

    /** Steps out of the tree: from an opened drive to the drives, otherwise to the places. */
    private fun leaveTree() {
        if (place.folder == null && tree.root != DRIVES) showDrives() else focusPlace()
    }

    // endregion

    // region Drives

    private fun showDrives() {
        val drives = FileTree(DRIVES)
        tree = drives
        pathView.text = place.label
        adapter.rows = emptyList()
        inBackground(worker, { Places.listDrives(this) }) { found ->
            if (drives !== tree) return@inBackground // another place was opened meanwhile
            drives.put(DRIVES, found.map { Entry(it.folder ?: File(it.label), isFolder = false, size = 0, drive = it) })
            refreshRows()
            focusTree()
        }
    }

    private fun openDrive(drive: Drive) {
        inBackground(worker, { drive.folder?.let(Disk::list) }) { entries ->
            if (tree.root != DRIVES) return@inBackground
            if (entries == null || drive.folder == null) toast(R.string.cannot_open) else openRoot(drive.folder)
        }
    }

    // endregion

    // region Tree

    private fun activate(row: Row) = when {
        row.entry.drive != null -> openDrive(row.entry.drive)
        !row.entry.isFolder -> open(row.entry.file)
        row.expanded -> collapse(row.entry.file)
        else -> expand(row.entry.file)
    }

    private fun onTreeKey(key: Int): Boolean {
        val row = rows.getOrNull(treeView.selectedItemPosition) ?: return false
        val file = row.entry.file
        when (key) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                row.entry.drive != null -> openDrive(row.entry.drive)
                row.entry.isFolder && !row.expanded -> expand(file)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                row.expanded -> collapse(file)
                row.depth > 0 -> select(file.parentFile)
                else -> leaveTree()
            }
            KeyEvent.KEYCODE_MENU -> showFileMenu(row.entry)
            else -> return false
        }
        return true
    }

    private fun expand(folder: File, then: () -> Unit = {}) {
        val current = tree
        inBackground(worker, { Disk.list(folder) }) { entries ->
            if (current !== tree) return@inBackground // another place was opened meanwhile
            if (entries == null) toast(R.string.cannot_read) else tree.put(folder, entries)
            refreshRows()
            then()
        }
    }

    private fun collapse(folder: File) {
        tree.close(folder)
        refreshRows()
    }

    /** Re-reads every open folder, keeping the selection. */
    private fun reload(selectAfter: File? = null) {
        if (tree.root == DRIVES) return showDrives()
        val current = tree
        val selected = selectAfter ?: rows.getOrNull(treeView.selectedItemPosition)?.entry?.file
        inBackground(worker, { current.openFolders().associateWith(Disk::list) }) { listings ->
            if (current !== tree) return@inBackground
            for ((folder, entries) in listings) {
                if (entries == null) tree.close(folder) else tree.put(folder, entries)
            }
            refreshRows()
            if (rows.isEmpty()) focusPlace() else selected?.let(::select)
        }
    }

    private fun refreshRows() {
        adapter.rows = tree.rows()
        if (rows.isEmpty()) showMessage(R.string.empty) else messageView.visibility = View.GONE
        schedulePreview() // the same position may now be another file
    }

    private fun select(file: File?) {
        val index = rows.indexOfFirst { it.entry.file == file }
        if (index >= 0) treeView.setSelectionFromTop(index, (treeView.selectedView?.top ?: 0).coerceAtLeast(0))
    }

    // endregion

    // region Images

    /** Waits a moment, so holding an arrow key does not decode every image on the way. */
    private fun schedulePreview() {
        handler.removeCallbacks(updatePreview)
        handler.postDelayed(updatePreview, PREVIEW_DELAY_MS)
    }

    private fun showPreview() {
        val file = rows.getOrNull(treeView.selectedItemPosition)?.entry
            ?.takeIf { treeView.hasFocus() && isImage(it) }?.file
        if (file == previewFile) return
        previewFile = file
        if (file == null) return hide(previewView)
        loadInto(previewView, file, dp(PREVIEW_SIZE), dp(PREVIEW_SIZE)) { previewFile == file }
    }

    private fun isImage(entry: Entry) = !entry.isFolder && entry.drive == null && Images.isImage(entry.file)

    private fun showFull(file: File) {
        fullFile = file
        val screen = resources.displayMetrics
        loadInto(fullView, file, screen.widthPixels, screen.heightPixels) { fullFile == file }
    }

    private fun closeFull() {
        val shown = fullFile
        fullFile = null
        hide(fullView)
        select(shown)
    }

    private fun loadInto(view: ImageView, file: File, width: Int, height: Int, wanted: () -> Boolean) {
        inBackground(imageWorker, { Images.load(file, width, height) }) { bitmap ->
            if (!wanted()) return@inBackground
            when {
                bitmap != null -> {
                    view.setImageBitmap(bitmap)
                    view.visibility = View.VISIBLE
                }
                view === fullView -> {
                    closeFull()
                    openWithApp(file) // not something we can show
                }
                else -> hide(view)
            }
        }
    }

    private fun hide(view: ImageView) {
        view.visibility = View.GONE
        view.setImageDrawable(null) // frees the bitmap
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val shown = fullFile ?: return super.dispatchKeyEvent(event)
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> 1
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> 0
            else -> return super.dispatchKeyEvent(event)
        }
        if (step == 0) {
            if (event.action == KeyEvent.ACTION_UP) closeFull()
        } else if (event.action == KeyEvent.ACTION_DOWN) {
            val images = rows.map(Row::entry).filter(::isImage).map(Entry::file)
            images.getOrNull(images.indexOf(shown) + step)?.let(::showFull)
        }
        return true
    }

    // endregion

    // region Actions

    private fun open(file: File) = if (Images.isImage(file)) showFull(file) else openWithApp(file)

    private fun openWithApp(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.uriFor(file), FileProvider.mimeType(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (runCatching { startActivity(intent) }.isFailure) toast(R.string.no_app)
    }

    private fun showFileMenu(entry: Entry) {
        if (entry.drive != null) return
        val file = entry.file
        val parent = file.parentFile ?: return
        val target = if (entry.isFolder) file else parent
        showMenu(
            file.name,
            listOf(
                getString(R.string.open) to { if (entry.isFolder) expand(file) else open(file) },
                getString(R.string.new_folder) to {
                    askName(R.string.new_folder, "") { name ->
                        change(File(target, name)) { Disk.newFolder(target, name) }
                    }
                },
                getString(R.string.rename) to {
                    askName(R.string.rename, file.name) { name ->
                        change(File(parent, name)) { Disk.rename(file, name) }
                    }
                },
                getString(R.string.delete) to { confirmDelete(entry) },
            ),
        )
    }

    private fun confirmDelete(entry: Entry) {
        val question = if (entry.isFolder) R.string.delete_folder_confirm else R.string.delete_confirm
        confirm(getString(question, entry.file.name), yes = R.string.delete) {
            change(null) { Disk.delete(entry.file) }
        }
    }

    private fun change(selectAfter: File?, action: () -> Boolean) = inBackground(worker, action) { done ->
        if (!done) toast(R.string.failed)
        reload(selectAfter.takeIf { done })
    }

    // endregion

    private fun showMessage(text: Int) {
        messageView.setText(text)
        messageView.visibility = View.VISIBLE
        focusPlace()
    }

    private companion object {
        /** Stands in for a root folder while the drives are listed. */
        val DRIVES = File("drives")

        const val REQUEST_ACCESS = 1
        const val PREVIEW_DELAY_MS = 150L
        const val PREVIEW_SIZE = 240
    }
}
