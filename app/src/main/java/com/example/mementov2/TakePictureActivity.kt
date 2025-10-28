package com.example.mementov2

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TakePictureActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private lateinit var groupCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pas besoin de setContentView si nous lançons directement la caméra

        groupCode = intent.getStringExtra("GROUP_CODE") ?: ""

        if (groupCode.isEmpty()) {
            Toast.makeText(this, "Erreur: Code de groupe manquant.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🚨 Lancement IMMÉDIAT de l'application de caméra 🚨
        dispatchTakePictureIntent()
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Utiliser startActivityForResult pour gérer le résultat après la photo
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(this, "Aucune application de caméra trouvée.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Gérer le résultat de la caméra
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Photo prise avec succès.
            // TODO: Ici, vous devez enregistrer l'image (Firebase Storage) et l'information (Firestore).
            Toast.makeText(this, "Photo capturée pour le groupe $groupCode ! Prochaine étape : Sauvegarde.", Toast.LENGTH_LONG).show()

        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_CANCELED) {
            // Photo annulée par l'utilisateur (on peut retourner au Feed ou juste se fermer)
            Toast.makeText(this, "Prise de photo annulée.", Toast.LENGTH_SHORT).show()
        }

        // Dans tous les cas, on ferme cette activité pour revenir à l'écran précédent (MyGroupsActivity)
        // ou vous pouvez la remplacer par un Intent vers GroupFeedActivity si vous voulez que le Feed
        // soit l'écran après la prise de photo.
        // Option 1: Retour à MyGroupsActivity:
        finish()

        // Option 2: Lancer le Feed après l'action (décommenter si besoin):
        /*
        val feedIntent = Intent(this, GroupFeedActivity::class.java)
        feedIntent.putExtra("GROUP_CODE", groupCode)
        feedIntent.putExtra("IS_OPEN", true) // On sait qu'il est ouvert car on est ici
        startActivity(feedIntent)
        finish()
        */
    }
}