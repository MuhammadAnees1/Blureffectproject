package com.example.blureffectproject.main.activities

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blureffectproject.R
import com.example.blureffectproject.databinding.ActivityBlurBinding
import com.example.blureffectproject.main.adaptor.BlurAdapter
import com.example.blureffectproject.models.BlurModel
import com.example.blureffectproject.models.BlurType
import com.example.blureffectproject.utils.BlurUtils
import com.example.blureffectproject.utils.BlurUtils.blurBitmap
import com.example.blureffectproject.utils.ImageCaptureHelper
import com.example.blureffectproject.view.DualCircleButtonView
import com.example.blureffectproject.view.LinerButtonView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.time.delay
import kotlin.math.hypot
import kotlin.math.log

class BlurActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlurBinding
    private lateinit var blurAdapter: BlurAdapter
    private var isEraserSelected = false
    private var isBlurOnFace = false
    private lateinit var segmenter: Segmenter
    private var originalBitmap: Bitmap? = null
    private var isEraserActive = false
    private var isBlurPaintActive = false
    private lateinit var mutableBlurredBitmap: Bitmap
    private lateinit var originalBitmapCopy: Bitmap
    private var motionBlurAngle = 0f
    private var blurProgress1 = 10f
    private var currentBlurType: BlurType? = null
    private var lastX = -1f
    private var lastY = -1f
    private val imageCaptureHelper by lazy { ImageCaptureHelper(this) }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlurBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSegmenter()


        val drawable = binding.blurImageView.drawable
        if (drawable == null) {
            Log.e("BlurEffect", "Drawable is null. Make sure image is loaded in blurImageView!")
        } else {
            (drawable as? BitmapDrawable)?.let {
                originalBitmap = it.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                mutableBlurredBitmap = it.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                originalBitmapCopy = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)!!
            }
        }
        // ✅ Set default blur type before SeekBar setup
        currentBlurType = BlurType.NORMAL
        originalBitmap?.let { bitmap ->
            processImageWithSegmentation(bitmap, binding.blurImageView, BlurType.NORMAL, 20f)
            binding.blurImageView.invalidate()
        } ?: Log.e("BlurEffect", "Original bitmap is null!")

        binding.blurSeekBar.max = 40
        binding.blurSeekBar.progress = 10
        binding.blurSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d("TAG", "onProgressChanged: " + progress)

                val blurRadius = progress * 20f / 10f

                Log.d("TAG", "onProgressChanged: " + "called")

                // If neither DualCircleButtonView nor LinerButtonView is present, check if a BlurType is active
                if (currentBlurType == null) {
                    currentBlurType = BlurType.NORMAL
                }
                // Display SeekBar value as 0% to 100%
                binding.blurValueText.text = "${(progress * 100 / 40)}%"

                // Convert SeekBar progress to blur intensity
                val blurProgress = (progress / 100f) * 20
                Log.d("TAG", "onProgressChanged: " + blurRadius)

                originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)?.let { bitmap ->
                    when (currentBlurType) {
                        BlurType.NORMAL -> processImageWithSegmentation(
                            bitmap,
                            binding.blurImageView,
                            BlurType.NORMAL,
                            radialZoomAmount = blurRadius
                        )

                        BlurType.RADIAL -> processImageWithSegmentation(
                            bitmap,
                            binding.blurImageView,
                            BlurType.RADIAL,
                            radialZoomAmount = blurProgress / 100f,
                            radialPasses = 15
                        )

                        BlurType.ZOOM -> processImageWithSegmentation(
                            bitmap,
                            binding.blurImageView,
                            BlurType.ZOOM,
                            radialZoomAmount = blurProgress / 100f,
                            radialPasses = 10
                        )

                        else -> Toast.makeText(
                            this@BlurActivity,
                            "Unsupported blur type!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } ?: Log.e("BlurEffect", "No valid image loaded")


                if (binding.blurContainer.childCount > 0) {
                    for (i in 0 until binding.blurContainer.childCount) {
                        val child = binding.blurContainer.getChildAt(i)

                        if (child is DualCircleButtonView) {
                            // Apply seekbar logic for DualCircleButtonView
                            child.setBlurIntensity(progress.toFloat())
                            return
                        } else if (child is LinerButtonView) {
                            // Apply seekbar logic for LinerButtonView
                            child.updateBlurIntensity(progress)
                            return
                        }
                    }
                }

            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d("TAG", "onStartTrackingTouch: "+"clicked")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val buttonList = listOf(
            BlurModel(R.drawable.bg1, "Blur"),
            BlurModel(R.drawable.bg2, "Circle"),
            BlurModel(R.drawable.bg3, "Linear"),
            BlurModel(R.drawable.bg4, "Radial"),
            BlurModel(R.drawable.bg5, "Motion"),
            BlurModel(R.drawable.bg6, "Zoom"),
        )


        // Store last selected blur effect
        var lastSelectedBlurId: Int? = null

        blurAdapter = BlurAdapter(this, buttonList) { imageResId ->

            if (lastSelectedBlurId == imageResId) {
                Log.d("BlurEffect", "Effect already applied for: $imageResId")
                return@BlurAdapter
            }

            binding.BlurMotionLinearLayout.visibility = View.GONE
            binding.blurLinearLayout.visibility = View.VISIBLE
            binding.dualCircleButton.visibility = View.GONE
            binding.eraser.visibility = View.VISIBLE
            binding.blurPaintButton.visibility = View.VISIBLE
            binding.reset.visibility = View.VISIBLE
            binding.blurSeekBar.progress = 10

            // Clean up old views
            for (i in binding.blurContainer.childCount - 1 downTo 0) {
                val child = binding.blurContainer.getChildAt(i)
                if (child is DualCircleButtonView || child is LinerButtonView) {
                    binding.blurContainer.removeViewAt(i)
                }
            }

            originalBitmap?.let { bitmap ->  // Use originalBitmap instead of getting it from blurImageView

                when (imageResId) {
                    R.drawable.bg1 -> {
                        currentBlurType = BlurType.NORMAL
                        processImageWithSegmentation(
                            bitmap,
                            binding.blurImageView,
                            BlurType.NORMAL,
                            20f
                        )
                    }

                    R.drawable.bg2 -> {
                        val button = DualCircleButtonView(this)
                        binding.blurContainer.addView(button)
                        button.setBitmap(bitmap)
                        binding.eraser.visibility = View.GONE
                        binding.blurPaintButton.visibility = View.GONE
                        binding.reset.visibility = View.GONE

                    }

                    R.drawable.bg3 -> {
                        val button = LinerButtonView(this)
                        binding.blurContainer.addView(button)
                        button.setImageBitmap(bitmap)
                        binding.eraser.visibility = View.GONE
                        binding.reset.visibility = View.GONE
                        binding.blurPaintButton.visibility = View.GONE
                    }

                    R.drawable.bg4 -> {
                        currentBlurType = BlurType.RADIAL
                        processImageWithSegmentation(bitmap, binding.blurImageView, BlurType.RADIAL)
                    }

                    R.drawable.bg5 -> {
                        currentBlurType = BlurType.MOTION
                        processImageWithSegmentation(bitmap, binding.blurImageView, BlurType.MOTION)
                        binding.BlurMotionLinearLayout.visibility = View.VISIBLE
                        binding.blurLinearLayout.visibility = View.GONE
                    }

                    R.drawable.bg6 -> {
                        currentBlurType = BlurType.ZOOM
                        processImageWithSegmentation(bitmap, binding.blurImageView, BlurType.ZOOM)
                    }

                    else -> {
                        currentBlurType = null
                        Toast.makeText(
                            this,
                            "No blur type found for this button",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                lastSelectedBlurId = imageResId
            } ?: Log.e("BlurEffect", "Original bitmap is null!")
        }

        binding.blurRecycleView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.blurRecycleView.adapter = blurAdapter

        binding.eraser.setOnClickListener {
            isEraserActive = true
            isEraserSelected = true
            isBlurPaintActive = false

            updateButtonStates(eraserSelected = true)

            enableEraseTouch()        }

        binding.blurPaintButton.setOnClickListener {
            isBlurPaintActive = !isBlurPaintActive
            isEraserActive = false
            isEraserSelected = false

            Toast.makeText(
                this,
                if (isBlurPaintActive) "Blur Paint Enabled" else "Blur Paint Disabled",
                Toast.LENGTH_SHORT
            ).show()

            if (isBlurPaintActive) {
                if (!::mutableBlurredBitmap.isInitialized) {
                    mutableBlurredBitmap = originalBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                    binding.blurImageView.setImageBitmap(mutableBlurredBitmap)
                }
                enableBlurPaintTouch()
            } else {
                disableTouch()
            }

            updateButtonStates(blurPaintSelected = isBlurPaintActive)
        }

        binding.toggleBlurButton.setOnClickListener {
            isEraserActive = false
            isEraserSelected = false
            isBlurOnFace = !isBlurOnFace

            // Toggle blur and redraw
            binding.dualCircleButton.setBlurMode(isBlurOnFace)

            if (currentBlurType == null) {
                Toast.makeText(this@BlurActivity, "Select a blur type first", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            // Toggle the blur effect between solid lines
            binding.dualLinerButton.setBlurBetweenSolidLines(isBlurOnFace)
            originalBitmap?.let {
                processImageWithSegmentation(it, binding.blurImageView, currentBlurType!!)
            } ?: run {
                Toast.makeText(this@BlurActivity, "No image loaded!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.SaveButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE

            // Block all touch interactions
            binding.blurContainer.setOnTouchListener { _, _ -> true }

            // Ensure the overlay blocks touches
            binding.disableOverlay.apply {
                visibility = View.VISIBLE
                isEnabled = true
                isClickable = true
                isFocusable = true
            }

            // Hide elements
            binding.dualCircleButton.hideCircles()
            binding.dualLinerButton.hideLines()

            // Delay for saving
            Handler(Looper.getMainLooper()).postDelayed({
                captureAndSaveImageViewWithCustomViews()
                binding.progressBar.visibility = View.GONE

                // Re-enable touch interactions
                binding.blurContainer.setOnTouchListener(null)
                binding.disableOverlay.visibility = View.GONE
            }, 3000)
        }

        binding.reset.setOnClickListener {
            isEraserActive = false
            isEraserSelected = false
            isBlurOnFace = false

            originalBitmap?.let {
//                binding.blurImageView.setImageBitmap(it)
                processImageWithSegmentation(it, binding.blurImageView, currentBlurType!!)
                updateButtonStates()
            }
        }

        binding.imagePreview.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val activeToolViews = mutableListOf<View>()
                    var isToolViewActive = false

                    // Check if any DualCircleButtonView or LinerButtonView is visible
                    for (i in 0 until binding.blurContainer.childCount) {
                        val child = binding.blurContainer.getChildAt(i)
                        if ((child is DualCircleButtonView || child is LinerButtonView) && child.visibility == View.VISIBLE) {
                            activeToolViews.add(child)
                            child.visibility = View.GONE  // Hide temporarily
                            isToolViewActive = true
                        }
                    }

                    // Check if any BlurType is active
                    val isBlurTypeActive = when (currentBlurType) {
                        BlurType.NORMAL, BlurType.RADIAL, BlurType.MOTION, BlurType.ZOOM -> true
                        else -> false
                    }

                    // If a tool view or blur type is active, show the original image
                    if (isToolViewActive || isBlurTypeActive) {
                        originalBitmapCopy?.let { binding.blurImageView.setImageBitmap(it) }
                    }

                    // Store hidden views for later restoration
                    binding.imagePreview.tag = activeToolViews
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val hiddenViews = binding.imagePreview.tag as? List<View>

                    if (!hiddenViews.isNullOrEmpty()) {
                        // Restore previously hidden tool views
                        hiddenViews.forEach { it.visibility = View.VISIBLE }
                    } else {
                        // Restore the last modified blurred image
                        mutableBlurredBitmap?.let {
                            binding.blurImageView.setImageBitmap(it)
                        } ?: originalBitmapCopy?.let { binding.blurImageView.setImageBitmap(it) }
                    }
                    true
                }

                else -> false
            }
        }

        // Separate variables to track progress changes
        var previousBlurProgress = -1
        var previousAngleProgress = -1

// Set up blur intensity SeekBar
        binding.motionBlurSeekBar.max = 40
        binding.motionBlurSeekBar.progress = 5
        binding.motionBlurSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (currentBlurType == null) {
                    Toast.makeText(
                        this@BlurActivity,
                        "Select a blur type first",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // Avoid redundant updates for blur intensity
                if (progress == previousBlurProgress) return
                previousBlurProgress = progress

                // Scale blur intensity dynamically to a softer effect (0.5 to 10)
                blurProgress1 = (progress / 40f * 9.5f + 0.5f).coerceIn(0.5f, 10f)

                Log.d("SeekBar", "Blur Intensity: $blurProgress1, Angle: $motionBlurAngle")
                reprocessMotionBlur()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

// Set up angle SeekBar
        binding.motionAngleSeekbar.max = 360
        binding.motionAngleSeekbar.progress = 0  // Set an initial angle if needed
        binding.motionAngleSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Avoid redundant updates for angle
                if (progress == previousAngleProgress) return
                previousAngleProgress = progress

                // Update the motion blur angle (ensuring it stays within 0-360 degrees)
                motionBlurAngle = progress.toFloat() % 360
                reprocessMotionBlur()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    private fun updateButtonStates(
        eraserSelected: Boolean = false,
        blurPaintSelected: Boolean = false
    ) {
        binding.eraser.setImageResource(if (eraserSelected) R.drawable.eraser_selected else R.drawable.eraser)
        binding.eraser.setBackgroundResource(if (eraserSelected) R.drawable.buttons_background_selected else R.drawable.buttons_background)

        binding.blurPaintButton.setImageResource(if (blurPaintSelected) R.drawable.brush_2_selected else R.drawable.brush)
        binding.blurPaintButton.setBackgroundResource(if (blurPaintSelected) R.drawable.buttons_background_selected else R.drawable.buttons_background)
    }

    private fun captureAndSaveImageViewWithCustomViews() {
        imageCaptureHelper.captureAndSaveImage(
            binding.blurImageView,
            binding.dualCircleButton,
            binding.dualLinerButton,
            binding.blurContainer
        )
    }

    private fun initSegmenter() {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        segmenter = Segmentation.getClient(options)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun processImageWithSegmentation(
        originalBitmap: Bitmap,
        targetImageView: ImageView,
        blurType: BlurType,
        radialZoomAmount: Float = 0.1f,
        radialPasses: Int = 10,
    ) {
        binding.progressBar.visibility = View.VISIBLE

        val width = originalBitmap.width
        val height = originalBitmap.height
        val inputImage = InputImage.fromBitmap(originalBitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { segmentationResult ->
                try {
                    val maskBuffer = segmentationResult.buffer
                    val maskWidth = segmentationResult.width
                    val maskHeight = segmentationResult.height

                    val maskBitmap =
                        Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
                    maskBuffer.rewind()

                    for (y in 0 until maskHeight) {
                        for (x in 0 until maskWidth) {
                            val foregroundConfidence = maskBuffer.float
                            val alpha: Int = if (!isBlurOnFace) {
                                (foregroundConfidence * 255).toInt().coerceIn(0, 255)
                            } else {
                                ((1f - foregroundConfidence) * 255).toInt().coerceIn(0, 255)
                            }

                            val color = Color.argb(alpha, 255, 255, 255)
                            maskBitmap.setPixel(x, y, color)
                        }
                    }

                    val scaledMask = Bitmap.createScaledBitmap(maskBitmap, width, height, true)

                    val blurredBackground = when (blurType) {
                        BlurType.RADIAL -> BlurUtils.applyRadialBlur(
                            context = this,
                            originalBitmap,
                            width / 2f,
                            height / 2f,
                            radialZoomAmount, radialPasses
                        )

                        BlurType.MOTION -> createMotionBlur(
                            context = this,
                            originalBitmap,
                            angleInDegrees = motionBlurAngle,
                            distancePerPass = 10f,
                            blurPasses = radialPasses

                        )

                        BlurType.ZOOM -> BlurUtils.createZoomBlur(
                            context = this,
                            originalBitmap,
                            width / 2f,
                            height / 2f,
                            zoomAmount = radialZoomAmount,
                            blurPasses = radialPasses
                        )

                        BlurType.NORMAL -> BlurUtils.applyLinearBlur(
                            this,
                            originalBitmap,
                            radialZoomAmount
                        )
                    }

                    val compositedBitmap =
                        compositeBitmaps(originalBitmap, blurredBackground, scaledMask)

                    mutableBlurredBitmap = compositedBitmap.copy(Bitmap.Config.ARGB_8888, true)

                    targetImageView.setImageBitmap(mutableBlurredBitmap)

                    currentBlurType = blurType

                } catch (e: Exception) {
                    Log.e("ProcessImage", "Error during blur process: ${e.message}")
                } finally {
                    binding.progressBar.visibility = View.GONE // 👉 Hide loader when done
                }
            }
            .addOnFailureListener { e ->
                Log.e("Segmentation", "Segmentation failed: ${e.message}")
                binding.progressBar.visibility = View.GONE // 👉 Hide loader on failure
            }
    }

    fun compositeBitmaps(original: Bitmap, blurred: Bitmap, mask: Bitmap): Bitmap {
        val width = original.width
        val height = original.height

        val adjustedBlurred = if (blurred.width != width || blurred.height != height)
            Bitmap.createScaledBitmap(blurred, width, height, true)
        else blurred

        val adjustedMask = if (mask.width != width || mask.height != height)
            Bitmap.createScaledBitmap(mask, width, height, true)
        else mask

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val origPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)

        original.getPixels(origPixels, 0, width, 0, 0, width, height)
        adjustedBlurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
        adjustedMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)

        for (i in origPixels.indices) {
            val maskAlpha = Color.alpha(maskPixels[i]) / 255f
            val r =
                (Color.red(origPixels[i]) * maskAlpha + Color.red(blurPixels[i]) * (1 - maskAlpha)).toInt()
            val g =
                (Color.green(origPixels[i]) * maskAlpha + Color.green(blurPixels[i]) * (1 - maskAlpha)).toInt()
            val b =
                (Color.blue(origPixels[i]) * maskAlpha + Color.blue(blurPixels[i]) * (1 - maskAlpha)).toInt()
            val a = 255

            resultPixels[i] = Color.argb(a, r, g, b)
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)

        return result
    }

    private fun getBitmapCoordinates(imageView: ImageView, event: MotionEvent): PointF? {
        val drawable = imageView.drawable ?: return null
        val matrix = imageView.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]

        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val x = (event.x - transX) / scaleX
        val y = (event.y - transY) / scaleY

        if (x < 0 || y < 0 || x >= drawable.intrinsicWidth || y >= drawable.intrinsicHeight) {
            return null
        }

        return PointF(x, y)

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableEraseTouch() {
        binding.blurImageView.setOnTouchListener { _, motionEvent ->
            if (!isEraserActive) return@setOnTouchListener false

            val point = getBitmapCoordinates(binding.blurImageView, motionEvent)
            if (point != null) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = point.x
                        lastY = point.y
                        eraseBlurAt(point.x, point.y)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        drawEraseLine(lastX, lastY, point.x, point.y)
                        lastX = point.x
                        lastY = point.y
                    }

                    MotionEvent.ACTION_UP -> {
                        lastX = -1f
                        lastY = -1f
                        // Only update the imageView after finishing
                        binding.blurImageView.setImageBitmap(mutableBlurredBitmap)
                    }
                }
            }
            true
        }
    }


    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) // Replace pixels directly
    }


    private fun eraseBlurAt(x: Float, y: Float) {
        val canvas = Canvas(mutableBlurredBitmap)

        // Draw a circle from the original bitmap onto the blurred one
        val radius = 40f
        val srcRect = originalBitmap?.let { (y + radius).toInt().coerceAtMost(it.height) }?.let {
            Rect(
                (x - radius).toInt().coerceAtLeast(0),
                (y - radius).toInt().coerceAtLeast(0),
                (x + radius).toInt().coerceAtMost(originalBitmap!!.width),
                it
            )
        }

        val destRect = srcRect // Same size/position

        originalBitmap?.let {
            if (destRect != null) {
                canvas.drawBitmap(it, srcRect, destRect, null)
            }
        }
    }

    private fun drawEraseLine(startX: Float, startY: Float, endX: Float, endY: Float) {
        val distance = hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
        val steps = distance.toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i / distance
            val x = lerp(startX, endX, t)
            val y = lerp(startY, endY, t)

            eraseBlurAt(x, y)
        }

        binding.blurImageView.setImageBitmap(mutableBlurredBitmap)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableBlurPaintTouch() {
        binding.blurImageView.setOnTouchListener { view, motionEvent ->
            if (!isBlurPaintActive) {
                return@setOnTouchListener false
            }
            val bitmapPoint = getBitmapCoordinates(binding.blurImageView, motionEvent)

            if (bitmapPoint != null) {
                val x = bitmapPoint.x
                val y = bitmapPoint.y

                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = x
                        lastY = y
                        applyBlurAtPoint(x.toInt(), y.toInt())
                    }

                    MotionEvent.ACTION_MOVE -> {
                        drawSmoothLine(lastX, lastY, x, y)
                        lastX = x
                        lastY = y
                    }

                    MotionEvent.ACTION_UP -> {
                        lastX = -1f
                        lastY = -1f
                    }
                }
            }
            true
        }
    }

    private fun drawSmoothLine(startX: Float, startY: Float, endX: Float, endY: Float) {
        val distance = Math.hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
        val steps = distance.toInt()

        for (i in 0..steps) {
            val t = i / distance
            val x = lerp(startX, endX, t)
            val y = lerp(startY, endY, t)
            applyBlurAtPoint(x.toInt(), y.toInt())
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    private fun applyBlurAtPoint(x: Int, y: Int) {
        val radius = 30f // increased radius for smoother edges
        val blurRadius = 50f // blur intensity

        if (originalBitmap == null) return

        val canvas = Canvas(mutableBlurredBitmap)

        val patchSize = (radius * 2).toInt()
        val left = (x - radius).toInt().coerceAtLeast(0)
        val top = (y - radius).toInt().coerceAtLeast(0)
        val right = (x + radius).toInt().coerceAtMost(originalBitmap!!.width)
        val bottom = (y + radius).toInt().coerceAtMost(originalBitmap!!.height)

        if (left >= right || top >= bottom) return

        // Extract the patch from the original bitmap
        val patch = Bitmap.createBitmap(originalBitmap!!, left, top, right - left, bottom - top)

        // Apply blur to the patch
        val blurredPatch = blurBitmap(this, patch, blurRadius)

        // Create a circular mask
        val mask = Bitmap.createBitmap(patch.width, patch.height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
        }
        maskCanvas.drawCircle(
            patch.width / 2f,
            patch.height / 2f,
            radius,
            paint
        )

        // Prepare paint for blending
        val maskedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }

        // Mask the blurred patch
        val finalPatch = Bitmap.createBitmap(patch.width, patch.height, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalPatch)

        // Draw the blurred patch first
        finalCanvas.drawBitmap(blurredPatch, 0f, 0f, null)
        // Apply circular mask
        finalCanvas.drawBitmap(mask, 0f, 0f, maskedPaint)

        // Draw the final circular blurred patch on the mutable blurred bitmap
        canvas.drawBitmap(finalPatch, left.toFloat(), top.toFloat(), null)

        binding.blurImageView.setImageBitmap(mutableBlurredBitmap)
    }

    private fun disableTouch() {
        binding.blurImageView.setOnTouchListener(null)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun reprocessMotionBlur() {
        (binding.blurImageView.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
            if (currentBlurType == BlurType.MOTION) {
                processImageWithSegmentation(
                    bitmap,
                    binding.blurImageView,
                    BlurType.MOTION,
                    radialZoomAmount = 0.1f,
                    radialPasses = blurProgress1.toInt()
                        .coerceIn(1, 25) // Fix: Use adaptive blur strength
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createMotionBlur(context: Context, bitmap: Bitmap, angleInDegrees: Float, distancePerPass: Float, blurPasses: Int): Bitmap {
        require(!bitmap.isRecycled) { "Original bitmap is recycled!" }

        val width = bitmap.width
        val height = bitmap.height

        val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else bitmap

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        val passes = blurPasses.coerceAtLeast(1)

        // Dynamically scale distance per pass based on blur strength
        val adaptiveDistance = (distancePerPass * (blurProgress1 / 10f)).coerceIn(1f, 20f)

        val radians = Math.toRadians(angleInDegrees.toDouble())
        val offsetX = (Math.cos(radians) * adaptiveDistance).toFloat() // Now using adaptiveDistance
        val offsetY = (Math.sin(radians) * adaptiveDistance).toFloat() // Now using adaptiveDistance

        val maxAlpha = 255f
        val alphaStep = maxAlpha / passes

        // Pre-blur image
        val blurredBitmap = applyLinearBlur1(context, safeBitmap, blurPasses)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (i in 0 until passes) {
            paint.alpha = (maxAlpha - (i * alphaStep)).toInt().coerceIn(0, 255)
            val dx = offsetX * i
            val dy = offsetY * i
            canvas.drawBitmap(blurredBitmap, dx, dy, paint)
        }

        // Ensure we do not recycle the bitmap while it is still in use
        if (blurredBitmap !== safeBitmap) {
            blurredBitmap.recycle()
        }

        return outputBitmap
    }

    fun applyLinearBlur1(context: Context, bitmap: Bitmap, blurRadius: Int): Bitmap {
        val rs = RenderScript.create(context)

        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        val radius = blurRadius.coerceIn(1, 25)
        script.setRadius(radius.toFloat())

        script.setInput(input)
        script.forEach(output)

        val outputBitmap =
            createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        output.copyTo(outputBitmap)

        rs.destroy()

        return outputBitmap
    }

}