package com.example.mementov2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var editUsername: EditText
    private lateinit var btnSaveProfile: Button
    private lateinit var profileImage: CircleImageView

    private var imageUri: Uri? = null // URI de l'image sélectionnée pour le téléversement

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialisation des services
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialisation des vues
        editUsername = findViewById(R.id.edit_username)
        btnSaveProfile = findViewById(R.id.btn_save_profile)
        profileImage = findViewById(R.id.profile_image)

        supportActionBar?.title = "Mon Profil"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 1. Charger le profil existant au démarrage
        loadUserProfile()

        // 2. Ouvrir la galerie au clic sur l'image
        profileImage.setOnClickListener {
            openImageChooser()
        }

        // 3. Événement de clic pour sauvegarder
        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    // Lance l'Intent pour sélectionner une image de la galerie
    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imageChooserLauncher.launch(intent)
    }

    // Contrat pour gérer le résultat de la sélection d'image
    private val imageChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            imageUri = result.data?.data
            profileImage.setImageURI(imageUri) // Afficher l'image sélectionnée
        }
    }

    /**
     * Charge le nom d'utilisateur et la photo de profil existante.
     */
    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val currentUsername = document.getString("username")
                    val photoUrl = document.getString("profileImageUrl")

                    editUsername.setText(currentUsername)

                    // 🚨 CORRECTION GLIDE : Utilisation des arguments positionnels 🚨
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl) // PAS DE "string ="
                            .placeholder(R.drawable.ic_profile_default)
                            .into(profileImage)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erreur de chargement du profil.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Sauvegarde le nom d'utilisateur et déclenche le téléversement de la photo si une nouvelle image a été sélectionnée.
     */
    private fun saveUserProfile() {
        val username = editUsername.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(this, "Erreur d'authentification.", Toast.LENGTH_SHORT).show()
            return
        }

        if (username.isEmpty()) {
            editUsername.error = "Veuillez entrer un nom d'utilisateur."
            return
        }

        if (imageUri != null) {
            uploadImageToStorage(userId, username)
        } else {
            // Sauvegarder uniquement les données textuelles si la photo n'a pas changé
            saveUserData(userId, username, null)
        }
    }

    /**
     * Téléverse l'image vers Firebase Storage et sauvegarde le lien.
     */
    private fun uploadImageToStorage(userId: String, username: String) {
        // Chemin d'enregistrement : profile_images/UID.jpg
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        Toast.makeText(this, "Téléversement de la photo...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                // Récupérer l'URL de téléchargement après le succès
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveUserData(userId, username, uri.toString())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Échec du téléversement : ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Sauvegarde le nom d'utilisateur et l'URL de la photo (si fournie) dans Firestore.
     */
    private fun saveUserData(userId: String, username: String, photoUrl: String?) {
        val userMap = mutableMapOf<String, Any>(
            "username" to username,
            "uid" to userId
        )

        if (photoUrl != null) {
            userMap["profileImageUrl"] = photoUrl // Ajoute l'URL si elle a été téléversée
        }

        // Tente de mettre à jour d'abord
        db.collection("users").document(userId)
            .update(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil mis à jour avec succès!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                // Si l'update échoue (document non existant), utilise 'set' pour créer le document.
                db.collection("users").document(userId).set(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profil créé avec succès!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e2 ->
                        Toast.makeText(this, "Erreur de sauvegarde finale: ${e2.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}