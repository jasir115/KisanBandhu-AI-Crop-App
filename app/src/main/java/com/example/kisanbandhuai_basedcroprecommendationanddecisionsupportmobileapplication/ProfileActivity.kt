package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : SwipeableActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvName: TextView
    private lateinit var tvLocation: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvPhone: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvFarmSize: TextView
    private lateinit var tvCurrentCrops: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Views
        tvName = findViewById(R.id.tv_profile_name)
        tvLocation = findViewById(R.id.tv_profile_location)
        ivAvatar = findViewById(R.id.iv_profile_avatar)
        tvPhone = findViewById(R.id.tv_profile_phone)
        tvEmail = findViewById(R.id.tv_profile_email)
        tvFarmSize = findViewById(R.id.tv_profile_farm_size_val)
        tvCurrentCrops = findViewById(R.id.tv_profile_current_crops_val)

        setupBottomNavigation()
        loadUserProfile()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Edit Farm Information
        findViewById<View>(R.id.btn_edit_farm)?.setOnClickListener {
            startActivity(Intent(this, FarmInfoActivity::class.java))
        }

        // Edit Contact Information
        findViewById<View>(R.id.btn_edit_contact)?.setOnClickListener {
            startActivity(Intent(this, ContactInfoActivity::class.java))
        }

        // Menu: Edit Profile
        findViewById<View>(R.id.btn_menu_edit_profile)?.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Menu: Crop History
        findViewById<View>(R.id.btn_menu_crop_history)?.setOnClickListener {
            startActivity(Intent(this, CropHistoryActivity::class.java))
        }

        // Menu: Settings
        findViewById<View>(R.id.btn_menu_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Menu: Help & Support
        findViewById<View>(R.id.btn_menu_help)?.setOnClickListener {
            startActivity(Intent(this, HelpSupportActivity::class.java))
        }

        // Logout
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            auth.signOut()
            // Clear local preferences
            getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().clear().apply()
            
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadUserProfile() {
        // Fetch using Phone instead of UID for consistency
        val phone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
        
        if (phone == null) {
            Log.e("PROFILE_DEBUG", "Phone is null in SharedPreferences")
            // Fallback to searching by UID if phone is not in prefs
            val uid = auth.currentUser?.uid ?: return
            db.collection("users").whereEqualTo("uid", uid).limit(1).get()
                .addOnSuccessListener { query ->
                    if (!query.isEmpty) {
                        val doc = query.documents[0]
                        populateUI(doc)
                    }
                }
            return
        }

        db.collection("users").document(phone).addSnapshotListener { document, error ->
            if (error != null) {
                Log.e("PROFILE_DEBUG", "Firestore Error: ${error.message}")
                return@addSnapshotListener
            }
            
            if (document != null && document.exists()) {
                populateUI(document)
            }
        }
    }

    private fun populateUI(document: com.google.firebase.firestore.DocumentSnapshot) {
        tvName.text = document.getString("name") ?: "Farmer"
        tvLocation.text = "📍 ${document.getString("location") ?: "Not Set"}"
        
        // Use the phone stored in the document, or the ID
        val phone = document.getString("phone") ?: document.id
        tvPhone.text = "+91 $phone"
        
        // Sync Email from Contact Info
        tvEmail.text = document.getString("email") ?: "Not Set"
        
        // Sync Farm Details
        val farmSize = document.get("farmSize") ?: "0"
        val farmSizeUnit = document.getString("farmSizeUnit") ?: "Acres"
        tvFarmSize.text = "$farmSize $farmSizeUnit"
        
        tvCurrentCrops.text = document.getString("currentCrops") ?: "None"

        // Sync Avatar and Clear Tint
        val avatarName = document.getString("profileImageUrl")
        if (!avatarName.isNullOrEmpty()) {
            val resId = resources.getIdentifier(avatarName, "drawable", packageName)
            if (resId != 0) {
                ivAvatar.setImageResource(resId)
                // Remove the green XML tint so the avatar's real colors show
                ivAvatar.imageTintList = null
                ivAvatar.colorFilter = null
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return
        bottomNav.selectedItemId = R.id.nav_profile
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
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}
