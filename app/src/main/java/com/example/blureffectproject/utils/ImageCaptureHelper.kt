package com.example.blureffectproject.utils
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast

class ImageCaptureHelper(private val context: Context) {
    fun captureAndSaveImage(imageView: ImageView, dualCircleButtonView: View, linerButtonView: View, containerView: View) {
        // Get the actual bitmap displayed inside ImageView
        val drawable = imageView.drawable as? BitmapDrawable
        val originalBitmap = drawable?.bitmap ?: return // Exit if no image

        // Get the ImageView's actual displayed size
        val imageViewWidth = imageView.width
        val imageViewHeight = imageView.height

        // Get the scale of the displayed bitmap inside ImageView
        val imageRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val viewRatio = imageViewWidth.toFloat() / imageViewHeight.toFloat()

        val actualWidth: Int
        val actualHeight: Int

        if (imageRatio > viewRatio) {
            actualWidth = imageViewWidth
            actualHeight = (imageViewWidth / imageRatio).toInt()
        } else {
            actualHeight = imageViewHeight
            actualWidth = (imageViewHeight * imageRatio).toInt()
        }

        // Resize the custom views to match the actual displayed image size
        dualCircleButtonView.layoutParams.width = actualWidth
        dualCircleButtonView.layoutParams.height = actualHeight
        linerButtonView.layoutParams.width = actualWidth
        linerButtonView.layoutParams.height = actualHeight

        // Apply changes to views
        dualCircleButtonView.requestLayout()
        linerButtonView.requestLayout()

        // Save original positions to restore after capture
        val originalX = containerView.x
        val originalY = containerView.y

        // Ensure all views are measured and drawn correctly
        containerView.measure(
            View.MeasureSpec.makeMeasureSpec(actualWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(actualHeight, View.MeasureSpec.EXACTLY)
        )
        containerView.layout(0, 0, actualWidth, actualHeight)

        // Create a new Bitmap with the actual displayed image size
        val bitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw the full container (now matching the ImageView dimensions)
        containerView.draw(canvas)

        // Save the final correctly captured image
        saveBitmapToGallery(bitmap)

        // Restore original positions after capture
        containerView.x = originalX
        containerView.y = originalY
        containerView.requestLayout()
    }
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "CapturedImage_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/BlurEffect"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver: ContentResolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
    }
}
