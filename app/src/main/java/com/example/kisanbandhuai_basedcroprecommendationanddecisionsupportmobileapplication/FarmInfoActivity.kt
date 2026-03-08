package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FarmInfoActivity : AppCompatActivity() {

    private lateinit var etFarmSize: EditText
    private lateinit var etCurrentCrops: EditText
    private lateinit var toggleUnit: MaterialButtonToggleGroup
    private lateinit var etFarmLocation: EditText
    private lateinit var btnSave: MaterialButton
    
    private var selectedSoilType: String = ""
    private var selectedIrrigation: String = ""
    private var selectedOwnership: String = ""
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var soilViews: List<TextView>
    private lateinit var irrViews: List<TextView>
    private lateinit var ownViews: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farm_info)

        etFarmSize = findViewById(R.id.et_farm_size)
        etCurrentCrops = findViewById(R.id.et_current_crops)
        toggleUnit = findViewById(R.id.toggle_unit)
        etFarmLocation = findViewById(R.id.et_farm_location)
        btnSave = findViewById(R.id.btn_save_farm)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        setupSelectionLists()
        setupSelectionLogic()
        
        btnSave.setOnClickListener {
            saveFarmInfo()
        }

        loadFarmInfo()
    }

    private fun setupSelectionLists() {
        soilViews = listOf(
            findViewById(R.id.soil_black),
            findViewById(R.id.soil_red),
            findViewById(R.id.soil_alluvial)
        )
        irrViews = listOf(
            findViewById(R.id.irr_drip),
            findViewById(R.id.irr_sprinkler),
            findViewById(R.id.irr_flood)
        )
        ownViews = listOf(
            findViewById(R.id.own_owned),
            findViewById(R.id.own_leased)
        )
    }

    private fun setupSelectionLogic() {
        soilViews.forEach { view ->
            view.setOnClickListener { selectOption(view, soilViews) { selectedSoilType = it } }
        }
        irrViews.forEach { view ->
            view.setOnClickListener { selectOption(view, irrViews) { selectedIrrigation = it } }
        }
        ownViews.forEach { view ->
            view.setOnClickListener { selectOption(view, ownViews) { selectedOwnership = it } }
        }
    }

    private fun selectOption(selected: TextView, all: List<TextView>, callback: (String) -> Unit) {
        all.forEach { it.setBackgroundResource(R.drawable.input_field_bg) }
        selected.setBackgroundResource(R.drawable.option_item_selector)
        callback(selected.text.toString())
    }

    private fun loadFarmInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(UserProfile::class.java)
                    etFarmSize.setText(profile?.farmSize?.toString() ?: "")
                    etCurrentCrops.setText(profile?.currentCrops ?: "")
                    etFarmLocation.setText(profile?.farmLocation ?: "")
                    
                    if (profile?.farmSizeUnit == "Hectares") {
                        toggleUnit.check(R.id.btn_hectares)
                    } else {
                        toggleUnit.check(R.id.btn_acres)
                    }
                    
                    // Pre-select options visually
                    profile?.soilType?.let { text ->
                        soilViews.find { it.text.toString() == text }?.let {
                            selectOption(it, soilViews) { selectedSoilType = it }
                        }
                    }
                    profile?.irrigationMethod?.let { text ->
                        irrViews.find { it.text.toString() == text }?.let {
                            selectOption(it, irrViews) { selectedIrrigation = it }
                        }
                    }
                    profile?.ownershipType?.let { text ->
                        ownViews.find { it.text.toString() == text }?.let {
                            selectOption(it, ownViews) { selectedOwnership = it }
                        }
                    }
                }
            }
    }

    private fun saveFarmInfo() {
        val uid = auth.currentUser?.uid ?: return
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        val sizeUnit = if (toggleUnit.checkedButtonId == R.id.btn_hectares) "Hectares" else "Acres"
        
        val updates = hashMapOf<String, Any>(
            "farmSize" to (etFarmSize.text.toString().toDoubleOrNull() ?: 0.0),
            "farmSizeUnit" to sizeUnit,
            "currentCrops" to etCurrentCrops.text.toString(),
            "farmLocation" to etFarmLocation.text.toString(),
            "soilType" to selectedSoilType,
            "irrigationMethod" to selectedIrrigation,
            "ownershipType" to selectedOwnership
        )

        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Farm Info Saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "SAVE FARM INFO / सहेजें"
            }
    }
}