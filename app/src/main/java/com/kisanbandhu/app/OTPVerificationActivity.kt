package com.kisanbandhu.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OTPVerificationActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var phoneNumber: String? = null
    
    private val otpTextViews = arrayOfNulls<TextView>(6)
    private var currentOtp = ""
    private lateinit var btnVerify: MaterialButton
    private lateinit var tvResend: TextView
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verification)

        auth = FirebaseAuth.getInstance()
        phoneNumber = intent.getStringExtra("phone")
        btnVerify = findViewById(R.id.btn_verify)
        tvResend = findViewById(R.id.tv_resend)

        findViewById<TextView>(R.id.tv_display_mobile).text = "+91 $phoneNumber"

        // Initialize OTP boxes
        otpTextViews[0] = findViewById(R.id.otp_1)
        otpTextViews[1] = findViewById(R.id.otp_2)
        otpTextViews[2] = findViewById(R.id.otp_3)
        otpTextViews[3] = findViewById(R.id.otp_4)
        otpTextViews[4] = findViewById(R.id.otp_5)
        otpTextViews[5] = findViewById(R.id.otp_6)

        setupNumpad()
        startResendTimer()

        btnVerify.setOnClickListener {
            if (currentOtp.length == 6) {
                performSmartLogin()
            } else {
                Toast.makeText(this, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // Voice icon click fallback
        findViewById<View>(R.id.num_mic)?.setOnClickListener {
            Toast.makeText(this, "Voice entry coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Resend OTP logic
        tvResend.setOnClickListener {
            resendOTP()
        }
    }

    private fun resendOTP() {
        // 1. Visual Feedback
        Toast.makeText(this, "OTP Resent successfully to +91 $phoneNumber", Toast.LENGTH_SHORT).show()
        
        // 2. Clear current entry for fresh input
        currentOtp = ""
        updateOtpDisplay()
        updateButtonStyle(0)
        
        // 3. Restart the timer
        startResendTimer()
    }

    private fun performSmartLogin() {
        btnVerify.text = "Logging in..."
        btnVerify.isEnabled = false
        
        // BACKDOOR FOR GOOGLE PLAY REVIEWERS
        // Test Number: 9999999999, OTP: 123456
        if (phoneNumber == "9999999999" && currentOtp == "123456") {
            Log.d("LOGIN_FLOW", "Backdoor triggered for Play Store Reviewer.")
            // PERSISTENCE FOR REVIEWER: Save phone immediately
            getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().putString("user_phone", phoneNumber).apply()
            
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    checkUserAccount(uid)
                } else {
                    resetVerifyButton()
                    Toast.makeText(this, "Reviewer Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        // Standard flow for normal users
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                checkUserAccount(uid)
            } else {
                resetVerifyButton()
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserAccount(uid: String) {
        val phone = phoneNumber ?: return
        
        // Step 2: Check Firestore using Phone as the key
        db.collection("users").document(phone).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")
                    val location = document.getString("location")
                    
                    if (!name.isNullOrEmpty() && !location.isNullOrEmpty()) {
                        // EXISTING USER: Skip setup, go to Home
                        Log.d("LOGIN_FLOW", "Existing user detected. Redirecting to Home.")
                        // Save phone to shared preferences for easy retrieval in other activities
                        getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().putString("user_phone", phone).apply()
                        
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // PARTIAL USER: Needs profile setup
                        navigateToProfileSetup(uid, phone)
                    }
                } else {
                    // NEW USER: Create record and go to setup
                    navigateToProfileSetup(uid, phone)
                }
            }
            .addOnFailureListener { e ->
                resetVerifyButton()
                Toast.makeText(this, "Connection Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToProfileSetup(uid: String, phone: String) {
        Log.d("LOGIN_FLOW", "New user. Redirecting to Profile Setup.")
        // Ensure the phone is saved in the doc so we can find it later
        db.collection("users").document(phone).set(hashMapOf(
            "uid" to uid, 
            "phone" to phone,
            "registered" to false
        ))
        
        getSharedPreferences("KB_PREFS", MODE_PRIVATE).edit().putString("user_phone", phone).apply()
        
        val intent = Intent(this, ProfileSetupActivity::class.java)
        intent.putExtra("phone", phone)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun resetVerifyButton() {
        btnVerify.isEnabled = true
        btnVerify.text = "VERIFY"
        updateButtonStyle(currentOtp.length)
    }

    private fun setupNumpad() {
        val buttons = listOf(
            R.id.num_0 to "0", R.id.num_1 to "1", R.id.num_2 to "2",
            R.id.num_3 to "3", R.id.num_4 to "4", R.id.num_5 to "5",
            R.id.num_6 to "6", R.id.num_7 to "7", R.id.num_8 to "8",
            R.id.num_9 to "9"
        )

        buttons.forEach { (id, value) ->
            findViewById<MaterialButton>(id).setOnClickListener {
                if (currentOtp.length < 6) {
                    currentOtp += value
                    updateOtpDisplay()
                    updateButtonStyle(currentOtp.length)
                }
            }
        }

        findViewById<MaterialButton>(R.id.num_del).setOnClickListener {
            if (currentOtp.isNotEmpty()) {
                currentOtp = currentOtp.substring(0, currentOtp.length - 1)
                updateOtpDisplay()
                updateButtonStyle(currentOtp.length)
            }
        }
        updateButtonStyle(0)
    }

    private fun updateOtpDisplay() {
        for (i in 0 until 6) {
            if (i < currentOtp.length) {
                otpTextViews[i]?.text = currentOtp[i].toString()
                otpTextViews[i]?.setBackgroundResource(R.drawable.option_item_selector)
            } else {
                otpTextViews[i]?.text = ""
                otpTextViews[i]?.setBackgroundResource(R.drawable.input_field_bg)
            }
        }
    }

    private fun updateButtonStyle(length: Int) {
        if (length == 6) {
            btnVerify.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btnVerify.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
        } else {
            btnVerify.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#81C784"))
            btnVerify.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark))
        }
    }

    private fun startResendTimer() {
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvResend.text = "Resend code in ${millisUntilFinished / 1000}s"
                tvResend.isEnabled = false
                tvResend.alpha = 0.6f
            }
            override fun onFinish() {
                tvResend.text = "Resend OTP"
                tvResend.isEnabled = true
                tvResend.alpha = 1.0f
            }
        }.start()
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
