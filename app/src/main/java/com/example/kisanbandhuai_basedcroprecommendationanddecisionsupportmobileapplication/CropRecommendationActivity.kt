package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider

class CropRecommendationActivity : BaseActivity() {

    private lateinit var cardSelectSource: MaterialCardView
    private lateinit var layoutInputForm: LinearLayout
    private lateinit var btnAnalyze: MaterialButton
    private lateinit var tvSelectedSourceName: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val weatherViewModel: WeatherViewModel by viewModels()

    // Parameters for ML model
    private var nitrogen: Int = 90
    private var phosphorus: Int = 45
    private var potassium: Int = 43
    private var phLevel: Double = 6.8
    private var temperature: Double = 28.0
    private var humidity: Double = 60.0
    private var rainfall: Double = 100.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_recommendation)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cardSelectSource = findViewById(R.id.card_select_source)
        layoutInputForm = findViewById(R.id.layout_input_form)
        btnAnalyze = findViewById(R.id.btn_analyze)
        tvSelectedSourceName = findViewById(R.id.tv_selected_source_name)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_choose_source).setOnClickListener {
            showChooseSourceDialog()
        }

        findViewById<View>(R.id.btn_change_source)?.setOnClickListener {
            showChooseSourceDialog()
        }

        // Setup individual input clicks
        findViewById<View>(R.id.btn_npk).setOnClickListener { showNPKBottomSheet() }
        findViewById<View>(R.id.btn_ph).setOnClickListener { showPHBottomSheet() }
        findViewById<View>(R.id.btn_humidity).setOnClickListener { showHumidityBottomSheet() }

        setupBottomNavigation()
        observeWeather()
        fetchLocationAndWeather()

        btnAnalyze.setOnClickListener {
            val intent = Intent(this, RecommendationResultActivity::class.java).apply {
                putExtra("N", nitrogen)
                putExtra("P", phosphorus)
                putExtra("K", potassium)
                putExtra("PH", phLevel)
                putExtra("TEMP", temperature)
                putExtra("HUMIDITY", humidity)
                putExtra("RAINFALL", rainfall)
            }
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    true
                }
                R.id.nav_market -> {
                    val intent = Intent(this, MarketAnalysisActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    true
                }
                R.id.nav_weather -> {
                    val intent = Intent(this, WeatherInfoActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    true
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    weatherViewModel.fetchWeather("${location.latitude},${location.longitude}")
                } else {
                    weatherViewModel.fetchWeather("Pune")
                }
            }
        }
    }

    private fun observeWeather() {
        weatherViewModel.weatherData.observe(this) { data ->
            if (data != null) {
                temperature = data.current.tempC
                humidity = data.current.humidity.toDouble()
                rainfall = data.forecast.forecastDay.getOrNull(0)?.day?.totalPrecipMm ?: data.current.precipMm
                if (rainfall == 0.0) rainfall = 100.0
                updateLiveUI()
            }
        }
    }

    private fun updateLiveUI() {
        findViewById<TextView>(R.id.tv_live_temp)?.text = "${temperature.toInt()}°C"
        findViewById<TextView>(R.id.tv_live_rainfall)?.text = "${rainfall.toInt()} mm"
        findViewById<TextView>(R.id.tv_humidity_summary)?.text = "$humidity %"
    }

    private fun showChooseSourceDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_choose_data_source, null)
        
        view.findViewById<LinearLayout>(R.id.option_soil_test_kit).setOnClickListener {
            updateSource("Soil Test Kit")
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.option_lab_report).setOnClickListener {
            updateSource("Lab Test Report")
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.option_previous_records).setOnClickListener {
            updateSource("Previous Records")
            dialog.dismiss()
        }
        
        view.findViewById<LinearLayout>(R.id.option_estimate_values).setOnClickListener {
            updateSource("Estimated Values")
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun updateSource(sourceName: String) {
        tvSelectedSourceName.text = "Data Source: $sourceName"
        switchToInputForm()
    }

    private fun showNPKBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.activity_npk_input, null)
        val etN = view.findViewById<EditText>(R.id.et_nitrogen)
        val etP = view.findViewById<EditText>(R.id.et_phosphorus)
        val etK = view.findViewById<EditText>(R.id.et_potassium)

        etN.setText(nitrogen.toString())
        etP.setText(phosphorus.toString())
        etK.setText(potassium.toString())

        view.findViewById<ImageButton>(R.id.btn_n_up).setOnClickListener { changeValue(etN, 1) }
        view.findViewById<ImageButton>(R.id.btn_n_down).setOnClickListener { changeValue(etN, -1) }
        view.findViewById<ImageButton>(R.id.btn_p_up).setOnClickListener { changeValue(etP, 1) }
        view.findViewById<ImageButton>(R.id.btn_p_down).setOnClickListener { changeValue(etP, -1) }
        view.findViewById<ImageButton>(R.id.btn_k_up).setOnClickListener { changeValue(etK, 1) }
        view.findViewById<ImageButton>(R.id.btn_k_down).setOnClickListener { changeValue(etK, -1) }

        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            nitrogen = etN.text.toString().toIntOrNull() ?: nitrogen
            phosphorus = etP.text.toString().toIntOrNull() ?: phosphorus
            potassium = etK.text.toString().toIntOrNull() ?: potassium
            findViewById<TextView>(R.id.tv_npk_summary)?.text = "N: $nitrogen | P: $phosphorus | K: $potassium"
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun changeValue(editText: EditText, delta: Int) {
        val currentVal = editText.text.toString().toIntOrNull() ?: 0
        editText.setText((currentVal + delta).coerceAtLeast(0).toString())
    }

    private fun showPHBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.activity_ph_input, null)
        val tvValue = view.findViewById<TextView>(R.id.tv_ph_value)
        val slider = view.findViewById<Slider>(R.id.slider_ph)
        slider.value = phLevel.toFloat()
        tvValue.text = String.format("%.1f", phLevel)
        slider.addOnChangeListener { _, value, _ -> tvValue.text = String.format("%.1f", value) }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            phLevel = slider.value.toDouble()
            findViewById<TextView>(R.id.tv_ph_summary)?.text = String.format("%.1f", phLevel)
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showHumidityBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.activity_moisture_input, null)
        val tvValue = view.findViewById<TextView>(R.id.tv_moisture_value)
        val slider = view.findViewById<Slider>(R.id.slider_moisture)
        val etHum = view.findViewById<EditText>(R.id.et_moisture)
        slider.value = humidity.toFloat()
        etHum.setText(humidity.toInt().toString())
        tvValue.text = "${humidity.toInt()}%"
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                tvValue.text = "${value.toInt()}%"
                etHum.setText(value.toInt().toString())
            }
        }
        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            humidity = etHum.text.toString().toDoubleOrNull() ?: humidity
            findViewById<TextView>(R.id.tv_humidity_summary)?.text = "$humidity %"
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun switchToInputForm() {
        cardSelectSource.visibility = View.GONE
        layoutInputForm.visibility = View.VISIBLE
        btnAnalyze.visibility = View.VISIBLE
    }
}