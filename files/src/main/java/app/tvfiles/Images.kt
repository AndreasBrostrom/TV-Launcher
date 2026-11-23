package app.tvfiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

object Images {
    fun isImage(file: File): Boolean =
        FileProvider.mimeType(file).let { it.startsWith("image/") && it != "image/svg+xml" }

    /** Decodes [file] to roughly the given size, upright. Null if it is not a readable image. Slow. */
    fun load(file: File, width: Int, height: Int): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, width, height)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.path, options)?.let { upright(it, file) }
    }.getOrNull() // also covers running out of memory

    /** The largest power of two that keeps the image at least as big as it will be shown. */
    internal fun sampleSize(imageWidth: Int, imageHeight: Int, width: Int, height: Int): Int {
        val scale = maxOf(imageWidth.toFloat() / width, imageHeight.toFloat() / height)
        var size = 1
        while (size * 2 <= scale) size *= 2
        return size
    }

    private fun upright(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrNull()
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
