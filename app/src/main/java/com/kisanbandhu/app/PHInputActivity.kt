package com.kisanbandhu.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider

class PHInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ph_input)

        val tvValue = findViewById<TextView>(R.id.tv_ph_value)
        val tvStatus = findViewById<TextView>(R.id.tv_ph_status)
        val slider = findViewById<Slider>(R.id.slider_ph)

        slider.addOnChangeListener { _, value, _ ->
            val formattedValue = String.format("%.1f", value)
            tvValue.text = formattedValue
            
            when {
                value < 6.0 -> {
                    tvStatus.text = "Acidic"
                    tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                }
                value <= 7.5 -> {
                    tvStatus.text = "Neutral (Ideal)"
                    tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
                }
                else -> {
                    tvStatus.text = "Alkaline"
                    tvStatus.setBackgroundColor(android.graphics.Color.parseColor("#E8EAF6"))
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#303F9F"))
                }
            }
        }

        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_save).setOnClickListener { finish() }
    }
}