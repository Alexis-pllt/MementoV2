package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.example.mementov2.ProfileActivity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mementov2.databinding.ActivityMyGroupsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class MyGroupsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyGroupsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    // DATA CLASS : Utilise closingTime et photosTakenByUser
    data class Group(
        val name: String = "",
        val joinCode: String = "",
        val photoLimitPerUser: Int = 1,
        val open: Boolean = true,
        val members: List<String> = listOf(),
        val closingTime: Timestamp? = null,
        val photosTakenByUser: Map<String, Long> = emptyMap()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.title = "My Groups"

        loadMyGroups()

        // LOGIQUE POUR LES BOUTONS EN BAS DE PAGE (inchangée)
        val createButton = findViewById<Button>(R.id.btn_create_group_main)
        createButton.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        val joinButton = findViewById<Button>(R.id.btn_join_group_main)
        joinButton.setOnClickListener {
            startActivity(Intent(this, JoinGroupActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu?.findItem(R.id.menu_create_group)?.isVisible = true
        menu?.findItem(R.id.menu_join_group)?.isVisible = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_create_group -> {
                startActivity(Intent(this, CreateGroupActivity::class.java))
                true
            }
            R.id.menu_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.menu_join_group -> {
                startActivity(Intent(this, JoinGroupActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                auth.signOut()
                val intent = Intent(this, SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- GROUP LOADING AND SORTING LOGIC ---

    private fun loadMyGroups() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) return

        val container = findViewById<LinearLayout>(R.id.groups_list_container)

        if (container.childCount > 1) {
            container.removeViews(1, container.childCount - 1)
        }

        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .orderBy("closingTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->

                if (result.isEmpty) {
                    val noGroupText = TextView(this).apply {
                        text = "You haven't joined any group yet."
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(noGroupText)
                    return@addOnSuccessListener
                }

                var lastClosingTime: Date? = null

                for (document in result) {
                    try {
                        val group = document.toObject(Group::class.java)

                        val currentClosingTime = group.closingTime?.toDate()

                        if (currentClosingTime != null && (lastClosingTime == null || !isSameDay(currentClosingTime, lastClosingTime))) {
                            addDateHeader(container, currentClosingTime)
                            lastClosingTime = currentClosingTime
                        }

                        createGroupItemView(group, container)

                    } catch (e: Exception) {
                        Toast.makeText(this, "Conversion error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                val errorMessage = exception.message ?: "Unknown error"
                Toast.makeText(this, "Loading error: $errorMessage", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Ajoute un TextView d'en-tête pour la date.
     */
    private fun addDateHeader(container: LinearLayout, date: Date) {
        val header = TextView(this).apply {
            text = dateFormat.format(date)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 8)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        container.addView(header)
    }

    /**
     * Crée le layout cliquable d'un groupe avec le nom et le statut/compteur.
     */
    private fun createGroupItemView(group: Group, container: LinearLayout) {
        val currentUserId = auth.currentUser?.uid ?: return

        // --- Calcul du Statut ---
        val photosTaken = group.photosTakenByUser[currentUserId]?.toInt() ?: 0
        val remainingPhotos = group.photoLimitPerUser - photosTaken

        // Déclaration des variables de statut
        val statusText: String
        val statusColorInt: Int

        // Logique de détermination du statut et de la couleur
        if (group.open && remainingPhotos > 0) {
            statusText = "$remainingPhotos left"
            statusColorInt = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        } else if (!group.open) {
            statusText = "🔒"
            statusColorInt = ContextCompat.getColor(this, android.R.color.darker_gray) // Gris/neutre pour le fermé
        } else {
            // Groupe OUVERT mais LIMITE ATTEINTE (remainingPhotos <= 0)
            statusText = "Limit reached"
            statusColorInt = ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }

        // --- 1. CRÉATION DU CONTENEUR CLIQUABLE ---
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            // 🚨 Utilise le fond arrondi (doit exister dans res/drawable) 🚨
            setBackgroundResource(R.drawable.group_item_background)

            setPadding(16, 16, 16, 16)

            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }

            setOnClickListener {
                val intent = Intent(context, GroupDetailActivity::class.java)
                intent.putExtra("GROUP_CODE", group.joinCode)
                context.startActivity(intent)
            }
        }

        // 2. NOM DU GROUPE
        val nameView = TextView(this).apply {
            text = group.name
            isAllCaps = false
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f // Poids 1.0
            )
        }

        // 3. STATUT / COMPTEUR
        val statusView = TextView(this).apply {
            text = statusText
            textSize = 16f

            // 🚨 Utilisation de la couleur Int corrigée 🚨
            setTextColor(statusColorInt)

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.END
        }

        // Ajout des vues au Layout
        itemLayout.addView(nameView)
        itemLayout.addView(statusView)

        // --- Ajout du Layout à l'écran ---
        container.addView(itemLayout)

    }

    /**
     * Checks if two Date objects fall on the same calendar day.
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}