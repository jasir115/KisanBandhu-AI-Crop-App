package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_info)

        tvPrimaryMobile = findViewById(R.id.tv_primary_mobile)
        etAltMobile = findViewById(R.id.et_alt_mobile)
        etEmail = findViewById(R.id.et_email)
        etAddress = findViewById(R.id.et_address)
        btnSave = findViewById(R.id.btn_save_contact)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveContactInfo()
        }

        loadContactInfo()
    }

    private fun loadContactInfo() {
        val uid = auth.currentUser?.uid ?: return
        
        // Use the phone number from Auth as the primary source of truth
        val authPhone = auth.currentUser?.phoneNumber ?: "Not Available"
        tvPrimaryMobile.text = authPhone

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    // We only overwrite if Firestore has a different stored number (rare), 
                    // but usually Auth is the best source.
                    // tvPrimaryMobile.text = profile?.mobileNumber ?: authPhone
                    
                    etAltMobile.setText(profile?.alternateMobile)
                    etEmail.setText(profile?.email)
                    etAddress.setText(profile?.address)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveContactInfo() {
        val uid = auth.currentUser?.uid ?: return
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "alternateMobile" to etAltMobile.text.toString(),
            "email" to etEmail.text.toString(),
            "address" to etAddress.text.toString()
        )

        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Contact Info Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "SAVE CONTACT INFO / सहेजें"
            }
    }
}