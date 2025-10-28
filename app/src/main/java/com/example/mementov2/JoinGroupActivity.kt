package com.example.mementov2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mementov2.databinding.ActivityJoinGroupBinding // Nécessite ViewBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue // Nécessaire pour mettre à jour la liste

class JoinGroupActivity : AppCompatActivity() {

    // Assurez-vous d'avoir ViewBinding activé dans votre build.gradle.kts
    private lateinit var binding: ActivityJoinGroupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityJoinGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = "Rejoindre un groupe"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnJoinGroupSubmit.setOnClickListener {
            joinGroup()
        }
    }

    private fun joinGroup() {
        val joinCode = binding.joinCodeEditText.text.toString().trim()
        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            Toast.makeText(this, "Erreur d'authentification. Veuillez vous reconnecter.", Toast.LENGTH_SHORT).show()
            return
        }

        if (joinCode.isEmpty()) {
            Toast.makeText(this, "Veuillez entrer le code secret.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- 1. Vérifier l'existence et l'état du groupe ---
        db.collection("groups").document(joinCode).get()
            .addOnSuccessListener { documentSnapshot ->
                if (!documentSnapshot.exists()) {
                    Toast.makeText(this, "Code de groupe invalide.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val isOpen = documentSnapshot.getBoolean("open") ?: false
                val members = documentSnapshot.get("members") as? List<String> ?: emptyList()
                val groupName = documentSnapshot.getString("name") ?: "Ce groupe"

                if (!isOpen) {
                    Toast.makeText(this, "Ce groupe est fermé et ne peut pas être rejoint.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                if (members.contains(currentUserId)) {
                    Toast.makeText(this, "Vous faites déjà partie de ce groupe.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // --- 2. Ajouter l'utilisateur à la liste des membres ---
                // FieldValue.arrayUnion ajoute l'ID seulement s'il n'est pas déjà présent
                db.collection("groups").document(joinCode)
                    .update("members", FieldValue.arrayUnion(currentUserId))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Félicitations ! Vous avez rejoint le groupe '$groupName'.", Toast.LENGTH_LONG).show()
                        finish() // Retourne à l'écran précédent
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Erreur lors de l'ajout au groupe.", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erreur de connexion : ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}