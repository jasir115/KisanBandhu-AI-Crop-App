package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<LinearProgressIndicator>(R.id.progress_bar)
        val tvLoading = findViewById<TextView>(R.id.tv_loading)

        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 2000
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            progressBar.progress = progress
            tvLoading.text = "${getString(R.string.loading)} $progress%"
            
            if (progress == 100) {
                checkUserStatusAndNavigate()
            }
        }
        animator.start()
    }

    private fun checkUserStatusAndNavigate() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
        finish()
    }
}