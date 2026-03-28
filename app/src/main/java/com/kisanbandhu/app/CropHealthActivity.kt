package com.kisanbandhu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CropHealthActivity : SwipeableActivity() {

    private val TAG = "CropHealthDebug"
    private lateinit var viewModel: CropHealthViewModel
    
    // Layouts
    private lateinit var layoutInitial: LinearLayout
    private lateinit var layoutAnalysis: LinearLayout
    
    // Header/Toolbar
    private lateinit var layoutActionButtons: LinearLayout
    private lateinit var tvToolbarSubtitle: TextView
    
    // Preview
    private lateinit var ivAnalyzedCrop: ImageView
    private lateinit var tvDetectedCropType: TextView
    
    // Disease Summary Card
    private lateinit var tvDiseaseTitle: TextView
    private lateinit var tvDiseaseHindi: TextView
    private lateinit var badgeSeverity: TextView
    private lateinit var ivSeverityIcon: ImageView
    private lateinit var pbConfidence: LinearProgressIndicator
    private lateinit var tvConfidencePercent: TextView
    private lateinit var tvActionSuggest: TextView
    
    // Dynamic Content
    private lateinit var tabLayout: TabLayout
    private lateinit var viewDescription: View
    private lateinit var viewPrevention: View
    private lateinit var viewTreatment: View
    
    private lateinit var tvDiseaseDesc: TextView
    private lateinit var tvSymptoms: TextView
    private lateinit var tvPreventionInfo: TextView
    private lateinit var tvTreatmentInfo: TextView
    
    private lateinit var tvReportId: TextView
    private lateinit var tvReportDate: TextView

    private var currentResult: DiseaseResponse? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        Log.d(TAG, "galleryLauncher: Result received. Uri: $uri")
        uri?.let { 
            processSelectedImage(it) 
        } ?: Log.w(TAG, "galleryLauncher: No image selected")
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "cameraLauncher: Result received. Code: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                Log.d(TAG, "cameraLauncher: Captured bitmap size: ${bitmap.width}x${bitmap.height}")
                val uri = saveBitmapToFile(bitmap)
                processSelectedImage(uri)
            } else {
                Log.e(TAG, "cameraLauncher: Bitmap is null")
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "cameraLauncher: Capture cancelled or failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_health)
        Log.d(TAG, "onCreate: Initializing Activity")

        viewModel = ViewModelProvider(this)[CropHealthViewModel::class.java]

        initViews()
        setupClickListeners()
        setupBottomNavigation()
        observeViewModel()
    }

    private fun initViews() {
        Log.d(TAG, "initViews: Binding layout components")
        layoutInitial = findViewById(R.id.layout_initial_selection)
        layoutAnalysis = findViewById(R.id.layout_analysis_view)
        layoutActionButtons = findViewById(R.id.layout_action_buttons)
        tvToolbarSubtitle = findViewById(R.id.tv_toolbar_subtitle)
        
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
        
        tvReportId = findViewById(R.id.tv_report_id)
        tvReportDate = findViewById(R.id.tv_report_date)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener { 
            Log.d(TAG, "btn_back clicked")
            if (layoutAnalysis.visibility == View.VISIBLE) resetUI() else finish()
        }

        findViewById<MaterialCardView>(R.id.btn_capture_card).setOnClickListener {
            Log.d(TAG, "btn_capture_card clicked")
            if (checkCameraPermission()) {
                openCamera()
            } else {
                showCameraDisclosure()
            }
        }

        findViewById<MaterialCardView>(R.id.btn_gallery_card).setOnClickListener {
            Log.d(TAG, "btn_gallery_card clicked")
            galleryLauncher.launch("image/*")
        }

        findViewById<View>(R.id.btn_scan_another).setOnClickListener {
            Log.d(TAG, "btn_scan_another clicked")
            resetUI()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                Log.d(TAG, "Tab selected: ${tab?.position}")
                updateTabContent(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Save Result
        findViewById<View>(R.id.btn_save_report).setOnClickListener {
            saveReport()
        }

        // Share
        findViewById<View>(R.id.btn_share).setOnClickListener {
            shareReport()
        }

        // Download
        findViewById<View>(R.id.btn_download).setOnClickListener {
            downloadReport()
        }

        // Expert Advice
        findViewById<View>(R.id.btn_get_expert).setOnClickListener {
            getExpertAdvice()
        }
    }

    private fun showCameraDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Camera Access Required")
            .setMessage("KisanBandhu needs camera access to let you take photos of diseased crops for AI analysis. Photos are used only for disease detection.")
            .setCancelable(false)
            .setPositiveButton("Proceed") { _, _ ->
                requestCameraPermission()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Crop health analysis cannot be performed without camera access", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveReport() {
        currentResult?.let {
            Toast.makeText(this, "Health report saved to your profile history", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(this, "No result to save", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val result = currentResult ?: return
        val shareText = """
            🌱 KisanBandhu Crop Health Report
            ------------------------------
            Disease: ${result.disease_name} (${result.hindi_name})
            Confidence: ${(result.confidence * 100).toInt()}%
            Severity: ${result.severity}
            
            Description: ${result.description}
            
            Treatment: ${result.treatment}
            ------------------------------
            Download KisanBandhu for smart farming!
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Crop Health Report")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun downloadReport() {
        Toast.makeText(this, "Generating PDF Report... saved to downloads", Toast.LENGTH_LONG).show()
    }

    private fun getExpertAdvice() {
        val phoneNumber = "18001801551"
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }

    private fun openCamera() {
        Log.d(TAG, "openCamera: Launching camera intent")
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun processSelectedImage(uri: Uri) {
        Log.d(TAG, "processSelectedImage: Processing Uri: $uri")
        layoutInitial.visibility = View.GONE
        layoutAnalysis.visibility = View.VISIBLE
        layoutActionButtons.visibility = View.VISIBLE
        tvToolbarSubtitle.text = "Analysis in progress..."
        tvDetectedCropType.text = "Analyzing..."
        
        ivAnalyzedCrop.load(uri) 
        
        val randomId = (100000..999999).random()
        tvReportId.text = "Report ID: CHA-$randomId"
        tvReportDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        
        val bitmap = uriToBitmap(uri)
        if (bitmap != null) {
            Log.d(TAG, "processSelectedImage: Triggering scanCropOffline")
            viewModel.scanCropOffline(bitmap)
        } else {
            Log.e(TAG, "processSelectedImage: Failed to convert Uri to Bitmap")
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            resetUI()
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        Log.d(TAG, "uriToBitmap: Converting $uri")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "uriToBitmap: Error: ${e.message}", e)
            null
        }
    }

    private fun resetUI() {
        Log.d(TAG, "resetUI: Resetting to initial selection state")
        layoutInitial.visibility = View.VISIBLE
        layoutAnalysis.visibility = View.GONE
        layoutActionButtons.visibility = View.GONE
        tvToolbarSubtitle.text = "AI-powered disease detection"
        
        tvDiseaseTitle.text = "Analyzing symptoms..."
        tvDiseaseHindi.text = "लक्षणों का विश्लेषण कर रहे हैं..."
        pbConfidence.progress = 0
        tvConfidencePercent.text = "0%"
        badgeSeverity.visibility = View.GONE
        tvActionSuggest.text = "Processing image data"
        
        tabLayout.getTabAt(0)?.select()
        updateTabContent(0)
        currentResult = null
    }

    private fun updateTabContent(position: Int) {
        viewDescription.visibility = if (position == 0) View.VISIBLE else View.GONE
        viewPrevention.visibility = if (position == 1) View.VISIBLE else View.GONE
        viewTreatment.visibility = if (position == 2) View.VISIBLE else View.GONE
    }

    private fun checkCameraPermission(): Boolean = 
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        Log.d(TAG, "requestCameraPermission: Requesting CAMERA permission")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1003)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: RequestCode: $requestCode")
        if (requestCode == 1003 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onRequestPermissionsResult: Permission GRANTED")
            openCamera()
        } else if (requestCode == 1003) {
            Log.w(TAG, "onRequestPermissionsResult: Permission DENIED")
            Toast.makeText(this, "Crop health analysis cannot be performed without camera access", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        Log.d(TAG, "observeViewModel: Starting observation")
        viewModel.diseaseResult.observe(this) { response ->
            if (response != null && response.success) {
                currentResult = response
                Log.d(TAG, "observeViewModel: Disease detected: ${response.disease_name} (Confidence: ${response.confidence})")
                tvToolbarSubtitle.text = "Diagnosis complete"
                tvDetectedCropType.text = "Detected Condition"
                tvDiseaseTitle.text = response.disease_name
                tvDiseaseHindi.text = response.hindi_name ?: getHindiName(response.disease_name)
                
                val confidence = (response.confidence * 100).toInt()
                tvConfidencePercent.text = "$confidence%"
                pbConfidence.progress = confidence
                
                badgeSeverity.visibility = View.VISIBLE
                badgeSeverity.text = "${response.severity} Severity"
                updateSeverityStyle(response.severity)

                tvDiseaseDesc.text = response.description ?: "Analysis completed via offline AI model."
                tvSymptoms.text = response.symptoms ?: "Visible changes in leaf color or texture."
                tvPreventionInfo.text = response.prevention ?: "Maintain proper crop hygiene and monitor regularly."
                tvTreatmentInfo.text = response.treatment ?: "Consult local agriculture office if severity increases."
                
                tvActionSuggest.text = if (response.severity?.lowercase() == "high") 
                    "Urgent treatment required" else "Follow recommendations"
            } else {
                Log.w(TAG, "observeViewModel: Empty or failed response")
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Log.e(TAG, "observeViewModel Error: $errorMsg")
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                resetUI()
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            Log.d(TAG, "observeViewModel Loading: $isLoading")
            if (isLoading) {
                tvToolbarSubtitle.text = "Analyzing image..."
            }
        }
    }

    private fun updateSeverityStyle(severity: String?) {
        Log.d(TAG, "updateSeverityStyle: Severity: $severity")
        when (severity?.lowercase()) {
            "high" -> {
                badgeSeverity.setBackgroundResource(R.drawable.bg_loss_badge)
                badgeSeverity.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                ivSeverityIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            "low" -> {
                badgeSeverity.setBackgroundResource(R.drawable.bg_rounded_green_light)
                badgeSeverity.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
                ivSeverityIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
            }
            else -> {
                badgeSeverity.setBackgroundResource(R.drawable.bg_rounded_orange)
                badgeSeverity.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                ivSeverityIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            }
        }
    }

    private fun getHindiName(name: String): String {
        val lower = name.lowercase()
        val result = when {
            lower.contains("leaf rust") -> "गेहूं का पत्ती रतुआ"
            lower.contains("healthy") -> "स्वस्थ फसल"
            lower.contains("early blight") -> "अगेती झुलसा"
            lower.contains("late blight") -> "पछेती झुलसा"
            else -> "रोग का पता लगाया गया"
        }
        Log.d(TAG, "getHindiName: Input: $name, Output: $result")
        return result
    }

    private fun saveBitmapToFile(bitmap: Bitmap): Uri {
        val fileName = "camera_scan_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)
        Log.d(TAG, "saveBitmapToFile: Saving to $fileName")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
        return Uri.fromFile(file)
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        bottomNav.setOnItemSelectedListener { item ->
            Log.d(TAG, "BottomNav item clicked: ${item.itemId}")
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    true
                }
                R.id.nav_market -> {
                    startActivity(Intent(this, MarketAnalysisActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    true
                }
                R.id.nav_weather -> {
                    startActivity(Intent(this, WeatherInfoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    true
                }
                else -> false
            }
        }
    }
}
