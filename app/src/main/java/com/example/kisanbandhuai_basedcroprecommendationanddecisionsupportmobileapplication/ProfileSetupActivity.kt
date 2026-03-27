package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ProfileSetupActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_profile_setup)

        // Robust phone retrieval
        userPhone = intent.getStringExtra("phone") ?: getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
        Log.d("SETUP_DEBUG", "Phone loaded: $userPhone")

        etName = findViewById(R.id.et_setup_name)
        btnStart = findViewById(R.id.btn_start_farming)
        ivSelectedAvatar = findViewById(R.id.iv_selected_avatar)
        
        setupAvatarSelection()
        setupLocationSelection()
        
        findViewById<MaterialButton>(R.id.btn_get_location).setOnClickListener {
            checkLocationPermission()
        }

        btnStart.setOnClickListener {
            val name = etName.text.toString().trim()
            
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (selectedLocation == null) {
                Toast.makeText(this, "Please select or detect your location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userPhone.isNullOrEmpty()) {
                Toast.makeText(this, "Login session error. Please restart.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            saveProfileAndStart(name, selectedLocation!!)
        }
    }

    private fun setupAvatarSelection() {
        val avatarIds = listOf(R.id.avatar_1, R.id.avatar_2, R.id.avatar_3, R.id.avatar_4, R.id.avatar_5, R.id.avatar_6)
        
        avatarIds.forEachIndexed { index, id ->
            findViewById<ImageView>(id)?.setOnClickListener {
                selectedAvatarName = "av_farmer_${index + 1}"
                Log.d("SETUP_DEBUG", "Selected: $selectedAvatarName")
                
                // Visual feedback for selection list
                avatarIds.forEach { otherId -> 
                    findViewById<ImageView>(otherId)?.setBackgroundResource(R.drawable.white_circle_bg) 
                }
                it.setBackgroundResource(R.drawable.option_item_selector)
                
                // Update Big Preview
                val resId = resources.getIdentifier(selectedAvatarName, "drawable", packageName)
                if (resId != 0) {
                    ivSelectedAvatar.setImageResource(resId)
                    // REMOVE THE GREEN TINT
                    ivSelectedAvatar.imageTintList = null 
                    ivSelectedAvatar.colorFilter = null
                }
            }
        }
    }

    private fun saveProfileAndStart(name: String, location: String) {
        val phone = userPhone ?: return
        
        btnStart.isEnabled = false
        btnStart.text = "Creating Farmer Profile..."

        val userMap = hashMapOf(
            "name" to name,
            "location" to location,
            "profileImageUrl" to selectedAvatarName, 
            "phone" to phone,
            "uid" to (auth.currentUser?.uid ?: ""),
            "registered" to true,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(phone)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("SETUP_DEBUG", "Firestore Save Success")
                getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().putString("user_phone", phone).apply()
                
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("SETUP_DEBUG", "Firestore Error: ${e.message}")
                btnStart.isEnabled = true
                btnStart.text = "START FARMING / शुरू करें"
                Toast.makeText(this, "Permission Error: Check Firestore Rules in Firebase Console", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkLocationPermission() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            selectedLocation = "${addr.locality}, ${addr.adminArea}"
                            Toast.makeText(this, "📍 Location detected!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { Log.e("LOC", "Error", e) }
                }
            }
        }
    }

    private fun setupLocationSelection() {
        val locIds = listOf(R.id.loc_pune, R.id.loc_delhi, R.id.loc_mumbai)
        locIds.forEach { id ->
            findViewById<TextView>(id)?.setOnClickListener {
                locIds.forEach { otherId -> findViewById<TextView>(otherId)?.setBackgroundResource(R.drawable.input_field_bg) }
                it.setBackgroundResource(R.drawable.option_item_selector)
                selectedLocation = (it as TextView).text.toString().replace("📍 ", "")
            }
        }
    }
}