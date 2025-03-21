package com.example.blureffectproject.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap

object BlurUtils {
    fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val inputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val outputBitmap = createBitmap(width, height)

        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, inputBitmap)
        val output = Allocation.createFromBitmap(rs, outputBitmap)

        val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        blurScript.setRadius(radius.coerceIn(1f, 25f))
        blurScript.setInput(input)
        blurScript.forEach(output)

        output.copyTo(outputBitmap)
        rs.destroy()

        return outputBitmap
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyRadialBlur(context: Context, bitmap: Bitmap, centerX: Float, centerY: Float, blurAmount: Float, blurPasses: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val outputBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        for (i in 1..blurPasses) {
            val scale = 1f + (i * blurAmount * 0.25f)  // More controlled scaling
            val alpha = ((255 * (1 - (i.toFloat() / blurPasses))) * 0.8).toInt().coerceIn(0, 255) // Smooth fade effect

            val matrix = Matrix().apply {
                postScale(scale, scale, centerX, centerY)
            }

            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

            // Apply Gaussian Blur instead of simple linear blur
            val blurredBitmap = applyGaussianBlur(context, scaledBitmap, 15f)

            val dx = (width - blurredBitmap.width) / 2f
            val dy = (height - blurredBitmap.height) / 2f

            paint.alpha = alpha
            canvas.drawBitmap(blurredBitmap, dx, dy, paint)

            // Properly recycle bitmaps to free memory
            scaledBitmap.recycle()
            blurredBitmap.recycle()
        }

        return outputBitmap
    }
    fun createZoomBlur(context: Context, bitmap: Bitmap, centerX: Float, centerY: Float, zoomAmount: Float, blurPasses: Int): Bitmap {
        if (bitmap.isRecycled) {
            throw IllegalArgumentException("Original bitmap is already recycled!")
        }

        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Draw original image first
//        canvas.drawBitmap(bitmap, 0f, 0f, null)

        for (i in 1..blurPasses) {
            val alpha = ((255 / blurPasses.toFloat()) * 0.8).toInt().coerceIn(0, 255)  // Smooth fade
            paint.alpha = alpha

            val scale = 1f + zoomAmount * (i.toFloat() / blurPasses)

            val matrix = Matrix().apply {
                postScale(scale, scale, centerX, centerY)
            }

            val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

            val dx = (width - scaledBitmap.width) / 2f
            val dy = (height - scaledBitmap.height) / 2f

            // Apply extra blur (optional, replace `applyLinearBlur` with your implementation)
            val blurredBitmap = applyLinearBlur(context, scaledBitmap)

            canvas.drawBitmap(blurredBitmap, dx, dy, paint)

            // Clean up bitmaps to avoid memory leaks
            scaledBitmap.recycle()
            blurredBitmap.recycle()
        }
        return outputBitmap
    }
    fun applyLinearBlur(context: Context, bitmap: Bitmap): Bitmap {
        val rs = RenderScript.create(context)

        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        script.setRadius(10f)
        script.setInput(input)
        script.forEach(output)

        val outputBitmap = createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        output.copyTo(outputBitmap)

        rs.destroy()

        return outputBitmap
    }
    fun applyLinearBlur(context: Context, bitmap: Bitmap, intensity: Int): Bitmap {
        val rs = RenderScript.create(context)

        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        // Ensure intensity stays within the valid range (0.1f - 25f)
        val blurRadius = (intensity / 40f) * 25f // Convert intensity (0-40) to valid radius (0.1-25)
        script.setRadius(blurRadius.coerceIn(0.1f, 25f))

        script.setInput(input)
        script.forEach(output)

        val outputBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.copyTo(outputBitmap)

        rs.destroy()

        return outputBitmap
    }
    fun applyLinearBlur(context: Context, bitmap: Bitmap, blurRadius: Float): Bitmap {
        val rs = RenderScript.create(context)

        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        // Ensure blur radius is within valid bounds (0 < radius ≤ 25)
        script.setRadius(blurRadius.coerceIn(0.1f, 25f))
        script.setInput(input)
        script.forEach(output)

        val outputBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        output.copyTo(outputBitmap)

        rs.destroy()

        return outputBitmap
    }
    fun applyGaussianBlur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val rs = RenderScript.create(context)

        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        script.setRadius(radius) // Stronger blur for a smoother effect
        script.setInput(input)
        script.forEach(output)

        val outputBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.copyTo(outputBitmap)

        rs.destroy()
        return outputBitmap
    }
}