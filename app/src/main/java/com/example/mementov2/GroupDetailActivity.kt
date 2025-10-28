package com.example.mementov2

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class GroupDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail) // Créer ce layout à l'étape B

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Récupérer le code unique du groupe passé par l'activité précédente
        val groupCode = intent.getStringExtra("GROUP_CODE")
        val groupName = intent.getStringExtra("GROUP_NAME")

        if (groupCode != null) {
            // Afficher le code du groupe pour vérification
            findViewById<TextView>(R.id.detail_title).text = "Group details: $groupName"
            // TODO: Ici, vous chargerez les détails complets du groupe depuis Firestore en utilisant ce code.
        } else {
            Toast.makeText(this, "Erreur: Aucun groupe sélectionné.", Toast.LENGTH_LONG).show()
            finish() // Ferme l'activité si aucun code n'est passé
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}