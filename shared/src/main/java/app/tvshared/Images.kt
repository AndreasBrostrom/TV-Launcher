package app.tvshared

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface

/** Turns a photo the way its camera was held. */
fun Bitmap.upright(exif: ExifInterface?): Bitmap {
    val degrees = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)
}
