package com.example.mementov2

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GroupFeedActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var photosRecyclerView: RecyclerView
    private lateinit var photoAdapter: PhotoAdapter
    private val photoList = mutableListOf<Photo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_feed)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        val groupCode = intent.getStringExtra("GROUP_CODE") ?: ""

        val titleTextView = findViewById<TextView>(R.id.feed_title)
        val statusTextView = findViewById<TextView>(R.id.feed_status_text)

        photosRecyclerView = findViewById(R.id.photos_recycler_view)
        photosRecyclerView.layoutManager = LinearLayoutManager(this)
        photoAdapter = PhotoAdapter(photoList)
        photosRecyclerView.adapter = photoAdapter

        if (groupCode.isNotEmpty()) {
            db.collection("groups").document(groupCode).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val groupName = document.getString("name") ?: ""
                        val isOpen = document.getBoolean("open") ?: true

                        titleTextView.text = "Feed: $groupName"
                        statusTextView.text = if (isOpen) "Group is open" else "The party is over"

                        loadPhotosInRealtime(groupCode)
                    } else {
                        Toast.makeText(this, "Group not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error loading group: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "No group code provided.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadPhotosInRealtime(groupCode: String) {
        db.collection("photos")
            .whereEqualTo("groupId", groupCode)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading photos: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val newPhotos = snapshots!!.documents.mapNotNull { document ->
                    // Correctly map the document to the Photo data class AND set its ID
                    document.toObject(Photo::class.java)?.copy(id = document.id)
                }

                photoList.clear()
                photoList.addAll(newPhotos)
                photoAdapter.notifyDataSetChanged()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}