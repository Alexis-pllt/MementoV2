package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth



import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.example.mementov2.CreateGroupActivity
import com.example.mementov2.MyGroupsActivity
import com.example.mementov2.JoinGroupActivity
// ... le reste du code ...

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAuth = FirebaseAuth.getInstance()



        // Bouton "Créer un groupe"
        val createGroupButton = findViewById<android.widget.Button>(R.id.btn_create_group)
        createGroupButton.setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java)
            startActivity(intent)
        }
        // Bouton "rejoindre un groupe"
        val joinGroupButton = findViewById<android.widget.Button>(R.id.btn_join_group)
        joinGroupButton.setOnClickListener {
            val intent = Intent(this, JoinGroupActivity::class.java)
            startActivity(intent)
        }
        // Bouton "Mes groupes"
        val myGroupsButton = findViewById<android.widget.Button>(R.id.btn_my_groups)
        myGroupsButton.setOnClickListener {
            val intent = Intent(this, MyGroupsActivity::class.java)
            startActivity(intent)
        }

        // ... (le reste de votre code onCreate pour ViewCompat...)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Gardez cette fonction si vous voulez le menu Profil/Logout dans l'ActionBar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)


        return true
    }

    // Gardez cette fonction pour gérer le clic sur Profil/Logout
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.menu_logout -> {
                firebaseAuth.signOut()
                val intent = Intent(this, SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}