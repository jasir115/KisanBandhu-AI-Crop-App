package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class EditProfileActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var btnSaveChanges: MaterialButton
    private lateinit var ivAvatarPreview: ImageView
    private var selectedImageUri: Uri? = null
    private var selectedLanguageCode = "en"
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                val file = File(cacheDir, "profile_camera.jpg")
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
                selectedImageUri = Uri.fromFile(file)
                ivAvatarPreview.setImageBitmap(bitmap)
                Toast.makeText(this, "Photo Captured!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.et_profile_name)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        ivAvatarPreview = findViewById(R.id.iv_avatar_preview)
        
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btn_take_photo).setOnClickListener {
            if (checkCameraPermission()) {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            } else {
                requestCameraPermission()
            }
        }

        setupLanguageSelection()

        btnSaveChanges.setOnClickListener {
            saveProfile()
        }

        loadCurrentProfile()
    }

    private fun setupLanguageSelection() {
        val langEnglish = findViewById<RelativeLayout>(R.id.lang_english_container)
        val langHindi = findViewById<RelativeLayout>(R.id.lang_hindi_container)
        val langMarathi = findViewById<RelativeLayout>(R.id.lang_marathi_container)

        val checkEnglish = findViewById<ImageView>(R.id.lang_english_check)
        val checkHindi = findViewById<ImageView>(R.id.lang_hindi_check)
        val checkMarathi = findViewById<ImageView>(R.id.lang_marathi_check)

        val containers = listOf(langEnglish, langHindi, langMarathi)
        val checks = listOf(checkEnglish, checkHindi, checkMarathi)

        fun updateUI(code: String) {
            containers.forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
            checks.forEach { it.visibility = View.GONE }

            when (code) {
                "en" -> {
                    langEnglish.setBackgroundResource(R.drawable.option_item_selector)
                    checkEnglish.visibility = View.VISIBLE
                }
                "hi" -> {
                    langHindi.setBackgroundResource(R.drawable.option_item_selector)
                    checkHindi.visibility = View.VISIBLE
                }
                "mr" -> {
                    langMarathi.setBackgroundResource(R.drawable.option_item_selector)
                    checkMarathi.visibility = View.VISIBLE
                }
            }
        }

        selectedLanguageCode = LocaleHelper.getLanguage(this)
        updateUI(selectedLanguageCode)

        langEnglish.setOnClickListener { selectedLanguageCode = "en"; updateUI("en") }
        langHindi.setOnClickListener { selectedLanguageCode = "hi"; updateUI("hi") }
        langMarathi.setOnClickListener { selectedLanguageCode = "mr"; updateUI("mr") }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1005)
    }

    private fun loadCurrentProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    etName.setText(profile?.name)
                    if (!profile?.profileImageUrl.isNullOrEmpty()) {
                        ivAvatarPreview.load(profile?.profileImageUrl) {
                            transformations(CircleCropTransformation())
                        }
                    }
                    val code = profile?.language ?: "en"
                    selectedLanguageCode = code
                    // Update UI happens in setupLanguageSelection but could be re-triggered here
                }
            }
    }

    private fun saveProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        btnSaveChanges.isEnabled = false
        btnSaveChanges.text = "Saving..."

        // Save selection to SharedPreferences immediately
        LocaleHelper.setLocale(this, selectedLanguageCode)

        if (selectedImageUri != null) {
            uploadImageAndSave(user.uid, name)
        } else {
            saveToFirestore(user.uid, name, "")
        }
    }

    private fun uploadImageAndSave(uid: String, name: String) {
        val filename = "profile_$uid.jpg"
        val ref = storage.reference.child("profile_images/$filename")

        val uploadTask = ref.putFile(selectedImageUri!!)
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) task.exception?.let { throw it }
            ref.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                saveToFirestore(uid, name, task.result.toString())
            } else {
                Toast.makeText(this, "Upload Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                btnSaveChanges.isEnabled = true
                btnSaveChanges.text = "SAVE CHANGES"
            }
        }
    }

    private fun saveToFirestore(uid: String, name: String, imageUrl: String) {
        val userMap = mutableMapOf<String, Any>(
            "uid" to uid,
            "name" to name,
            "language" to selectedLanguageCode
        )
        
        if (imageUrl.isNotEmpty()) userMap["profileImageUrl"] = imageUrl

        db.collection("users").document(uid)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                // Restart to apply language change
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "DB Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSaveChanges.isEnabled = true
                btnSaveChanges.text = "SAVE CHANGES"
            }
    }
}