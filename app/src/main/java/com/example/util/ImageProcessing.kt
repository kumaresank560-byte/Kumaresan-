package com.example.util

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageProcessing {

    /**
     * Warps the perspective of a source bitmap using 4 source corners to a standard rectangular output.
     * Uses Android's native Matrix.setPolyToPoly which provides fast perspective projection.
     */
    fun warpPerspective(
        original: Bitmap,
        topLeft: PointF,
        topRight: PointF,
        bottomRight: PointF,
        bottomLeft: PointF
    ): Bitmap {
        val width = original.width.toFloat()
        val height = original.height.toFloat()

        // Source coordinates (relative 0f..1f converted to actual pixel coordinates)
        val srcPoints = floatArrayOf(
            topLeft.x * width, topLeft.y * height,
            topRight.x * width, topRight.y * height,
            bottomRight.x * width, bottomRight.y * height,
            bottomLeft.x * width, bottomLeft.y * height
        )

        // Determine destination dimensions (e.g. aspect ratio from cropped boundaries)
        val targetWidth = maxOf(
            distance(topLeft.x * width, topLeft.y * height, topRight.x * width, topRight.y * height),
            distance(bottomLeft.x * width, bottomLeft.y * height, bottomRight.x * width, bottomRight.y * height)
        ).coerceIn(100f, 4000f)

        val targetHeight = maxOf(
            distance(topLeft.x * width, topLeft.y * height, bottomLeft.x * width, bottomLeft.y * height),
            distance(topRight.x * width, topRight.y * height, bottomRight.x * width, bottomRight.y * height)
        ).coerceIn(100f, 4000f)

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth, 0f,
            targetWidth, targetHeight,
            0f, targetHeight
        )

        val matrix = Matrix()
        // Compute perspective projection mapping 4 source points to 4 destination points
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!success) {
            // Fallback: simple copy if perspective matrix computation fails
            return original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
        }

        val warped = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(original, matrix, paint)

        return warped
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Rotates a bitmap by specific degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Applies standard grayscale transformation.
     */
    fun applyGrayscaleFilter(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Boosts contrast, brightness, and saturates colors slightly to achieve the iconic "Magic Color"
     * effect where paper backgrounds are whitened and text/colors are enhanced.
     */
    fun applyMagicColorFilter(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Dynamic brightness/contrast enhancement matrix
        val contrast = 1.4f
        val brightness = 20f // Translate channel values upwards to whiten grey areas
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Applies an ultra high contrast binary (Black & White) threshold filter to replicate photocopy styles.
     */
    fun applyBWFilter(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Binary-like color matrix: maps average grayscale input to steep high/low levels
        val cm = ColorMatrix(floatArrayOf(
            5f, 5f, 5f, 0f, -1100f,
            5f, 5f, 5f, 0f, -1100f,
            5f, 5f, 5f, 0f, -1100f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Applies filter based on filter type string.
     */
    fun applyFilter(src: Bitmap, filterType: String): Bitmap {
        return when (filterType.uppercase()) {
            "GRAYSCALE" -> applyGrayscaleFilter(src)
            "MAGIC" -> applyMagicColorFilter(src)
            "BW" -> applyBWFilter(src)
            else -> src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * Saves a bitmap to the application's local file storage.
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, folderName: String = "scanned_pages"): String {
        val directory = File(context.filesDir, folderName).apply { mkdirs() }
        val file = File(directory, "IMG_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }
}
