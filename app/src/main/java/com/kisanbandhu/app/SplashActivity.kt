package com.kisanbandhu.app

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kisanbandhu.app.R

class SplashActivity : BaseActivity() {

    private var isNavigationStarted = false
    private val TAG = "SplashDebug"

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
            
            if (progress == 100 && !isNavigationStarted) {
                isNavigationStarted = true
                checkUserStatusAndNavigate()
            }
        }
        animator.start()
    }

    private fun checkUserStatusAndNavigate() {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser != null) {
            Log.d(TAG, "User authenticated: ${currentUser.uid}")
            
            // 1. Get phone from Prefs (Primary ID)
            val prefs = getSharedPreferences("KB_PREFS", MODE_PRIVATE)
            val phone = prefs.getString("user_phone", null)

            if (phone != null) {
                // Check Firestore using Phone ID
                db.collection("users").document(phone).get()
                    .addOnSuccessListener { document ->
                        if (!isFinishing) {
                            if (document.exists() && document.getBoolean("registered") == true) {
                                Log.d(TAG, "Profile found for phone. Moving to Main.")
                                startActivity(Intent(this, MainActivity::class.java))
                            } else {
                                Log.d(TAG, "Partial profile found. Moving to Setup.")
                                startActivity(Intent(this, ProfileSetupActivity::class.java))
                            }
                            finish()
                        }
                    }
                    .addOnFailureListener {
                        fallbackToMain()
                    }
            } else {
                // 2. Fallback: Query by UID if phone is missing from Prefs
                db.collection("users").whereEqualTo("uid", currentUser.uid).limit(1).get()
                    .addOnSuccessListener { query ->
                        if (!isFinishing) {
                            if (!query.isEmpty) {
                                val doc = query.documents[0]
                                val userPhone = doc.getString("phone")
                                if (userPhone != null) {
                                    prefs.edit().putString("user_phone", userPhone).apply()
                                }
                                Log.d(TAG, "Profile found by UID query. Moving to Main.")
                                startActivity(Intent(this, MainActivity::class.java))
                            } else {
                                Log.d(TAG, "No profile found by UID. Moving to Welcome.")
                                startActivity(Intent(this, WelcomeActivity::class.java))
                            }
                            finish()
                        }
                    }
                    .addOnFailureListener { fallbackToMain() }
            }
        } else {
            Log.d(TAG, "No user session. Moving to Welcome.")
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }
    }

    private fun fallbackToMain() {
        if (!isFinishing) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
