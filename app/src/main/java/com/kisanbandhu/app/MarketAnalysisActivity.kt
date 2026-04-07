package com.kisanbandhu.app

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kisanbandhu.app.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MarketAnalysisActivity : SwipeableActivity() {

    private val TAG = "MarketAnalysisDebug"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MarketAnalysisViewModel by viewModels()
    private lateinit var recommendationViewModel: CropRecommendationViewModel
    
    private lateinit var recPricesContainer: LinearLayout
    private lateinit var genericSectionsContainer: LinearLayout
    private lateinit var pbLocalPrices: ProgressBar
    private lateinit var tvCurrentLocation: TextView
    private lateinit var sectionRecommended: View
    private lateinit var cardSmartRecBanner: MaterialCardView
    private lateinit var tvSmartRecDesc: TextView

    private lateinit var tvRisingCount: TextView
    private lateinit var tvFallingCount: TextView
    private lateinit var tvBestDealCount: TextView

    private lateinit var btnVeg: MaterialButton
    private lateinit var btnFruits: MaterialButton
    private lateinit var btnGrains: MaterialButton
    private lateinit var etSearch: EditText
    private lateinit var layoutLocationSelector: View
    private lateinit var btnVoiceSearch: ImageView
    private lateinit var btnFilter: MaterialButton

    private var currentCategory = "Vegetables"
    private var allMarketData: List<MarketPriceInfo> = emptyList()
    private var displayData: List<MarketPriceInfo> = emptyList()
    
    private var searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val REQUEST_CHECK_SETTINGS = 0x1

    // Filter states
    private var selectedSort: Int = 0 // 0: None, 1: High to Low, 2: Low to High
    private var selectedTrend: String? = null // "Rising", "Falling", null

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let {
                etSearch.setText(it)
                Log.d(TAG, "Voice input received: $it")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market_analysis)

        recommendationViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[CropRecommendationViewModel::class.java]

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        initViews()
        setupBottomNavigation()
        checkLocationPermission()
        observeViewModel()
        setupSearch()

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val recommendedCrops = intent?.getStringArrayListExtra("recommended_crops")
        if (recommendedCrops != null) {
            viewModel.fetchPricesForRecommendedCrops(recommendedCrops)
            cardSmartRecBanner.visibility = View.VISIBLE
            sectionRecommended.visibility = View.VISIBLE
        }
    }

    private fun initViews() {
        recPricesContainer = findViewById(R.id.rec_prices_container)
        genericSectionsContainer = findViewById(R.id.generic_market_sections)
        pbLocalPrices = findViewById(R.id.pb_local_prices)
        tvCurrentLocation = findViewById(R.id.tv_current_location)
        sectionRecommended = findViewById(R.id.section_recommended)
        cardSmartRecBanner = findViewById(R.id.card_smart_rec_banner)
        tvSmartRecDesc = findViewById(R.id.tv_smart_rec_desc)

        tvRisingCount = findViewById(R.id.tv_rising_count)
        tvFallingCount = findViewById(R.id.tv_falling_count)
        tvBestDealCount = findViewById(R.id.tv_best_deal_count)

        btnVeg = findViewById(R.id.btn_category_veg)
        btnFruits = findViewById(R.id.btn_category_fruits)
        btnGrains = findViewById(R.id.btn_category_grains)
        etSearch = findViewById(R.id.et_search_crops)
        layoutLocationSelector = findViewById(R.id.layout_location_selector)
        btnVoiceSearch = findViewById(R.id.btn_voice_search)
        btnFilter = findViewById(R.id.btn_filter)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnVeg.setOnClickListener { switchCategory("Vegetables") }
        btnFruits.setOnClickListener { switchCategory("Fruits") }
        btnGrains.setOnClickListener { switchCategory("Grains") }

        layoutLocationSelector.setOnClickListener {
            checkLocationPermission()
        }

        findViewById<MaterialButton>(R.id.btn_create_alert).setOnClickListener {
            showCreateAlertDialog()
        }

        btnVoiceSearch.setOnClickListener {
            checkVoicePermissionAndStart()
        }

        btnFilter.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_market_filter, null)
        
        val btnApply = view.findViewById<MaterialButton>(R.id.btn_apply_filter)
        val btnReset = view.findViewById<MaterialButton>(R.id.btn_reset_filter)
        val sortGroup = view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_sort_price)
        val chipRising = view.findViewById<com.google.android.material.chip.Chip>(R.id.chip_rising)
        val chipFalling = view.findViewById<com.google.android.material.chip.Chip>(R.id.chip_falling)

        // Restore state
        if (selectedSort == 1) sortGroup.check(R.id.btn_sort_high)
        else if (selectedSort == 2) sortGroup.check(R.id.btn_sort_low)
        
        chipRising.isChecked = selectedTrend == "Rising"
        chipFalling.isChecked = selectedTrend == "Falling"

        btnApply.setOnClickListener {
            selectedSort = when (sortGroup.checkedButtonId) {
                R.id.btn_sort_high -> 1
                R.id.btn_sort_low -> 2
                else -> 0
            }
            selectedTrend = if (chipRising.isChecked) "Rising" else if (chipFalling.isChecked) "Falling" else null
            
            applyFilters()
            dialog.dismiss()
        }

        btnReset.setOnClickListener {
            selectedSort = 0
            selectedTrend = null
            applyFilters()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun applyFilters() {
        val query = etSearch.text.toString()
        var filtered = allMarketData.filter { 
            (query.isEmpty() && it.category == currentCategory) || 
            (query.isNotEmpty() && it.record.commodity?.contains(query, ignoreCase = true) == true)
        }

        // Apply Trend Filter
        if (selectedTrend != null) {
            filtered = filtered.filter { 
                if (selectedTrend == "Rising") it.isRising else !it.isRising
            }
        }

        // Apply Sort
        filtered = when (selectedSort) {
            1 -> filtered.sortedByDescending { it.record.modalPrice?.toIntOrNull() ?: 0 }
            2 -> filtered.sortedBy { it.record.modalPrice?.toIntOrNull() ?: 0 }
            else -> filtered
        }

        displayData = filtered
        displayGenericSection(if(query.isEmpty()) currentCategory else "Search Results", displayData)
    }

    private fun checkVoicePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            LocationUtils.showVoiceDisclosure(
                this,
                onAgree = { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1005) },
                onCancel = { Toast.makeText(this, R.string.voice_permission_denied, Toast.LENGTH_SHORT).show() }
            )
        } else {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a crop name")
            }
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    if (query.length >= 3) {
                        viewModel.searchMarketPrices(query)
                    } else {
                        applyFilters()
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 600)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun switchCategory(category: String) {
        currentCategory = category
        val buttons = listOf(btnVeg, btnFruits, btnGrains)
        buttons.forEach { 
            it.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
            it.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        val activeBtn = when(category) {
            "Vegetables" -> btnVeg
            "Fruits" -> btnFruits
            else -> btnGrains
        }
        activeBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)
        activeBtn.setTextColor(ContextCompat.getColor(this, R.color.black))

        applyFilters()
    }

    private fun observeViewModel() {
        viewModel.allProcessedMarketData.observe(this) { data ->
            allMarketData = data
            applyFilters()
        }

        viewModel.marketStats.observe(this) { stats ->
            tvRisingCount.text = stats.risingCount.toString()
            tvFallingCount.text = stats.fallingCount.toString()
            tvBestDealCount.text = stats.bestDealCount.toString()
        }

        viewModel.recommendedCropPrices.observe(this) { cropPricesMap ->
            updateRecommendedUI(cropPricesMap)
        }

        viewModel.isLoading.observe(this) { loading ->
            pbLocalPrices.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { msg ->
            if (!isFinishing) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayGenericSection(title: String, items: List<MarketPriceInfo>) {
        genericSectionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        
        val headerView = inflater.inflate(R.layout.item_section_header, genericSectionsContainer, false)
        headerView.findViewById<TextView>(R.id.tv_section_title).text = title
        headerView.findViewById<TextView>(R.id.tv_section_count).text = "${items.size} items"
        genericSectionsContainer.addView(headerView)

        if (items.isEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "No results found. Try clearing filters or search."
            emptyTv.setPadding(32, 64, 32, 32)
            emptyTv.textAlignment = View.TEXT_ALIGNMENT_CENTER
            genericSectionsContainer.addView(emptyTv)
            return
        }

        items.forEach { info ->
            val itemView = inflater.inflate(R.layout.item_market_card_generic, genericSectionsContainer, false)
            populateGenericCard(itemView, info)
            genericSectionsContainer.addView(itemView)
        }
    }

    private fun populateGenericCard(view: View, info: MarketPriceInfo) {
        val record = info.record
        view.findViewById<TextView>(R.id.tv_crop_name_generic).text = translateCropName(record.commodity ?: "")
        view.findViewById<TextView>(R.id.tv_market_location_generic).text = "📍 ${record.market}, ${record.state}"
        view.findViewById<TextView>(R.id.tv_price_value_generic).text = "₹${record.modalPrice}"
        
        val tagView = view.findViewById<TextView>(R.id.tv_price_tag_generic)
        tagView.text = info.priceTag
        
        when(info.priceTag) {
            "Good Price" -> {
                tagView.setBackgroundResource(R.drawable.bg_rounded_green_light)
                tagView.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
            }
            "Low Price" -> {
                tagView.setBackgroundResource(R.drawable.bg_loss_badge)
                tagView.setTextColor(android.graphics.Color.RED)
            }
            else -> {
                tagView.setBackgroundResource(R.drawable.bg_pill)
                tagView.setTextColor(android.graphics.Color.GRAY)
            }
        }

        val trendView = view.findViewById<TextView>(R.id.tv_price_trend_generic)
        val trendIcon = view.findViewById<ImageView>(R.id.iv_trend_generic)
        trendView.text = String.format("%.1f%%", Math.abs(info.trendPercentage))
        
        if (info.isRising) {
            trendView.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            trendIcon.setImageResource(R.drawable.ic_trend_up)
            trendIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
        } else {
            trendView.setTextColor(android.graphics.Color.RED)
            trendIcon.setImageResource(R.drawable.ic_trend_down)
            trendIcon.setColorFilter(android.graphics.Color.RED)
        }
    }

    private fun updateRecommendedUI(cropPricesMap: Map<String, MarketPriceInfo?>) {
        recPricesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        cropPricesMap.forEach { (cropKey, info) ->
            val itemView = inflater.inflate(R.layout.item_recommended_crop, recPricesContainer, false)
            itemView.findViewById<TextView>(R.id.tv_crop_name).text = translateCropName(cropKey)
            if (info != null) {
                val record = info.record
                itemView.findViewById<TextView>(R.id.tv_market_location).text = "📍 ${record.market}, ${record.state}"
                itemView.findViewById<TextView>(R.id.tv_price_value).text = "₹${record.modalPrice}"
                itemView.findViewById<TextView>(R.id.tv_price_tag).text = info.priceTag
                itemView.findViewById<TextView>(R.id.tv_price_trend).text = String.format("%.1f%%", Math.abs(info.trendPercentage))
            } else {
                itemView.findViewById<TextView>(R.id.tv_market_location).text = "No recent mandi prices found."
                itemView.findViewById<TextView>(R.id.tv_price_value).text = "N/A"
                itemView.findViewById<View>(R.id.tv_price_tag).visibility = View.GONE
                itemView.findViewById<View>(R.id.tv_price_trend).visibility = View.GONE
            }
            recPricesContainer.addView(itemView)
        }
    }

    private fun translateCropName(rawName: String): String {
        val resourceId = resources.getIdentifier("crop_${rawName.lowercase().replace(" ", "")}", "string", packageName)
        return if (resourceId != 0) getString(resourceId) else rawName
    }

    private fun showCreateAlertDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_create_price_alert, null)
        val atvCrop = view.findViewById<AutoCompleteTextView>(R.id.atv_crop_selection)
        val etPrice = view.findViewById<EditText>(R.id.et_target_price)
        val cropNames = allMarketData.map { it.record.commodity ?: "" }.distinct().sorted()
        atvCrop.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cropNames))

        view.findViewById<MaterialButton>(R.id.btn_save_alert).setOnClickListener {
            val crop = atvCrop.text.toString()
            val price = etPrice.text.toString()
            if (crop.isNotEmpty() && price.isNotEmpty()) {
                dialog.dismiss()
                MaterialAlertDialogBuilder(this).setTitle("Alert Set Successfully! 🔔").setMessage("We'll notify you when $crop reaches ₹$price/Qtl.").setPositiveButton("OK", null).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.btn_cancel_alert).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            LocationUtils.showLocationDisclosure(this, onAgree = { requestLocationPermission() }, onCancel = { 
                // RESPECT USER CHOICE: Stop persistent dialogs and fallback
                tvCurrentLocation.text = "National Market"
                viewModel.fetchMarketIntelligence(null)
            })
            return
        }
        enableGPSAndFetch()
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
    }

    private fun enableGPSAndFetch() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { fetchLocationAndStart() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (e: Exception) {}
                } else {
                    fetchLocationAndStart()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
            fetchLocationAndStart()
        }
    }

    private fun fetchLocationAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val state = if (location != null) {
                        try {
                            val geocoder = Geocoder(this@MarketAnalysisActivity, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            addresses?.get(0)?.adminArea
                        } catch (e: Exception) { null }
                    } else null
                    
                    withContext(Dispatchers.Main) {
                        tvCurrentLocation.text = state ?: "National Market"
                        viewModel.fetchMarketIntelligence(state)
                    }
                }
            }
        } else {
            tvCurrentLocation.text = "National Market"
            viewModel.fetchMarketIntelligence(null)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        bottomNav.selectedItemId = R.id.nav_market
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                R.id.nav_market -> true
                R.id.nav_weather -> { startActivity(Intent(this, WeatherInfoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_market
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableGPSAndFetch()
        }
    }
}
