package app.tvfiles

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import app.tvshared.dp
import app.tvshared.inBackground
import java.io.File
import java.util.concurrent.Executors

/** Shows the images in Pictures/Background and returns the chosen one, readable by the caller only. */
class BackgroundPicker : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val adapter = Thumbnails()
    private lateinit var gridView: GridView
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background)
        gridView = findViewById(R.id.grid)
        messageView = findViewById(R.id.message)
        gridView.adapter = adapter
        gridView.setOnItemClickListener { _, _, position, _ -> choose(adapter.images[position]) }
        if (hasAccess()) load() else requestAccess()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun hasAccess() =
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() = requestPermissions(
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_ACCESS,
    )

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        if (hasAccess()) load() else showMessage(R.string.cannot_read)
    }

    private fun load() = inBackground(worker, ::findImages) { images ->
        adapter.images = images.orEmpty()
        adapter.notifyDataSetChanged()
        when {
            images == null -> showMessage(R.string.cannot_read)
            images.isEmpty() -> showMessage(R.string.no_backgrounds)
            else -> gridView.requestFocus()
        }
    }

    /** Creates the folder if needed, so it is there to put images in. Null if unreadable. */
    private fun findImages(): List<File>? {
        val folder = Places.backgrounds()
        folder.mkdirs()
        return Disk.list(folder)?.filter { !it.isFolder && Images.isImage(it.file) }?.map(Entry::file)
    }

    private fun choose(image: File) {
        setResult(
            RESULT_OK,
            Intent().setData(FileProvider.uriFor(image)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        finish()
    }

    private fun showMessage(text: Int) {
        messageView.setText(text)
        messageView.visibility = View.VISIBLE
    }

    private inner class Thumbnails : BaseAdapter() {
        var images: List<File> = emptyList()

        private val requested = HashSet<File>()
        private val unreadable = HashSet<File>()

        /** Far more than fits on screen at once, so scrolling back reloads what was dropped. */
        private val cache = object : LruCache<File, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(key: File, value: Bitmap) = value.byteCount
        }

        override fun getCount() = images.size
        override fun getItem(position: Int) = images[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val view = recycled ?: LayoutInflater.from(parent.context).inflate(R.layout.thumbnail, parent, false)
            val file = images[position]
            val bitmap = cache.get(file)
            view.contentDescription = file.name
            view.findViewById<ImageView>(R.id.image).setImageBitmap(bitmap)
            view.findViewById<TextView>(R.id.name).text = file.name
            if (bitmap == null) request(file)
            return view
        }

        private fun request(file: File) {
            if (file in unreadable || !requested.add(file)) return
            inBackground(worker, { thumbnail(file) }) { bitmap ->
                requested -= file
                if (bitmap != null) cache.put(file, bitmap) else unreadable += file
                notifyDataSetChanged()
            }
        }

        private fun thumbnail(file: File): Bitmap? {
            val width = dp(THUMBNAIL_WIDTH)
            val height = dp(THUMBNAIL_HEIGHT)
            return runCatching {
                Images.load(file, width, height)?.let { ThumbnailUtils.extractThumbnail(it, width, height) }
            }.getOrNull()
        }
    }

    private companion object {
        const val REQUEST_ACCESS = 1
        const val THUMBNAIL_WIDTH = 204
        const val THUMBNAIL_HEIGHT = 115
        const val CACHE_BYTES = 16 * 1024 * 1024
    }
}
