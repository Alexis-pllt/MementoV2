package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    // Définition de la Data Class pour pouvoir lire le document
    data class Group(
        val name: String = "",
        val joinCode: String = "",
        val photoLimitPerUser: Int = 1,
        val open: Boolean = true, // Champ crucial
        val ownerId: String = "",
        val members: List<String> = listOf()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nous n'affichons qu'un layout minimal pour éviter un écran noir pendant le chargement
        setContentView(R.layout.activity_group_detail)

        db = FirebaseFirestore.getInstance()

        val groupCode = intent.getStringExtra("GROUP_CODE")

        if (groupCode != null) {
            loadGroupAndRedirect(groupCode)
        } else {
            Toast.makeText(this, "Erreur: Aucun groupe sélectionné.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadGroupAndRedirect(groupCode: String) {
        db.collection("groups").document(groupCode).get()
            .addOnSuccessListener { document ->
                val group = document.toObject(Group::class.java)
                val targetIntent: Intent

                if (group != null) {
                    if (group.open) {
                        // 🚨 OPEN = TRUE -> Redirection vers l'activité de prise de photo 🚨
                        targetIntent = Intent(this, TakePictureActivity::class.java)
                    } else {
                        // 🚨 OPEN = FALSE -> Redirection vers le Feed (lecture seule) 🚨
                        targetIntent = Intent(this, GroupFeedActivity::class.java)
                        // Nous passons toujours IS_OPEN=false au Feed pour qu'il n'affiche pas le bouton
                        targetIntent.putExtra("IS_OPEN", false)
                    }

                    targetIntent.putExtra("GROUP_CODE", groupCode)
                    startActivity(targetIntent)
                } else {
                    Toast.makeText(this, "Le groupe n'existe plus.", Toast.LENGTH_LONG).show()
                }
                finish() // Termine cette activité immédiatement
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erreur de connexion. Impossible de vérifier le statut du groupe.", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}