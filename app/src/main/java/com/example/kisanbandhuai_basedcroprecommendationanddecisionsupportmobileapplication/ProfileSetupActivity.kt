package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class ProfileSetupActivity : AppCompatActivity() {

    private var selectedLocation: String? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var selectedImageUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                // In a real app, you would save this bitmap to a file and get the Uri
                // For simplicity, we'll just show a toast
                Toast.makeText(this, "Photo Captured!", Toast.LENGTH_SHORT).show()
                // You would also display it in an ImageView here
            }
        }
    }
    
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            Toast.makeText(this, "Photo Selected!", Toast.LENGTH_SHORT).show()
            // Display in ImageView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        val etName = findViewById<EditText>(R.id.et_setup_name)
        val btnStart = findViewById<MaterialButton>(R.id.btn_start_farming)
        val btnTakePhoto = findViewById<MaterialButton>(R.id.btn_take_photo)

        setupLocationSelection()

        btnTakePhoto.setOnClickListener {
            if (checkCameraPermission()) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            } else {
                requestCameraPermission()
            }
        }

        btnStart.setOnClickListener {
            val name = etName.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedLocation == null) {
                Toast.makeText(this, "Please select your location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveProfileAndStart(name, selectedLocation!!)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1004)
    }

    private fun setupLocationSelection() {
        val tvPune = findViewById<TextView>(R.id.loc_pune)
        val tvDelhi = findViewById<TextView>(R.id.loc_delhi)
        val tvMumbai = findViewById<TextView>(R.id.loc_mumbai)

        val locationViews = listOf(tvPune, tvDelhi, tvMumbai)

        locationViews.forEach { view ->
            view.setOnClickListener {
                // Reset others
                locationViews.forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
                // Highlight selected
                view.setBackgroundResource(R.drawable.option_item_selector)
                selectedLocation = view.text.toString().replace("📍 ", "")
            }
        }
    }

    private fun saveProfileAndStart(name: String, location: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            goToMain()
            return
        }

        val btnStart = findViewById<MaterialButton>(R.id.btn_start_farming)
        btnStart.isEnabled = false
        btnStart.text = "Setting up..."

        // If image is selected, upload it first (Logic similar to EditProfile)
        // For now, we save text data directly
        val userMap = hashMapOf(
            "uid" to uid,
            "name" to name,
            "location" to location,
            "mobileNumber" to (auth.currentUser?.phoneNumber ?: ""),
            "language" to "English", // Default for now
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(uid)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()
                goToMain()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
                btnStart.isEnabled = true
                btnStart.text = "START FARMING / शुरू करें"
            }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}