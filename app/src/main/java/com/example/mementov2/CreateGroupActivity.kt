package com.example.mementov2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mementov2.databinding.ActivityCreateGroupBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var closingTime: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = "Créer un nouveau groupe"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnSelectClosingTime.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun showDateTimePicker() {
        val currentCalendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val timePickerDialog = TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        closingTime = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                        }
                        updateClosingTimeLabel()
                    },
                    currentCalendar.get(Calendar.HOUR_OF_DAY),
                    currentCalendar.get(Calendar.MINUTE),
                    true
                )
                timePickerDialog.show()
            },
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH),
            currentCalendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun updateClosingTimeLabel() {
        closingTime?.let {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.closingTimeTextView.text = "Closes on: ${sdf.format(it.time)}"
        }
    }

    private fun createGroup() {
        val groupName = binding.groupNameEditText.text.toString().trim()
        val joinCode = binding.joinCodeEditText.text.toString().trim()
        val photoLimitText = binding.photoLimitEditText.text.toString().trim()
        val currentUserId = auth.currentUser?.uid

        if (groupName.isEmpty() || joinCode.isEmpty() || photoLimitText.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs.", Toast.LENGTH_SHORT).show()
            return
        }

        val photoLimit = photoLimitText.toIntOrNull()
        if (photoLimit == null || photoLimit <= 0) {
            Toast.makeText(this, "Le nombre de photos doit être un nombre positif.", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentUserId == null) {
            Toast.makeText(this, "Erreur d'authentification. Veuillez vous reconnecter.", Toast.LENGTH_SHORT).show()
            return
        }

        val newGroup = hashMapOf(
            "name" to groupName,
            "joinCode" to joinCode,
            "photoLimitPerUser" to photoLimit,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "ownerId" to currentUserId,
            "members" to listOf(currentUserId),
            "open" to true
        )

        closingTime?.let {
            newGroup["closingTime"] = com.google.firebase.Timestamp(it.time)
        }

        db.collection("groups").document(joinCode).set(newGroup)
            .addOnSuccessListener {
                Toast.makeText(this, "Groupe '$groupName' créé avec succès!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erreur de création : ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
