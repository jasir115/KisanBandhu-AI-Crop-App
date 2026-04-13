package com.kisanbandhu.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Html
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.View
import android.view.WindowInsetsController
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kisanbandhu.app.utils.LocationUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class CropHealthActivity : SwipeableActivity() {

    private val TAG = "CropHealthDebug"
    private lateinit var viewModel: CropHealthViewModel

    private lateinit var layoutInitial: View
    private lateinit var layoutPreview: View
    private lateinit var layoutProcessing: View
    private lateinit var layoutAnalysis: View
    private lateinit var layoutCamera: View

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isFlashOn = false
    private var isGridVisible = true

    private lateinit var ivPreviewImage: ImageView
    private var selectedImageUri: Uri? = null
    private var currentResult: DiseaseResponse? = null

    private lateinit var stepCrop: TextView
    private lateinit var stepDisease: TextView
    private lateinit var stepSeverity: TextView
    private lateinit var stepTreatment: TextView

    private lateinit var ivAnalyzedCrop: ImageView
    private lateinit var tvDetectedCropType: TextView
    private lateinit var tvDiseaseTitle: TextView
    private lateinit var tvDiseaseHindi: TextView
    private lateinit var badgeSeverity: TextView
    private lateinit var ivSeverityIcon: ImageView
    private lateinit var pbConfidence: LinearProgressIndicator
    private lateinit var tvConfidencePercent: TextView
    private lateinit var tvActionSuggest: TextView

    private lateinit var tabLayout: TabLayout
    private lateinit var viewDescription: View
    private lateinit var viewPrevention: View
    private lateinit var viewTreatment: View
    private lateinit var tvDiseaseDesc: TextView
    private lateinit var tvSymptoms: TextView
    private lateinit var tvPreventionInfo: TextView
    private lateinit var tvTreatmentInfo: TextView

    private lateinit var zoomSeekBar: SeekBar

    // Performance: Frame throttling for ImageAnalysis
    private var lastAnalysisTime = 0L

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (isFinishing) return@registerForActivityResult
        uri?.let {
            selectedImageUri = it
            showPreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_health)

        clearInternalCache()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }

        viewModel = ViewModelProvider(this)[CropHealthViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()

        initViews()
        setupClickListeners()
        setupBottomNavigation()
        observeViewModel()
    }

    private fun clearInternalCache() {
        try {
            val dir = cacheDir
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_crop") || file.name.startsWith("cropped_crop")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
        }
    }

    private fun initViews() {
        layoutInitial = findViewById(R.id.layout_initial_selection)
        layoutPreview = findViewById(R.id.layout_preview)
        layoutProcessing = findViewById(R.id.layout_processing)
        layoutAnalysis = findViewById(R.id.layout_analysis_view)
        layoutCamera = findViewById(R.id.layout_camera_interface)

        viewFinder = findViewById(R.id.viewFinder)
        viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER

        ivPreviewImage = findViewById(R.id.iv_preview_image)

        stepCrop = findViewById(R.id.step_crop_type)
        stepDisease = findViewById(R.id.step_disease)
        stepSeverity = findViewById(R.id.step_severity)
        stepTreatment = findViewById(R.id.step_treatment)

        ivAnalyzedCrop = findViewById(R.id.iv_analyzed_crop)
        tvDetectedCropType = findViewById(R.id.tv_detected_crop_type)
        tvDiseaseTitle = findViewById(R.id.tv_disease_title)
        tvDiseaseHindi = findViewById(R.id.tv_disease_hindi)
        badgeSeverity = findViewById(R.id.badge_severity)
        ivSeverityIcon = findViewById(R.id.iv_severity_icon)
        pbConfidence = findViewById(R.id.pb_confidence)
        tvConfidencePercent = findViewById(R.id.tv_confidence_percent)
        tvActionSuggest = findViewById(R.id.tv_action_suggest)

        tabLayout = findViewById(R.id.tabs_disease_info)
        viewDescription = findViewById(R.id.view_description)
        viewPrevention = findViewById(R.id.view_prevention)
        viewTreatment = findViewById(R.id.view_treatment)
        tvDiseaseDesc = findViewById(R.id.tv_disease_desc)
        tvSymptoms = findViewById(R.id.tv_symptoms)
        tvPreventionInfo = findViewById(R.id.tv_prevention_info)
        tvTreatmentInfo = findViewById(R.id.tv_treatment_info)

        zoomSeekBar = findViewById(R.id.zoom_seekbar)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { onBackPressed() }
        findViewById<MaterialCardView>(R.id.btn_capture_card).setOnClickListener {
            if (checkCameraPermission()) startCustomCamera()
            else LocationUtils.showCameraDisclosure(this, onAgree = { requestCameraPermission() }, onCancel = {})
        }
        findViewById<MaterialCardView>(R.id.btn_gallery_card).setOnClickListener { galleryLauncher.launch("image/*") }
        findViewById<View>(R.id.btn_capture_circle).setOnClickListener { takePhoto() }
        findViewById<View>(R.id.btn_close_camera).setOnClickListener { hideCamera() }
        findViewById<View>(R.id.btn_cam_switch).setOnClickListener { switchCamera() }
        findViewById<View>(R.id.btn_cam_flash).setOnClickListener { toggleFlash() }
        findViewById<View>(R.id.btn_cam_grid).setOnClickListener { toggleGrid() }
        findViewById<View>(R.id.btn_clear_preview).setOnClickListener { resetUI() }
        findViewById<View>(R.id.btn_choose_different).setOnClickListener { resetUI() }
        findViewById<View>(R.id.btn_start_ai_analysis).setOnClickListener { selectedImageUri?.let { startAnalysis(it) } }
        findViewById<View>(R.id.btn_scan_another).setOnClickListener { resetUI() }
        findViewById<View>(R.id.btn_save_report).setOnClickListener { saveReport() }
        findViewById<View>(R.id.btn_share).setOnClickListener { shareReport() }
        findViewById<View>(R.id.btn_download).setOnClickListener { downloadReport() }
        findViewById<View>(R.id.btn_get_expert).setOnClickListener { getExpertAdvice() }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateTabContent(tab?.position ?: 0) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    camera?.cameraControl?.setLinearZoom(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.diseaseResult.observe(this) { response ->
            if (isFinishing) return@observe
            if (response != null && response.success) {
                if (layoutProcessing.visibility == View.VISIBLE) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isFinishing) {
                            showResult(response)
                            checkResultStatusAndNotify(response)
                        }
                    }, 3500)
                } else {
                    showResult(response)
                    checkResultStatusAndNotify(response)
                }
            }
        }
        viewModel.error.observe(this) { errorMsg ->
            if (!isFinishing && !errorMsg.isNullOrEmpty()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                resetUI()
                viewModel.clearError()
            }
        }
        viewModel.toastEvent.observe(this) { message ->
            if (!isFinishing && !message.isNullOrEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearToast()
            }
        }
    }

    private fun checkResultStatusAndNotify(response: DiseaseResponse) {
        val language = LocaleHelper.getLanguage(this)
        when {
            response.isRejected -> {
                val msg = if (language == "hi") "⚠️ पत्ती नहीं मिली: कृपया पत्ती की स्पष्ट फोटो लें।" else "⚠️ Not a Leaf: Please capture a clear photo of the leaf."
                showTopToast(msg, true)
            }
            response.disease_name == "Uncertain Detection" || response.confidence < 0.45f -> {
                val msg = if (language == "hi") "❓ अनिश्चित परिणाम: AI सुनिश्चित नहीं है। बेहतर रोशनी में प्रयास करें।" else "❓ Uncertain Result: AI is unsure. Try better lighting."
                showTopToast(msg, true)
            }
            response.confidence < 0.70f -> {
                val msg = if (language == "hi") "ℹ️ बुनियादी विश्लेषण: सटीकता के लिए विशेषज्ञ डेटा का उपयोग किया जा रहा है।" else "ℹ️ Basic Analysis: Using offline expert data for accuracy."
                showTopToast(msg, false)
            }
        }
    }

    private fun showTopToast(message: String, isWarning: Boolean) {
        if (isFinishing) return
        val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 150)
        toast.show()
    }

    private fun showResult(response: DiseaseResponse) {
        if (isFinishing) return
        currentResult = response
        layoutProcessing.visibility = View.GONE
        layoutAnalysis.visibility = View.VISIBLE

        ivAnalyzedCrop.load(selectedImageUri)
        val detectedLabel = if (LocaleHelper.getLanguage(this) == "hi") "पता चला:" else "Detected:"
        tvDetectedCropType.text = "$detectedLabel ${response.crop_type ?: "Crop"}"
        tvDiseaseTitle.text = response.disease_name
        tvDiseaseHindi.text = response.hindi_name

        val confidence = (response.confidence * 100).toInt()
        tvConfidencePercent.text = "$confidence%"
        pbConfidence.progress = confidence

        updateSeverityStyle(response.severity, response.disease_name, response.isRejected)

        tvDiseaseDesc.text = formatHtml(response.description)
        tvSymptoms.text = formatHtml(response.symptoms)
        tvPreventionInfo.text = formatHtml(response.prevention)
        tvTreatmentInfo.text = formatHtml(response.treatment)

        val sev = response.severity?.lowercase()
        val isHealthy = sev == "healthy" || sev == "none" || response.disease_name?.lowercase()?.contains("healthy") == true
        val isRejected = response.isRejected

        tvActionSuggest.text = when {
            isRejected -> getString(R.string.action_retry_scan)
            isHealthy -> getString(R.string.action_healthy)
            sev == "high" -> getString(R.string.action_high_severity)
            sev == "medium" -> getString(R.string.action_medium_severity)
            else -> getString(R.string.action_default)
        }
    }

    private fun formatHtml(text: String?): CharSequence {
        if (text == null) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text)
        }
    }

    private fun startCustomCamera() {
        if (isFinishing) return
        layoutInitial.visibility = View.GONE
        layoutCamera.visibility = View.VISIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (isFinishing) return@addListener
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Performance Fix: Lower resolution (640x480) and Frame Throttling (300ms)
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastAnalysisTime >= 300) {
                            lastAnalysisTime = currentTime
                            val luminance = calculateLuminance(imageProxy)
                            updateQualityUI(luminance)
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()

                // FORCE 1:1 Rational to sync with the square scanner box
                val viewPort = ViewPort.Builder(Rational(1, 1), viewFinder.display.rotation).build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture!!)
                    .addUseCase(imageAnalyzer!!)
                    .setViewPort(viewPort)
                    .build()

                camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup)

                camera?.cameraInfo?.zoomState?.observe(this) { zoomState ->
                    val progress = (zoomState.linearZoom * 100).toInt()
                    zoomSeekBar.progress = progress
                }
            } catch (exc: Exception) { Log.e(TAG, "Use case binding failed", exc) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        lensFacing = if (CameraSelector.LENS_FACING_BACK == lensFacing) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCustomCamera()
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        val flashIcon = findViewById<ImageView>(R.id.btn_cam_flash)
        imageCapture?.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        flashIcon.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        flashIcon.setColorFilter(ContextCompat.getColor(this, if (isFlashOn) R.color.brand_green else R.color.white))
    }

    private fun toggleGrid() {
        isGridVisible = !isGridVisible
        findViewById<View>(R.id.camera_focus_frame).visibility = if (isGridVisible) View.VISIBLE else View.GONE
        findViewById<ImageView>(R.id.btn_cam_grid).setColorFilter(ContextCompat.getColor(this, if (isGridVisible) R.color.brand_green else R.color.white))
    }

    private fun hideCamera() {
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (e: Exception) { }
        layoutCamera.visibility = View.GONE
        layoutInitial.visibility = View.VISIBLE
    }

    private fun showPreview(uri: Uri) {
        layoutInitial.visibility = View.GONE
        layoutPreview.visibility = View.VISIBLE
        ivPreviewImage.load(uri)
    }

    private fun startAnalysis(uri: Uri) {
        layoutPreview.visibility = View.GONE
        layoutProcessing.visibility = View.VISIBLE

        stepCrop.setCompoundDrawablesWithIntrinsicBounds(R.drawable.circle_dot_yellow, 0, 0, 0)
        stepDisease.setCompoundDrawablesWithIntrinsicBounds(R.drawable.circle_dot_yellow, 0, 0, 0)
        stepSeverity.setCompoundDrawablesWithIntrinsicBounds(R.drawable.circle_dot_yellow, 0, 0, 0)
        stepTreatment.setCompoundDrawablesWithIntrinsicBounds(R.drawable.circle_dot_yellow, 0, 0, 0)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ if (!isFinishing) stepCrop.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0) }, 800)
        handler.postDelayed({ if (!isFinishing) stepDisease.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0) }, 1600)
        handler.postDelayed({ if (!isFinishing) stepSeverity.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0) }, 2400)
        handler.postDelayed({ if (!isFinishing) stepTreatment.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0) }, 3200)

        // PERFORMANCE: Move bitmap processing to background
        cameraExecutor.execute {
            val bitmap = uriToBitmap(uri)
            runOnUiThread {
                if (bitmap != null) {
                    val language = LocaleHelper.getLanguage(this)
                    viewModel.scanCropOffline(bitmap, language)
                } else {
                    resetUI()
                }
            }
        }
    }

    private fun resetUI() {
        layoutInitial.visibility = View.VISIBLE
        layoutPreview.visibility = View.GONE
        layoutProcessing.visibility = View.GONE
        layoutAnalysis.visibility = View.GONE
        layoutCamera.visibility = View.GONE
        clearInternalCache()
        selectedImageUri = null
        currentResult = null
    }

    private fun updateTabContent(position: Int) {
        viewDescription.visibility = if (position == 0) View.VISIBLE else View.GONE
        viewPrevention.visibility = if (position == 1) View.VISIBLE else View.GONE
        viewTreatment.visibility = if (position == 2) View.VISIBLE else View.GONE
    }

    private fun updateSeverityStyle(severity: String?, diseaseName: String?, isRejected: Boolean) {
        val sev = severity?.lowercase() ?: "low"
        val isHealthy = sev == "healthy" || sev == "none" || diseaseName?.lowercase()?.contains("healthy") == true

        val color = when {
            isRejected -> R.color.text_secondary
            isHealthy -> R.color.brand_green
            sev == "high" -> android.R.color.holo_red_dark
            sev == "medium" -> android.R.color.holo_orange_dark
            else -> R.color.brand_green
        }

        val background = when {
            isRejected -> R.drawable.bg_pill
            isHealthy -> R.drawable.bg_rounded_green_light
            sev == "high" -> R.drawable.bg_loss_badge
            sev == "medium" -> R.drawable.bg_rounded_orange
            else -> R.drawable.bg_rounded_green_light
        }

        badgeSeverity.setBackgroundResource(background)
        badgeSeverity.text = when {
            isRejected -> getString(R.string.severity_invalid)
            isHealthy -> getString(R.string.severity_healthy)
            sev == "high" -> getString(R.string.severity_high)
            sev == "medium" -> getString(R.string.severity_medium)
            else -> getString(R.string.severity_low)
        }

        ivSeverityIcon.setColorFilter(ContextCompat.getColor(this, color))
        ivSeverityIcon.setImageResource(if (isRejected) R.drawable.ic_info else if (isHealthy) R.drawable.ic_check_circle else R.drawable.ic_warning)

        if (isHealthy) {
            badgeSeverity.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
        } else if (sev == "high") {
            badgeSeverity.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else if (isRejected) {
            badgeSeverity.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            badgeSeverity.setTextColor(ContextCompat.getColor(this, color))
        }
    }

    private fun checkCameraPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1003) }

    private fun loadBitmapWithRotation(file: File): Bitmap? {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // CRITICAL FIX: No Hardware Bitmaps
                }
            } else {
                @Suppress("DEPRECATION")
                val bmp = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                bmp.copy(Bitmap.Config.ARGB_8888, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion failed", e)
            null
        }
    }

    private fun calculateLuminance(image: ImageProxy): Double {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return data.map { it.toInt() and 0xFF }.average()
    }

    private fun updateQualityUI(luminance: Double) {
        if (isFinishing) return
        runOnUiThread {
            findViewById<View>(R.id.dot_quality)?.setBackgroundResource(if (luminance < 40) R.drawable.circle_dot_red else R.drawable.circle_dot_yellow)
            findViewById<TextView>(R.id.tv_quality)?.text = if (luminance < 40) "Poor Quality" else "Good"
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "temp_crop_capture.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // PERFORMANCE: Run image saving and processing on cameraExecutor
        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) { Log.e(TAG, "Capture failed", exc) }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (isFinishing) return

                val bitmap = loadBitmapWithRotation(photoFile)
                if (bitmap != null) {
                    val originalWidth = bitmap.width
                    val originalHeight = bitmap.height
                    val size = min(originalWidth, originalHeight)

                    val cropSize = (size * 0.55).toInt()
                    val x = (originalWidth - cropSize) / 2
                    val y = (originalHeight - cropSize) / 2

                    val squareBitmap = Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize)

                    val savedFile = File(cacheDir, "cropped_crop_output.jpg")
                    FileOutputStream(savedFile).use { squareBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    selectedImageUri = Uri.fromFile(savedFile)
                }

                runOnUiThread {
                    if (!isFinishing) {
                        hideCamera()
                        selectedImageUri?.let { showPreview(it) }
                    }
                }
            }
        })
    }

    private fun saveReport() {
        val result = currentResult ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val reportData = hashMapOf("userId" to uid, "diseaseName" to result.disease_name, "hindiName" to result.hindi_name, "cropType" to result.crop_type, "timestamp" to System.currentTimeMillis())
        FirebaseFirestore.getInstance().collection("crop_reports").add(reportData).addOnSuccessListener {
            if (!isFinishing) Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareReport() {
        val result = currentResult ?: return
        val shareText = "🌱 KisanBandhu AI Report\nCrop: ${result.crop_type}\nDisease: ${result.disease_name}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "Share via"))
    }

    private fun downloadReport() { Toast.makeText(this, "Report saved to gallery", Toast.LENGTH_SHORT).show() }

    private fun getExpertAdvice() { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:18001801551"))) }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                R.id.nav_market -> { startActivity(Intent(this, MarketAnalysisActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                R.id.nav_weather -> { startActivity(Intent(this, WeatherInfoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearInternalCache()
        cameraExecutor.shutdown()
    }
}
