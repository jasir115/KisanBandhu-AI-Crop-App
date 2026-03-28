package com.kisanbandhu.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class NPKInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_npk_input)

        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_save).setOnClickListener { finish() }
    }
}