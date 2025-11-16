package com.example.mementov2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Group(
    val name: String = "",
    val joinCode: String = "",
    val photoLimitPerUser: Int = 1,
    val open: Boolean = true,
    val ownerId: String = "",
    val members: List<String> = listOf(),
    val closingTime: Timestamp? = null
)

class TakePictureActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var storage: FirebaseStorage

    private lateinit var groupCode: String
    private var photoLimit: Int = 1
    private var currentPhotosTaken: Int = 0
    private lateinit var currentUserId: String

    private lateinit var photosRemainingTextView: TextView
    private lateinit var countdownTimerTextView: TextView
    private lateinit var captureButton: ImageButton
    private lateinit var flipCameraButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

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

        try {
            storage = FirebaseStorage.getInstance("gs://memento-98044.firebasestorage.app")
        } catch (e: Exception) {
            Toast.makeText(this, "FATAL: Échec de l'initialisation de Storage.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        photosRemainingTextView = findViewById(R.id.photos_remaining_text)
        countdownTimerTextView = findViewById(R.id.countdown_timer_text)
        captureButton = findViewById(R.id.capture_button)
        flipCameraButton = findViewById(R.id.flip_camera_button)
        cameraExecutor = Executors.newSingleThreadExecutor()

        flipCameraButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        if (allPermissionsGranted()) {
            loadGroupDataAndStartCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun loadGroupDataAndStartCamera() {
        db.collection("groups").document(groupCode).get()
            .addOnSuccessListener { document ->
                val group = document.toObject(Group::class.java)

                if (group != null) {
                    photoLimit = group.photoLimitPerUser
                    group.closingTime?.let { startCountdown(it) }

                    val photosMap = document.get("photosTakenByUser") as? Map<String, Long> ?: emptyMap()
                    currentPhotosTaken = photosMap[currentUserId]?.toInt() ?: 0

                    updatePhotoCounter()

                    if (currentPhotosTaken < photoLimit) {
                        startCamera()
                        captureButton.setOnClickListener { takePhoto() }
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

    private fun startCountdown(closingTime: Timestamp) {
        countdownRunnable = object : Runnable {
            override fun run() {
                val remainingTime = closingTime.toDate().time - System.currentTimeMillis()
                if (remainingTime > 0) {
                    val days = TimeUnit.MILLISECONDS.toDays(remainingTime)
                    val hours = TimeUnit.MILLISECONDS.toHours(remainingTime) % 24
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime) % 60

                    countdownTimerTextView.text = String.format("Time remaining: %dd:%02dh:%02dm", days, hours, minutes)
                    countdownTimerTextView.visibility = View.VISIBLE

                    handler.postDelayed(this, 1000) // Répéter toutes les secondes
                } else {
                    countdownTimerTextView.text = "Group closed"
                    captureButton.isEnabled = false
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun updatePhotoCounter() {
        val remaining = photoLimit - currentPhotosTaken
        photosRemainingTextView.text = "Photos restantes : $remaining / $photoLimit"

        if (remaining <= 0) {
            captureButton.isEnabled = false
        }
    }

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

    private fun takePhoto() {
        if (currentPhotosTaken >= photoLimit) {
            updatePhotoCounter()
            return
        }

        val imageCapture = imageCapture ?: return
        captureButton.isEnabled = false

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
                    uploadImageAndSaveRecord(output.savedUri ?: Uri.fromFile(photoFile), photoFile)
                }
            }
        )
    }

    private fun uploadImageAndSaveRecord(photoUri: Uri, photoFile: File) {
        val storageRef = storage.reference
        val timestamp = System.currentTimeMillis()
        val storagePath = """photos/$groupCode/$currentUserId\_$timestamp.jpg"""
        val imageRef = storageRef.child(storagePath)

        imageRef.putFile(photoUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val newPhotoRecord = hashMapOf(
                        "groupId" to groupCode,
                        "userId" to currentUserId,
                        "storageUrl" to downloadUri.toString(),
                        "timestamp" to Timestamp.now()
                    )

                    db.collection("photos").add(newPhotoRecord)
                        .addOnSuccessListener {
                            updateGroupPhotoCount()
                            Toast.makeText(this, "Photo publiée avec succès!", Toast.LENGTH_LONG).show()
                            photoFile.delete()
                            if (currentPhotosTaken < photoLimit) { 
                                captureButton.isEnabled = true
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Erreur de sauvegarde Firestore (référence photo).", Toast.LENGTH_LONG).show()
                            captureButton.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Échec du téléversement de l'image: ${e.message}", Toast.LENGTH_LONG).show()
                captureButton.isEnabled = true
            }
    }

    private fun updateGroupPhotoCount() {
        val groupRef = db.collection("groups").document(groupCode)
        val updatePath = "photosTakenByUser.$currentUserId"

        groupRef.update(updatePath, FieldValue.increment(1))
            .addOnSuccessListener {
                currentPhotosTaken++
                updatePhotoCounter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Avertissement: Échec de la mise à jour du compteur.", Toast.LENGTH_SHORT).show()
            }
    }

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
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
