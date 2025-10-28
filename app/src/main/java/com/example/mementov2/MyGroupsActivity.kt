package com.example.mementov2

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mementov2.databinding.ActivityMyGroupsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View
import com.google.firebase.firestore.toObject
import android.content.Intent
import android.widget.Button

class MyGroupsActivity : AppCompatActivity() {

    // Nous n'avons plus besoin de l'Adapter
    private lateinit var binding: ActivityMyGroupsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = "Mes groupes"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadMyGroups()
    }

    private fun loadMyGroups() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(this, "Erreur: Utilisateur non connecté.", Toast.LENGTH_SHORT).show()
            return
        }

        // Conteneur où les groupes seront ajoutés
        val container = findViewById<LinearLayout>(R.id.groups_list_container)

        // Requête Firestore: Chercher les documents de la collection 'groups'
        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                // Supprimer les vues existantes avant d'ajouter les nouvelles
                // On garde seulement la première vue (le titre "Vos groupes rejoints :")
                if (container.childCount > 1) {
                    container.removeViews(1, container.childCount - 1)
                }

                if (result.isEmpty) {
                    val noGroupText = TextView(this).apply {
                        text = "Vous n'avez rejoint aucun groupe pour l'instant."
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(noGroupText)
                    return@addOnSuccessListener
                }

                for (document in result) {
                    try {
                        val group = document.toObject<Group>()
                        // 🚨 Création dynamique du TextView pour chaque groupe 🚨
                        createGroupButton(group, container)

                    } catch (e: Exception) {
                        Toast.makeText(this, "Erreur de conversion de document.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Erreur de chargement des groupes: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun createGroupButton(group: Group, container: LinearLayout) {
        val button = Button(this).apply { // 🚨 UTILISATION DE BUTTON AU LIEU DE TEXTVIEW 🚨
            // Définir le texte à afficher
            text = "${group.name} (Code: ${group.joinCode})"

            // Définir un style et une taille de police (peut être retiré pour utiliser le style Button par défaut)
            textSize = 18f
            // setPadding(16, 16, 16, 16) // Les boutons ont leur propre padding

            // Définir la mise en page
            layoutParams = ViewGroup.MarginLayoutParams( // Utiliser MarginLayoutParams pour les marges
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // Ajouter une petite marge en bas pour séparer les boutons
                setMargins(0, 8, 0, 8)
            }

            // Ajouter un listener de clic pour ouvrir la page de détail
            setOnClickListener {
                val intent = Intent(context, GroupDetailActivity::class.java)
                // 🚨 PASSER LE CODE UNIQUE DU GROUPE 🚨
                intent.putExtra("GROUP_CODE", group.joinCode)
                intent.putExtra("GROUP_NAME", group.name)

                startActivity(intent)
            }
        }

        // Retirer la ligne de séparation si on utilise des boutons avec des marges
        // container.addView(textView) // Ancien
        container.addView(button) // Nouveau

        // La fonction loadMyGroups() appelle maintenant createGroupButton
    }

// ... fin de la classe MyGroupsActivity

    // DÉFINITION DE LA DATA CLASS POUR FIRESTORE
    data class Group(
        val name: String = "",
        val joinCode: String = "",
        val members: List<String> = listOf()
        // Ajoutez ici d'autres champs si votre document Firestore en contient
    )

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}