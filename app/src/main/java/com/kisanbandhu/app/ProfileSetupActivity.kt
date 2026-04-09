package com.kisanbandhu.app

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kisanbandhu.app.R
import com.kisanbandhu.app.utils.LocationUtils
import java.util.*

class ProfileSetupActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var ivSelectedAvatar: ImageView
    private lateinit var tvPhoneDisplay: TextView
    private lateinit var btnStartFarming: MaterialButton
    private var selectedAvatarName = "av_farmer_1"
    private var selectedLocation = "Pune, Maharashtra"
    private var userPhone: String = ""
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private val REQUEST_CHECK_SETTINGS = 0x2
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        etName = findViewById(R.id.et_setup_name)
        ivSelectedAvatar = findViewById(R.id.iv_selected_avatar)
        tvPhoneDisplay = findViewById(R.id.tv_setup_phone_display)
        btnStartFarming = findViewById(R.id.btn_start_farming)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        userPhone = intent.getStringExtra("phone") ?: getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", "") ?: ""
        tvPhoneDisplay.text = "Logged in as: +91 $userPhone"

        setupAvatarSelection()
        setupLocationSelection()

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkFormValidity()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnStartFarming.setOnClickListener {
            saveProfileAndProceed()
        }
        
        checkFormValidity()
    }

    private fun checkFormValidity() {
        val name = etName.text.toString().trim()
        val isValid = name.isNotEmpty()
        
        btnStartFarming.isEnabled = isValid
        if (isValid) {
            btnStartFarming.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btnStartFarming.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
            btnStartFarming.alpha = 1.0f
        } else {
            btnStartFarming.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81C784"))
            btnStartFarming.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
            btnStartFarming.alpha = 0.6f
        }
    }

    private fun setupAvatarSelection() {
        val avatarIds = listOf(R.id.avatar_1, R.id.avatar_2, R.id.avatar_3, R.id.avatar_4, R.id.avatar_5, R.id.avatar_6)
        val avatarNames = listOf("av_farmer_1", "av_farmer_2", "av_farmer_3", "av_farmer_4", "av_farmer_5", "av_farmer_6")

        avatarIds.forEachIndexed { index, id ->
            findViewById<ImageView>(id).setOnClickListener {
                selectedAvatarName = avatarNames[index]
                val resId = resources.getIdentifier(selectedAvatarName, "drawable", packageName)
                if (resId != 0) {
                    ivSelectedAvatar.setImageResource(resId)
                }
                
                avatarIds.forEach { fid -> 
                    findViewById<ImageView>(fid).setBackgroundResource(R.drawable.white_circle_bg)
                }
                findViewById<ImageView>(id).setBackgroundResource(R.drawable.option_item_selector)
                checkFormValidity()
            }
        }
    }

    private fun setupLocationSelection() {
        val locPune = findViewById<TextView>(R.id.loc_pune)
        val locDelhi = findViewById<TextView>(R.id.loc_delhi)
        val locMumbai = findViewById<TextView>(R.id.loc_mumbai)
        val btnDetect = findViewById<View>(R.id.btn_get_location)

        val locs = listOf(locPune, locDelhi, locMumbai)
        locs.forEach { view ->
            view.setOnClickListener {
                locs.forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
                view.setBackgroundResource(R.drawable.option_item_selector)
                selectedLocation = view.text.toString().replace("📍 ", "")
                checkFormValidity()
            }
        }

        btnDetect.setOnClickListener {
            checkLocationPermission()
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            LocationUtils.showLocationDisclosure(
                this,
                onAgree = { requestLocationPermission() },
                onCancel = { /* Just stay with manual choice */ }
            )
            return
        }
        enableGPSAndDetect()
    }

    private fun enableGPSAndDetect() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener { detectCurrentLocation() }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (e: IntentSender.SendIntentException) {}
                }
            }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1003)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
            detectCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1003 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableGPSAndDetect()
        }
    }

    private fun detectCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val city = address.locality ?: address.subAdminArea ?: "Unknown City"
                            val state = address.adminArea ?: ""
                            selectedLocation = if (state.isNotEmpty()) "$city, $state" else city
                            Toast.makeText(this, "Detected: $selectedLocation", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedLocation = "Detected Location"
                            Toast.makeText(this, "Location detected!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        selectedLocation = "Detected Location"
                        Toast.makeText(this, "Location detected!", Toast.LENGTH_SHORT).show()
                    }
                    
                    val locPune = findViewById<TextView>(R.id.loc_pune)
                    val locDelhi = findViewById<TextView>(R.id.loc_delhi)
                    val locMumbai = findViewById<TextView>(R.id.loc_mumbai)
                    listOf(locPune, locDelhi, locMumbai).forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
                    
                    checkFormValidity()
                } else {
                    Toast.makeText(this, "Could not get location. Please select manually.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveProfileAndProceed() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        if (userPhone.isEmpty()) {
            Toast.makeText(this, "Session error. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }
        
        btnStartFarming.isEnabled = false
        
        val userMap = hashMapOf(
            "uid" to (auth.currentUser?.uid ?: ""),
            "name" to name,
            "phone" to userPhone,
            "profileImageUrl" to selectedAvatarName,
            "location" to selectedLocation,
            "language" to LocaleHelper.getLanguage(this),
            "farmSize" to 0.0,
            "farmSizeUnit" to "Acres",
            "registered" to true
        )

        db.collection("users").document(userPhone).set(userMap)
            .addOnSuccessListener {
                getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit()
                    .putString("user_phone", userPhone)
                    .putBoolean("is_logged_in", true)
                    .apply()
                
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                btnStartFarming.isEnabled = true
                Toast.makeText(this, "Failed to save profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
