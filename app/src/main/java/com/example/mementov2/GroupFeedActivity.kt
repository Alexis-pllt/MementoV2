package com.example.mementov2

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GroupFeedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_feed)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val groupCode = intent.getStringExtra("GROUP_CODE") ?: ""
        val groupName = intent.getStringExtra("GROUP_NAME") ?: ""

        // IS_OPEN est utilisé pour afficher le statut, mais le bouton a été retiré du XML du feed.
        val isOpen = intent.getBooleanExtra("IS_OPEN", true)

        // Récupérer les vues
        val titleTextView = findViewById<TextView>(R.id.feed_title)
        val statusTextView = findViewById<TextView>(R.id.feed_status_text)

        titleTextView.text = "Feed: $groupName"


            // Affichage si le groupe est FERMÉ (lecture seule)
            statusTextView.text = "The party is over"


        // Le bouton de prise de photo (btn_take_photo_feed) n'existe plus dans ce layout,
        // ou est masqué par défaut, car la seule voie de photo est TakePictureActivity.
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}