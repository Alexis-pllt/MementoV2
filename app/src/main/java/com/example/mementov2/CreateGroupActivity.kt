package com.example.mementov2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.mementov2.databinding.ActivityCreateGroupBinding // Assurez-vous d'avoir le ViewBinding activé

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisation du ViewBinding
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialisation de Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configuration de la Barre d'Action (pour le bouton retour)
        supportActionBar?.title = "Créer un nouveau groupe"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Listener du bouton de création
        binding.btnCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun createGroup() {
        val groupName = binding.groupNameEditText.text.toString().trim()
        val joinCode = binding.joinCodeEditText.text.toString().trim()
        val photoLimitText = binding.photoLimitEditText.text.toString().trim()
        val currentUserId = auth.currentUser?.uid

        // --- 1. Validation des champs ---
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

        // --- 2. Préparation des données du groupe ---
        val newGroup = hashMapOf(
            "name" to groupName,
            "joinCode" to joinCode, // Utilisé pour joindre le groupe
            "photoLimitPerUser" to photoLimit,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "ownerId" to currentUserId,
            "members" to listOf(currentUserId), // L'utilisateur qui crée est le premier membre
            "open" to true
        )

        // --- 3. Sauvegarde dans Firestore ---

        // Nous allons utiliser le code secret comme ID de document pour garantir l'unicité
        db.collection("groups").document(joinCode).set(newGroup)
            .addOnSuccessListener {
                Toast.makeText(this, "Groupe '$groupName' créé avec succès!", Toast.LENGTH_LONG).show()
                finish() // Retourne à la MainActivity
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erreur de création : ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Pour gérer le bouton de retour (Up button) de l'ActionBar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}