package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
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
        
        // Update the current language text view based on string resources
        findViewById<TextView>(R.id.tv_current_lang)?.text = getString(R.string.current_lang_display)

        findViewById<View>(R.id.layout_language_picker)?.setOnClickListener {
            showLanguageDialog()
        }

        setupBottomNavigation()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hindi", "Marathi")
        val codes = arrayOf("en", "hi", "mr")
        val currentLang = LocaleHelper.getLanguage(this)
        val currentIndex = codes.indexOf(currentLang)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languages, if(currentIndex >= 0) currentIndex else 0) { dialog, which ->
                val newCode = codes[which]
                if (newCode != currentLang) {
                    updateLanguage(newCode)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun updateLanguage(code: String) {
        LocaleHelper.setLocale(this, code)
        
        // Update Firestore if user is logged in
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).update("language", code)
        }
        
        // No need to manually restart MainActivity here because AppCompatDelegate.setApplicationLocales 
        // handles activity recreation gracefully for the current stack.
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