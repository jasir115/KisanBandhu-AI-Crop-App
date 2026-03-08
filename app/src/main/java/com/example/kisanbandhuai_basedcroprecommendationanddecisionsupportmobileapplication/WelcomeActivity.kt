package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class WelcomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // Using MaterialButton instead of Button since it's defined as MaterialButton in XML
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_start).setOnClickListener {
            // Start the onboarding/login flow
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
        }
        
        // Also handle the language change button if clicked directly
        findViewById<android.widget.LinearLayout>(R.id.btn_change_language).setOnClickListener {
            startActivity(Intent(this, LanguageSelectionActivity::class.java))
        }
    }
}