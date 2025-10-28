package com.example.mementov2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue // Pour l'incrémentation atomique
import com.google.firebase.Timestamp // Pour l'horodatage
import com.google.firebase.storage.FirebaseStorage // Import non-KTX
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Assurez-vous que cette Data Class correspond à votre structure de document 'groups'
// et inclut le champ photosTakenByUser pour éviter les erreurs de désérialisation.
data class Group(
    val name: String = "",
    val joinCode: String = "",
    val photoLimitPerUser: Int = 1,
    val open: Boolean = true,
    val ownerId: String = "",
    val members: List<String> = listOf()
    // Le champ photosTakenByUser est géré comme une Map dans le code pour la sécurité
)

class TakePictureActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    // Services Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var storage: FirebaseStorage // Utilisation de la version standard

    // Données du groupe et du compteur
    private lateinit var groupCode: String
    private var photoLimit: Int = 1
    private var currentPhotosTaken: Int = 0
    private lateinit var currentUserId: String

    private lateinit var photosRemainingTextView: TextView
    private lateinit var captureButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_picture)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Prise de Photo"

        groupCode = intent.getStringExtra("GROUP_CODE") ?: run {
            Toast.makeText(this, "Erreur: Code de groupe manquant.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentUserId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Erreur d'authentification.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🚨 DÉBUT DE LA ZONE CRITIQUE (Initialisation Storage) 🚨
        try {
            // REMPLACEZ CETTE CHAÎNE PAR VOTRE URL RÉELLE DE BUCKET (ex: gs://memento-v2-xxxx.appspot.com)
            storage = FirebaseStorage.getInstance("gs://memento-98044.firebasestorage.app")
            // Si vous n'êtes pas sûr, utilisez juste getInstance(), mais le problème reviendra.
            // storage = FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Toast.makeText(this, "FATAL: Échec de l'initialisation de Storage.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // 🚨 FIN DE LA ZONE CRITIQUE 🚨


        photosRemainingTextView = findViewById(R.id.photos_remaining_text)
        captureButton = findViewById(R.id.capture_button)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 1. Demander les permissions et charger les données
        if (allPermissionsGranted()) {
            loadGroupDataAndStartCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    // Fonction pour lire le compte de photos persistant
    private fun loadGroupDataAndStartCamera() {
        db.collection("groups").document(groupCode).get()
            .addOnSuccessListener { document ->
                // Utilisation de la méthode standard toObject(Class)
                val group = document.toObject(Group::class.java)

                if (group != null) {
                    photoLimit = group.photoLimitPerUser

                    // LECTURE DU COMPTEUR PERSISTANT DE FIRESTORE 🚨
                    val photosMap = document.get("photosTakenByUser") as? Map<String, Long> ?: emptyMap()
                    currentPhotosTaken = photosMap[currentUserId]?.toInt() ?: 0

                    updatePhotoCounter()

                    if (currentPhotosTaken < photoLimit) {
                        startCamera()
                        captureButton.setOnClickListener {
                            takePhoto()
                        }
                    } else {
                        Toast.makeText(this, "Limite de photos atteinte pour ce groupe.", Toast.LENGTH_LONG).show()
                        captureButton.isEnabled = false
                    }
                } else {
                    Toast.makeText(this, "Groupe introuvable.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erreur de connexion aux données: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // Met à jour l'interface utilisateur pour le décompte
    private fun updatePhotoCounter() {
        val remaining = photoLimit - currentPhotosTaken
        photosRemainingTextView.text = "Photos restantes : $remaining / $photoLimit"

        if (remaining <= 0) {
            captureButton.isEnabled = false
        } else {
            captureButton.isEnabled = true
        }
    }

    // Initialise le PreviewView et l'ImageCapture
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.view_finder).surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Échec de la liaison de la caméra: ${exc.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Prend la photo, téléverse et sauvegarde dans Firestore
    private fun takePhoto() {
        if (currentPhotosTaken >= photoLimit) {
            updatePhotoCounter()
            return
        }

        val imageCapture = imageCapture ?: return
        captureButton.isEnabled = false // Désactiver le bouton pendant la capture

        // Créer un fichier de sortie pour stocker l'image localement avant le téléversement
        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    captureButton.isEnabled = true
                    Toast.makeText(this@TakePictureActivity, "Erreur de capture : ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capturée! Téléversement en cours..."
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()

                    // 🚨 TÉLÉVERSEMENT ET SAUVEGARDE 🚨
                    uploadImageAndSaveRecord(output.savedUri ?: Uri.fromFile(photoFile), photoFile)
                }
            }
        )
    }

    // Fonction de téléversement et de mise à jour de la BDD
    private fun uploadImageAndSaveRecord(photoUri: Uri, photoFile: File) {
        val storageRef = storage.reference

        val timestamp = System.currentTimeMillis()

        // CORRECTION DE LA SYNTAXE DU CHEMIN AVEC TRIPLE-QUOTES
        val storagePath = """photos/$groupCode/$currentUserId\_$timestamp.jpg"""

        val imageRef = storageRef.child(storagePath)

        // 1. Téléverser le fichier
        imageRef.putFile(photoUri)
            .addOnSuccessListener {
                // 2. Récupérer l'URL de téléchargement
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->

                    // 3. Sauvegarder la référence dans la collection 'photos'
                    val newPhotoRecord = hashMapOf(
                        "groupId" to groupCode,
                        "userId" to currentUserId,
                        "storageUrl" to downloadUri.toString(),
                        "timestamp" to Timestamp.now()
                    )

                    db.collection("photos").add(newPhotoRecord)
                        .addOnSuccessListener {
                            // Mettre à jour le compteur du groupe après le succès des deux opérations
                            updateGroupPhotoCount()

                            Toast.makeText(this, "Photo publiée avec succès!", Toast.LENGTH_LONG).show()

                            // Nettoyage: Supprimer le fichier local
                            photoFile.delete()
                            captureButton.isEnabled = true
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erreur de sauvegarde Firestore (référence photo).", Toast.LENGTH_LONG).show()
                            captureButton.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                // Échec du téléversement de l'image (Règles Storage, Connexion, ou Mauvais Bucket)
                Toast.makeText(this, "Échec du téléversement de l'image: ${e.message}", Toast.LENGTH_LONG).show()
                captureButton.isEnabled = true
            }
    }

    // Fonction pour incrémenter le compteur dans le document 'group'
    private fun updateGroupPhotoCount() {
        val groupRef = db.collection("groups").document(groupCode)

        // Incrémente atomiquement le compteur: photosTakenByUser.{currentUserId} += 1
        val updatePath = "photosTakenByUser.$currentUserId"
        groupRef.update(updatePath, FieldValue.increment(1))
            .addOnSuccessListener {
                // Mettre à jour la variable locale et le compteur UI
                currentPhotosTaken++
                updatePhotoCounter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Avertissement: Échec de la mise à jour du compteur.", Toast.LENGTH_SHORT).show()
            }
    }

    // ----------------------------------
    // Gestion des permissions
    // ----------------------------------

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                loadGroupDataAndStartCamera()
            } else {
                Toast.makeText(this, "Les permissions de la caméra sont requises pour continuer.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}