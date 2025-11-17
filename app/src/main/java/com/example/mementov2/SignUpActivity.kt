package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mementov2.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // 🚨 NOUVEL IMPORT 🚨

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // 🚨 Déclaration Firestore 🚨

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance() // 🚨 Initialisation Firestore 🚨

        binding.textView.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        binding.button.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val username = binding.usernameEt.text.toString().trim() // 🚨 LECTURE DU NOUVEAU CHAMP 🚨
            val pass = binding.passET.text.toString()
            val confirmPass = binding.confirmPassEt.text.toString()

            // 🚨 Validation : vérifie que l'USERNAME n'est pas vide 🚨
            if (email.isNotEmpty() && username.isNotEmpty() && pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                if (pass == confirmPass) {

                    firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = firebaseAuth.currentUser!!.uid

                            // 🚨 ÉTAPE CLÉ : Sauvegarder l'username dans Firestore 🚨
                            saveUsernameToFirestore(userId, username)

                            val intent = Intent(this, SignInActivity::class.java)
                            startActivity(intent)
                            finish() // Termine l'activité d'inscription

                        } else {
                            // Affiche un message d'erreur d'authentification (ex: email déjà utilisé)
                            Toast.makeText(this, task.exception?.message ?: "Sign up failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Password is not matching", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Empty Fields Are not Allowed !!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Sauvegarde l'UID et l'username dans la collection 'users'.
     * Le document ID est l'UID de l'utilisateur.
     */
    private fun saveUsernameToFirestore(userId: String, username: String) {
        val user = hashMapOf(
            "username" to username,
            "uid" to userId,
            "profileImageUrl" to "" // Champ par défaut pour la photo de profil
        )

        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                Log.d("SignUpActivity", "User profile successfully written to Firestore.")
            }
            .addOnFailureListener { e ->
                Log.e("SignUpActivity", "Error saving user data to Firestore: ${e.message}")
                Toast.makeText(this, "Profile saving failed.", Toast.LENGTH_LONG).show()
            }
    }
}