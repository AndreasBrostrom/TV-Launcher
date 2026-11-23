package app.tvfiles

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/** A button on the left. Without a [folder] it is the list of drives. */
data class Place(val label: String, val folder: File?)

/** [folder] is null if Android does not say where the drive is. */
class Drive(val label: String, val folder: File?, val free: Long, val total: Long)

object Places {
    fun home(context: Context) = Place(context.getString(R.string.home), Environment.getExternalStorageDirectory())

    fun drives(context: Context) = Place(context.getString(R.string.drives), null)

    fun shortcuts(context: Context): List<Place> = listOf(
        R.string.download to Environment.DIRECTORY_DOWNLOADS,
        R.string.movies to Environment.DIRECTORY_MOVIES,
        R.string.music to Environment.DIRECTORY_MUSIC,
        R.string.pictures to Environment.DIRECTORY_PICTURES,
    ).map { (label, folder) ->
        Place(context.getString(label), Environment.getExternalStoragePublicDirectory(folder))
    }

    /** SD cards and USB drives, if any are plugged in and readable. */
    fun otherStorage(context: Context): List<Place> =
        removable(context).filter { it.folder()?.canRead() == true }.map { Place(it.label(context), it.folder()) }

    /** Every drive, readable or not. Slow: call off the main thread. */
    fun listDrives(context: Context): List<Drive> {
        // Apps may not look into the internal disk itself, only its shared part.
        val shared = Environment.getExternalStorageDirectory()
        val internal = drive(context.getString(R.string.internal), shared, Environment.getDataDirectory())
        val system = Environment.getRootDirectory().let { drive(context.getString(R.string.system), it, it) }
        val others = removable(context).map { volume -> volume.folder().let { drive(volume.label(context), it, it) } }
        return listOf(internal, system) + others
    }

    private fun drive(label: String, opens: File?, measured: File?) =
        Drive(label, opens, measured?.usableSpace ?: 0, measured?.totalSpace ?: 0)

    private fun removable(context: Context): List<StorageVolume> =
        context.getSystemService(StorageManager::class.java).storageVolumes
            .filter { !it.isPrimary && it.state in MOUNTED }

    private val MOUNTED = setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)

    private fun StorageVolume.label(context: Context): String =
        getDescription(context) ?: context.getString(R.string.storage)

    private fun StorageVolume.folder(): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) directory
        // Not public before Android 11, but present and allowed on this TV's Android 8.
        else runCatching { File(javaClass.getMethod("getPath").invoke(this) as String) }.getOrNull()
}
