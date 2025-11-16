package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LinkHandlerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val appLinkData = intent?.data

        if (appLinkData != null) {
            // L'URL est https://mementoapp.com/join?code=XYZ
            val joinCode = appLinkData.getQueryParameter("code")

            if (joinCode != null) {
                // Rediriger vers JoinGroupActivity avec le code
                val joinIntent = Intent(this, JoinGroupActivity::class.java).apply {
                    // Les Deep Links utilisent toujours ACTION_VIEW
                    action = Intent.ACTION_VIEW
                    // Réutilise le schéma Memento pour la gestion dans JoinGroupActivity
                    data = appLinkData.buildUpon()
                        .scheme("memento") // Modifie le schéma pour le traitement interne
                        .build()
                }
                startActivity(joinIntent)
            }
        }

        finish() // Termine cette activité immédiatement
    }
}