package com.kisanbandhu.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.google.android.material.button.MaterialButton

class LanguageSelectionActivity : BaseActivity() {

    private var selectedLanguageCode = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_selection)

        val btnEnglish = findViewById<View>(R.id.lang_english_container)
        val btnHindi = findViewById<View>(R.id.lang_hindi_container)
        val btnMarathi = findViewById<View>(R.id.lang_marathi_container)
        val btnContinue = findViewById<MaterialButton>(R.id.btn_continue)
        
        val currentCode = LocaleHelper.getLanguage(this)
        selectedLanguageCode = currentCode
        updateSelection(currentCode)

        btnEnglish.setOnClickListener {
            updateSelection("en")
            selectedLanguageCode = "en"
        }

        btnHindi.setOnClickListener {
            updateSelection("hi")
            selectedLanguageCode = "hi"
        }

        btnMarathi.setOnClickListener {
            updateSelection("mr")
            selectedLanguageCode = "mr"
        }

        btnContinue.setOnClickListener {
            // Setting locale via AppCompatDelegate
            LocaleHelper.setLocale(this, selectedLanguageCode)
            
            // Go to mobile entry (recreating stack handles the locale apply safely)
            val intent = Intent(this, MobileEntryActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun updateSelection(selectedCode: String) {
        val allContainers = mapOf(
            "en" to R.id.lang_english_container,
            "hi" to R.id.lang_hindi_container,
            "mr" to R.id.lang_marathi_container
        )

        val allCheckmarks = mapOf(
            "en" to R.id.lang_english_check,
            "hi" to R.id.lang_hindi_check,
            "mr" to R.id.lang_marathi_check
        )

        allContainers.forEach { (code, id) ->
            val container = findViewById<View>(id)
            container.setBackgroundResource(R.drawable.input_field_bg)
            findViewById<ImageView>(allCheckmarks[code]!!).visibility = View.GONE
        }

        findViewById<View>(allContainers[selectedCode]!!).setBackgroundResource(R.drawable.option_item_selector)
        findViewById<ImageView>(allCheckmarks[selectedCode]!!).visibility = View.VISIBLE
    }
}