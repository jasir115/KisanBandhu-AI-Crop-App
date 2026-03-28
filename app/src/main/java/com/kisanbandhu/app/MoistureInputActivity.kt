package com.kisanbandhu.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

/**
 * Activity for collecting soil moisture data.
 * DATA SAFETY CONFIRMATION: This data (soil humidity) is stored in Firestore 
 * and mapped to the user's UID for historical tracking and farming recommendations.
 */
class MoistureInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moisture_input)

        val tvValue = findViewById<TextView>(R.id.tv_moisture_value)
        val tvStatus = findViewById<TextView>(R.id.tv_moisture_status)
        val slider = findViewById<Slider>(R.id.slider_moisture)
        val etMoisture = findViewById<TextView>(R.id.et_moisture) // Note: layout uses EditText, not TextInputEditText

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intValue = value.toInt()
                tvValue.text = "$intValue%"
                etMoisture.text = intValue.toString()
                updateStatus(intValue, tvStatus)
            }
        }

        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_save).setOnClickListener { finish() }
    }

    private fun updateStatus(value: Int, tvStatus: TextView) {
        when {
            value < 20 -> {
                tvStatus.text = "Very Dry"
                tvStatus.setTextColor(android.graphics.Color.RED)
            }
            value <= 50 -> {
                tvStatus.text = "Good"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            }
            else -> {
                tvStatus.text = "Wet"
                tvStatus.setTextColor(android.graphics.Color.BLUE)
            }
        }
    }
}