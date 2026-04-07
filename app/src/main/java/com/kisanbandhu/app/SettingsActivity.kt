package com.kisanbandhu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : BaseActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        findViewById<TextView>(R.id.tv_current_lang)?.text = getString(R.string.current_lang_display)

        findViewById<View>(R.id.layout_language_picker)?.setOnClickListener {
            showLanguageDialog()
        }

        findViewById<View>(R.id.btn_privacy_policy)?.setOnClickListener {
            openPrivacyPolicy()
        }

        findViewById<View>(R.id.btn_delete_account)?.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        setupDisplaySettings()
        setupBottomNavigation()
    }

    private fun setupDisplaySettings() {
        val prefs = getSharedPreferences("KB_PREFS", MODE_PRIVATE)
        
        // Dark Mode Logic
        val switchDarkMode = findViewById<SwitchMaterial>(R.id.switch_dark_mode)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        switchDarkMode?.isChecked = isDarkMode
        
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            // Prevent redundant updates if state is same
            if (prefs.getBoolean("dark_mode", false) == isChecked) return@setOnCheckedChangeListener
            
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            val targetMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppCompatDelegate.setDefaultNightMode(targetMode)
            Toast.makeText(this, "Display theme updated", Toast.LENGTH_SHORT).show()
        }

        // Push Notifications Logic
        val switchNotifications = findViewById<SwitchMaterial>(R.id.switch_notifications)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        switchNotifications?.isChecked = notificationsEnabled
        
        switchNotifications?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Push notifications $status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivacyPolicy() {
        val url = getString(R.string.privacy_policy_url)
        if (url.isNullOrEmpty() || !url.startsWith("http")) {
            Toast.makeText(this, "Privacy Policy URL not configured", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open Privacy Policy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteAccountConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_msg)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                dialog.dismiss()
                performActualDeletion()
            }
            .show()
    }

    private fun performActualDeletion() {
        val user = auth.currentUser
        val prefs = getSharedPreferences("KB_PREFS", MODE_PRIVATE)
        val phoneNumber = prefs.getString("user_phone", null)
        
        if (user == null) {
            Toast.makeText(this, "No active session found.", Toast.LENGTH_SHORT).show()
            navigateToWelcome()
            return
        }

        if (phoneNumber == null) {
            user.delete().addOnCompleteListener { navigateToWelcome() }
            return
        }

        db.collection("users").document(phoneNumber).delete()
            .addOnSuccessListener {
                user.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        prefs.edit().clear().apply()
                        Toast.makeText(this, "Account and data deleted permanently", Toast.LENGTH_LONG).show()
                        navigateToWelcome()
                    } else {
                        showReauthDialog()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showReauthDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reauth_required_title)
            .setMessage(R.string.reauth_required_msg)
            .setPositiveButton(R.string.ok) { _, _ ->
                auth.signOut()
                navigateToWelcome()
            }
            .show()
    }

    private fun navigateToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Marathi")
        val codes = arrayOf("en", "hi", "mr")
        val currentLang = LocaleHelper.getLanguage(this)
        val currentIndex = codes.indexOf(currentLang)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languages, if(currentIndex >= 0) currentIndex else 0) { dialog, which ->
                val newCode = codes[which]
                if (newCode == "mr") {
                    Toast.makeText(this, "Marathi language support is coming soon in the next update!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                if (newCode != currentLang) {
                    updateLanguage(newCode)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun updateLanguage(code: String) {
        LocaleHelper.setLocale(this, code)
        val uid = auth.currentUser?.uid
        if (uid != null) {
            // Update the user's preferred language in Firestore
            val phone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)
            if (phone != null) {
                db.collection("users").document(phone).update("language", code)
            } else {
                db.collection("users").document(uid).update("language", code)
            }
        }
        
        // REFRESH FIX: Instead of going to Welcome (which causes logout-like behavior), 
        // we restart the current activity or go back to Main to apply changes.
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
}
