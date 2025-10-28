package com.example.mementov2

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mementov2.databinding.ActivityMyGroupsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
// REMOVED: import com.google.firebase.firestore.ktx.toObject

class MyGroupsActivity : AppCompatActivity() {

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

        val container = findViewById<LinearLayout>(R.id.groups_list_container)

        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                // Supprimer les vues existantes avant d'ajouter les nouvelles
                if (container.childCount > 1) {
                    container.removeViews(1, container.childCount - 1)
                }

                if (result.isEmpty) {
                    val noGroupText = android.widget.TextView(this).apply {
                        text = "Vous n'avez rejoint aucun groupe pour l'instant."
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(noGroupText)
                    return@addOnSuccessListener
                }

                for (document in result) {
                    try {
                        // 🚨 MODIFICATION ICI : Utilisation de la méthode toObject(Class) 🚨
                        val group = document.toObject(Group::class.java)
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
        val button = Button(this).apply {
            // Définir le texte à afficher
            text = "${group.name} (Code: ${group.joinCode})"

            // Définir un style et une taille de police
            textSize = 18f

            // Définir la mise en page
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }

            // Ajouter un listener de clic pour ouvrir la page de détail
            setOnClickListener {
                val intent = android.content.Intent(context, GroupDetailActivity::class.java)
                intent.putExtra("GROUP_CODE", group.joinCode)
                intent.putExtra("GROUP_NAME", group.name)

                context.startActivity(intent)
            }
        }

        val divider = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme)) // Utiliser le thème pour getColor
        }

        container.addView(button)
        container.addView(divider)
    }

    // DÉFINITION DE LA DATA CLASS POUR FIRESTORE
    data class Group(
        val name: String = "",
        val joinCode: String = "",
        val photoLimitPerUser: Int = 1,
        val open: Boolean = true,
        val members: List<String> = listOf()
    )

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}