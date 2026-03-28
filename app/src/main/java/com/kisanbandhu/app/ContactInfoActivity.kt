package com.kisanbandhu.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ContactInfoActivity : AppCompatActivity() {

    private lateinit var tvPrimaryMobile: TextView
    private lateinit var etAltMobile: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSave: MaterialButton
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var userPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_info)

        // Retrieve phone from SharedPreferences (Primary ID)
        userPhone = getSharedPreferences("KB_PREFS", MODE_PRIVATE).getString("user_phone", null)

        // Initialize Views
        tvPrimaryMobile = findViewById(R.id.tv_primary_mobile)
        etAltMobile = findViewById(R.id.et_alt_mobile)
        etEmail = findViewById(R.id.et_email)
        etAddress = findViewById(R.id.et_address)
        btnSave = findViewById(R.id.btn_save_contact)

        // Set Primary Mobile (Non-changeable)
        tvPrimaryMobile.text = if (userPhone != null) "+91 $userPhone" else "Not Available"

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveContactInfo()
        }

        loadContactInfo()
    }

    private fun loadContactInfo() {
        val phone = userPhone ?: return
        
        db.collection("users").document(phone).get()
            .addOnSuccessListener { document ->
                if (isFinishing) return@addOnSuccessListener
                
                if (document.exists()) {
                    etAltMobile.setText(document.getString("alternateMobile") ?: "")
                    etEmail.setText(document.getString("email") ?: "")
                    etAddress.setText(document.getString("address") ?: "")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CONTACT_DEBUG", "Load failed: ${e.message}")
            }
    }

    private fun saveContactInfo() {
        val phone = userPhone ?: return
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "alternateMobile" to etAltMobile.text.toString().trim(),
            "email" to etEmail.text.toString().trim(),
            "address" to etAddress.text.toString().trim()
        )

        // Save under Phone ID
        db.collection("users").document(phone)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Contact details updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("CONTACT_DEBUG", "Update failed: ${e.message}")
                btnSave.isEnabled = true
                btnSave.text = "SAVE CONTACT INFO"
                Toast.makeText(this, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}