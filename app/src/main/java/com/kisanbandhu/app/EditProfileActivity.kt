package com.kisanbandhu.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var btnSaveChanges: MaterialButton
    private lateinit var ivAvatarPreview: ImageView
    private var selectedLanguageCode = "en"
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.et_profile_name)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        ivAvatarPreview = findViewById(R.id.iv_avatar_preview)
        
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Profile photo update placeholder
        findViewById<MaterialButton>(R.id.btn_take_photo).setOnClickListener {
            Toast.makeText(this, "Profile photo update coming soon!", Toast.LENGTH_SHORT).show()
        }

        setupLanguageSelection()

        btnSaveChanges.setOnClickListener {
            saveProfile()
        }

        loadCurrentProfile()
    }

    private fun setupLanguageSelection() {
        val langEnglish = findViewById<RelativeLayout>(R.id.lang_english_container)
        val langHindi = findViewById<RelativeLayout>(R.id.lang_hindi_container)
        val langMarathi = findViewById<RelativeLayout>(R.id.lang_marathi_container)

        selectedLanguageCode = LocaleHelper.getLanguage(this)
        updateUI(selectedLanguageCode)

        langEnglish.setOnClickListener { 
            selectedLanguageCode = "en"
            updateUI("en") 
        }
        
        langHindi.setOnClickListener { 
            selectedLanguageCode = "hi"
            updateUI("hi") 
        }
        
        langMarathi.setOnClickListener { 
            selectedLanguageCode = "mr"
            updateUI("mr")
            Toast.makeText(this, "Marathi language support is coming soon! Please select English or Hindi to save changes.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadCurrentProfile() {
        val phone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null) ?: return
        
        db.collection("users").document(phone).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    etName.setText(document.getString("name"))
                    val profileImageUrl = document.getString("profileImageUrl")
                    if (!profileImageUrl.isNullOrEmpty()) {
                        val resId = resources.getIdentifier(profileImageUrl, "drawable", packageName)
                        if (resId != 0) {
                            ivAvatarPreview.setImageResource(resId)
                        } else {
                            ivAvatarPreview.load(profileImageUrl) {
                                transformations(CircleCropTransformation())
                            }
                        }
                    }
                    val code = document.getString("language") ?: "en"
                    selectedLanguageCode = code
                    updateUI(code)
                }
            }
    }

    private fun updateUI(code: String) {
        val langEnglish = findViewById<RelativeLayout>(R.id.lang_english_container)
        val langHindi = findViewById<RelativeLayout>(R.id.lang_hindi_container)
        val langMarathi = findViewById<RelativeLayout>(R.id.lang_marathi_container)

        val checkEnglish = findViewById<ImageView>(R.id.lang_english_check)
        val checkHindi = findViewById<ImageView>(R.id.lang_hindi_check)
        val checkMarathi = findViewById<ImageView>(R.id.lang_marathi_check)

        val containers = listOf(langEnglish, langHindi, langMarathi)
        val checks = listOf(checkEnglish, checkHindi, checkMarathi)

        // Reset all
        containers.forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
        checks.forEach { it.visibility = View.GONE }

        // Handle selection and Save Button state
        when (code) {
            "en" -> {
                langEnglish.setBackgroundResource(R.drawable.option_item_selector)
                checkEnglish.visibility = View.VISIBLE
                enableSaveButton(true)
            }
            "hi" -> {
                langHindi.setBackgroundResource(R.drawable.option_item_selector)
                checkHindi.visibility = View.VISIBLE
                enableSaveButton(true)
            }
            "mr" -> {
                langMarathi.setBackgroundResource(R.drawable.option_item_selector)
                checkMarathi.visibility = View.VISIBLE
                enableSaveButton(false) // Disable for Marathi
            }
        }
    }

    private fun enableSaveButton(enable: Boolean) {
        btnSaveChanges.isEnabled = enable
        if (enable) {
            btnSaveChanges.alpha = 1.0f
            // Resets to your theme's dark green/primary color
        } else {
            btnSaveChanges.alpha = 0.5f // Dim the button to indicate it's inactive
        }
    }

    private fun saveProfile() {
        val phone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
        if (phone == null) {
            Toast.makeText(this, "Session error. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveChanges.isEnabled = false
        btnSaveChanges.text = "Saving..."

        LocaleHelper.setLocale(this, selectedLanguageCode)

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "language" to selectedLanguageCode
        )

        db.collection("users").document(phone)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                enableSaveButton(true)
                btnSaveChanges.text = "SAVE CHANGES"
            }
    }
}
