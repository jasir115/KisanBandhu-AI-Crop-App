package com.kisanbandhu.app

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import coil.load
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kisanbandhu.app.R
import com.kisanbandhu.app.utils.LocationUtils

class MainActivity : SwipeableActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val weatherViewModel: WeatherViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val REQUEST_CHECK_SETTINGS = 0x1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Card button clicks
        findViewById<MaterialCardView>(R.id.btn_crop_recommendation)?.setOnClickListener {
            startActivity(Intent(this, CropRecommendationActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.btn_crop_health)?.setOnClickListener {
            try {
                val intent = Intent(this, CropHealthActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                if (!isFinishing) {
                    Toast.makeText(this, "Error starting scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }

        findViewById<MaterialCardView>(R.id.btn_market_analysis)?.setOnClickListener {
            val intent = Intent(this, MarketAnalysisActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        // Location click to update
        findViewById<View>(R.id.layout_location)?.setOnClickListener {
            checkLocationPermission()
        }

        setupBottomNavigation()
        observeWeather()
        loadUserProfile()
        
        // Initial location check
        checkLocationPermission()
    }

    private fun loadUserProfile() {
        val phone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
        
        if (phone == null) {
            val uid = auth.currentUser?.uid ?: return
            db.collection("users").whereEqualTo("uid", uid).limit(1).get()
                .addOnSuccessListener { query ->
                    if (!isFinishing && !query.isEmpty) {
                        val doc = query.documents[0]
                        updateUI(doc.getString("name"), doc.getString("profileImageUrl"))
                    }
                }
            return
        }

        db.collection("users").document(phone).addSnapshotListener { document, error ->
            if (isFinishing) return@addSnapshotListener
            
            if (error != null) {
                Log.e("MAIN_FS", "Error: ${error.message}")
                return@addSnapshotListener
            }
            
            if (document != null && document.exists()) {
                val name = document.getString("name")
                val avatarName = document.getString("profileImageUrl")
                updateUI(name, avatarName)
            }
        }
    }

    private fun updateUI(name: String?, avatarName: String?) {
        if (isFinishing) return
        
        if (!name.isNullOrEmpty()) {
            val welcomeText = getString(R.string.welcome) + " " + name + "!"
            findViewById<TextView>(R.id.tv_welcome_name)?.text = welcomeText
        }
        
        val ivLogo = findViewById<ImageView>(R.id.iv_main_avatar) ?: return
        
        if (!avatarName.isNullOrEmpty()) {
            val resId = resources.getIdentifier(avatarName, "drawable", packageName)
            if (resId != 0) {
                ivLogo.setImageResource(resId)
                ivLogo.imageTintList = null 
                ivLogo.clearColorFilter()
            }
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            LocationUtils.showLocationDisclosure(
                this,
                onAgree = { requestLocationPermission() },
                onCancel = { 
                    // RESPECT USER CHOICE: Stop persistent dialogs and fallback
                    if (!isFinishing) {
                        weatherViewModel.fetchWeather("Pune")
                    }
                }
            )
            return
        }
        enableGPSAndGetLocation()
    }

    private fun enableGPSAndGetLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { 
            if (!isFinishing) updateLocationAndWeather()
        }

        task.addOnFailureListener { exception ->
            if (!isFinishing && exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                }
            }
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            1002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                updateLocationAndWeather()
            } else {
                if (!isFinishing) {
                    Toast.makeText(this, "Location must be turned on for weather updates", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableGPSAndGetLocation()
        }
    }

    private fun updateLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (isFinishing) return@addOnSuccessListener
                    if (location != null) {
                        weatherViewModel.fetchWeather("${location.latitude},${location.longitude}")
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (!isFinishing) {
                                if (lastLoc != null) {
                                    weatherViewModel.fetchWeather("${lastLoc.latitude},${lastLoc.longitude}")
                                } else {
                                    weatherViewModel.fetchWeather("Pune")
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    if (!isFinishing) weatherViewModel.fetchWeather("Pune")
                }
        }
    }

    private fun observeWeather() {
        weatherViewModel.weatherData.observe(this) { data ->
            if (isFinishing) return@observe
            if (data != null) {
                findViewById<TextView>(R.id.tv_current_location)?.text = "${getString(R.string.location_prefix)}: ${data.location.name}, ${data.location.region}"
                findViewById<TextView>(R.id.tv_location_mode)?.text = ""
                findViewById<TextView>(R.id.tv_weather_temp_main)?.text = "${data.current.tempC.toInt()}°C | ${data.current.condition.text}"
                findViewById<TextView>(R.id.tv_weather_details_main)?.text = "Humidity: ${data.current.humidity}% | Wind: ${data.current.windKph.toInt()} km/h"
                val iconUrl = "https:${data.current.condition.icon}".replace("64x64", "128x128")
                findViewById<ImageView>(R.id.iv_weather_icon_main)?.load(iconUrl)
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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

    override fun onResume() {
        super.onResume()
        findViewById<BottomNavigationView>(R.id.bottom_navigation)?.selectedItemId = R.id.nav_home
    }
}
