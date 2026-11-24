package app.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import app.tvshared.upright
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt
import app.tvshared.R as SharedR

/**
 * The picture behind the launcher. We keep our own copy, cropped to the screen, so it needs
 * no storage access and outlives the original. Loading and saving are slow.
 */
object Background {
    const val NONE = 0L

    /** Changes when the background does. */
    fun version(context: Context): Long = file(context).lastModified()

    fun remove(context: Context) {
        file(context).delete()
    }

    /** False if [image] cannot be read; the current background then stays. */
    fun save(context: Context, image: Uri): Boolean = runCatching {
        val screen = context.resources.displayMetrics
        val source = checkNotNull(decode(context, image, screen.widthPixels, screen.heightPixels))
        val cropped = ThumbnailUtils.extractThumbnail(source, screen.widthPixels, screen.heightPixels)
        // Write then rename, so a crash never leaves half a picture behind.
        val unfinished = File(context.noBackupFilesDir, "background.tmp")
        check(unfinished.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) })
        unfinished.renameTo(file(context))
    }.getOrDefault(false) // also covers running out of memory

    /** The picture, darkened so text stays readable. Null, meaning plain grey, if there is none. */
    fun load(context: Context): Drawable? = runCatching {
        val bitmap = BitmapFactory.decodeFile(file(context).path) ?: return null
        val shade = context.getColor(SharedR.color.background) and 0xFFFFFF or (shadeAlpha(brightness(bitmap)) shl 24)
        LayerDrawable(arrayOf(BitmapDrawable(context.resources, bitmap), ColorDrawable(shade)))
    }.getOrNull()

    /** How strongly to darken a picture of this [brightness], 0 to 1. */
    internal fun shadeAlpha(brightness: Float): Int = (255 * (0.5f + 0.35f * brightness)).roundToInt()

    /** The largest power of two that keeps the image big enough to fill the screen. */
    internal fun sampleSize(imageWidth: Int, imageHeight: Int, width: Int, height: Int): Int {
        val scale = minOf(imageWidth.toFloat() / width, imageHeight.toFloat() / height)
        var size = 1
        while (size * 2 <= scale) size *= 2
        return size
    }

    private fun decode(context: Context, image: Uri, width: Int, height: Int): Bitmap? {
        fun <T> read(block: (InputStream) -> T): T? =
            context.contentResolver.openInputStream(image)?.use(block)

        val exif = runCatching { read(::ExifInterface) }.getOrNull()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, width, height)
        options.inJustDecodeBounds = false
        return read { BitmapFactory.decodeStream(it, null, options) }?.upright(exif)
    }

    private fun brightness(bitmap: Bitmap): Float {
        val side = 16
        val pixels = IntArray(side * side)
        Bitmap.createScaledBitmap(bitmap, side, side, true).getPixels(pixels, 0, side, 0, 0, side, side)
        return pixels.map(Color::luminance).average().toFloat()
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, "background.jpg")

    private const val QUALITY = 92
}
