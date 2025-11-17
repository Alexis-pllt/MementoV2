package com.example.mementov2

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import de.hdodenhof.circleimageview.CircleImageView // 🚨 Assurez-vous que cet import est présent 🚨
import java.text.SimpleDateFormat
import java.util.Locale

// Data class Photo (inchangée)
@IgnoreExtraProperties
data class Photo(
    @com.google.firebase.firestore.DocumentId val id: String = "",
    val storageUrl: String = "",
    val userId: String = "",
    val groupId: String = "",
    val timestamp: Timestamp? = null,
    val likes: List<String> = emptyList()
)

// 🚨 Data class pour les informations de profil utilisateur 🚨
data class UserProfile(
    val username: String? = null,
    val profileImageUrl: String? = null
)


class PhotoAdapter(private val photos: List<Photo>) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount() = photos.size

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Vues existantes
        private val photoImageView: ImageView = itemView.findViewById(R.id.photo_image_view)
        private val likeButton: ImageButton = itemView.findViewById(R.id.like_button)
        private val likeCountTextView: TextView = itemView.findViewById(R.id.like_count_text_view)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text_view)

        // 🚨 VUES DU PROFIL : Initialisation correcte avec le type CircleImageView 🚨
        private val profileImageView: CircleImageView = itemView.findViewById(R.id.profile_image_feed)
        private val usernameTextView: TextView = itemView.findViewById(R.id.text_view_username)
        // -------------------------------------------------------------------------

        private val db = FirebaseFirestore.getInstance()
        private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        fun bind(photo: Photo) {

            // 🚨 ÉTAPE 1 : Charger le profil utilisateur 🚨
            loadUserProfile(photo.userId)

            // Charger l'image principale du feed
            if (photo.storageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(photo.storageUrl)
                    .into(photoImageView)
            } else {
                photoImageView.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            updateLikes(photo)

            likeButton.setOnClickListener {
                toggleLike(photo)
            }

            photo.timestamp?.let {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                timestampTextView.text = sdf.format(it.toDate())
            } ?: run {
                timestampTextView.text = ""
            }
        }

        /**
         * Effectue une requête secondaire pour récupérer le profil de l'utilisateur.
         */
        private fun loadUserProfile(userId: String) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    // Désérialisation du profil utilisateur
                    val userProfile = document.toObject(UserProfile::class.java)

                    val username = userProfile?.username ?: "Unknown User"
                    val profileUrl = userProfile?.profileImageUrl

                    // 1. Afficher le nom d'utilisateur
                    usernameTextView.text = username

                    // 2. Charger la photo de profil
                    if (!profileUrl.isNullOrEmpty()) {
                        Glide.with(itemView.context)
                            .load(profileUrl)
                            .placeholder(R.drawable.ic_profile_default)
                            .into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile_default)
                    }
                }
                .addOnFailureListener {
                    usernameTextView.text = "Anonimous"
                    profileImageView.setImageResource(R.drawable.ic_profile_default)
                }
        }


        private fun toggleLike(photo: Photo) {
            if (currentUserId == null) return

            Log.d("LikeDebug", "Attempting to update photo with ID: '${photo.id}'")

            if (photo.id.isEmpty()) {
                Log.e("LikeDebug", "Photo ID is EMPTY. This is why the write is failing.")
                return
            }

            val photoRef = db.collection("photos").document(photo.id)
            if (photo.likes.contains(currentUserId)) {
                photoRef.update("likes", FieldValue.arrayRemove(currentUserId))
            } else {
                photoRef.update("likes", FieldValue.arrayUnion(currentUserId))
            }
        }

        private fun updateLikes(photo: Photo) {
            likeCountTextView.text = photo.likes.size.toString()
            if (currentUserId != null) {
                if (photo.likes.contains(currentUserId)) {
                    likeButton.setImageResource(android.R.drawable.btn_star_big_on)
                } else {
                    likeButton.setImageResource(android.R.drawable.btn_star_big_off)
                }
            }
        }
    }
}