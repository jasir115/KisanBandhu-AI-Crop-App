package com.kisanbandhu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kisanbandhu.app.utils.LocationUtils
import java.util.Locale

class ProfileSetupActivity : BaseActivity() {

    private var selectedLocation: String? = null
    private var selectedAvatarName: String = "av_farmer_1"
    private var userPhone: String? = null
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private lateinit var etName: EditText
    private lateinit var btnStart: MaterialButton
    private lateinit var ivSelectedAvatar: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FIXED: Removed duplicate and incorrect setContentView
        setContentView(R.layout.activity_profile_setup)

        userPhone = intent.getStringExtra("phone") ?: getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
        Log.d("SETUP_DEBUG", "Activity created for: $userPhone")

        etName = findViewById(R.id.et_setup_name)
        btnStart = findViewById(R.id.btn_start_farming)
        ivSelectedAvatar = findViewById(R.id.iv_selected_avatar)
        
        setupAvatarSelection()
        setupLocationSelection()
        
        findViewById<MaterialButton>(R.id.btn_get_location).setOnClickListener {
            // STRICT: Always show disclosure if permission is missing
            checkLocationPermission()
        }

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateButtonStyle() }
        })

        btnStart.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty() || selectedLocation == null || userPhone.isNullOrEmpty()) {
                Toast.makeText(this, "Please complete all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveProfileAndStart(name, selectedLocation!!)
        }
        updateButtonStyle()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            LocationUtils.showLocationDisclosure(
                this,
                onAgree = { requestLocationPermission() },
                onCancel = { Toast.makeText(this, "Please select a location manually", Toast.LENGTH_SHORT).show() }
            )
        } else {
            getCurrentLocation()
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1002)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    try {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            selectedLocation = "${addr.locality}, ${addr.adminArea}"
                            Toast.makeText(this, "📍 Location detected: $selectedLocation", Toast.LENGTH_SHORT).show()
                            updateButtonStyle()
                        }
                    } catch (e: Exception) { Log.e("LOC", "Error", e) }
                }
            }
        }
    }

    private fun updateButtonStyle() {
        val name = etName.text.toString().trim()
        val isFormValid = name.isNotEmpty() && selectedLocation != null
        if (isFormValid) {
            btnStart.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btnStart.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
        } else {
            btnStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81C784"))
            btnStart.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
        }
    }

    private fun setupAvatarSelection() {
        val avatarIds = listOf(R.id.avatar_1, R.id.avatar_2, R.id.avatar_3, R.id.avatar_4, R.id.avatar_5, R.id.avatar_6)
        avatarIds.forEachIndexed { index, id ->
            findViewById<ImageView>(id)?.setOnClickListener {
                selectedAvatarName = "av_farmer_${index + 1}"
                avatarIds.forEach { otherId -> findViewById<ImageView>(otherId)?.setBackgroundResource(R.drawable.white_circle_bg) }
                it.setBackgroundResource(R.drawable.option_item_selector)
                val resId = resources.getIdentifier(selectedAvatarName, "drawable", packageName)
                if (resId != 0) {
                    ivSelectedAvatar.setImageResource(resId)
                    ivSelectedAvatar.imageTintList = null 
                    ivSelectedAvatar.colorFilter = null
                }
                updateButtonStyle()
            }
        }
    }

    private fun saveProfileAndStart(name: String, location: String) {
        val phone = userPhone ?: return
        btnStart.isEnabled = false
        btnStart.text = "Saving..."
        val userMap = hashMapOf("name" to name, "location" to location, "profileImageUrl" to selectedAvatarName, "phone" to phone, "uid" to (auth.currentUser?.uid ?: ""), "registered" to true, "createdAt" to System.currentTimeMillis())
        db.collection("users").document(phone).set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().putString("user_phone", phone).apply()
                startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                finish()
            }
            .addOnFailureListener { btnStart.isEnabled = true; btnStart.text = "START FARMING" }
    }

    private fun setupLocationSelection() {
        val locIds = listOf(R.id.loc_pune, R.id.loc_delhi, R.id.loc_mumbai)
        locIds.forEach { id ->
            findViewById<TextView>(id)?.setOnClickListener {
                locIds.forEach { otherId -> findViewById<TextView>(otherId)?.setBackgroundResource(R.drawable.input_field_bg) }
                it.setBackgroundResource(R.drawable.option_item_selector)
                selectedLocation = (it as TextView).text.toString().replace("📍 ", "")
                updateButtonStyle()
            }
        }
    }
}
