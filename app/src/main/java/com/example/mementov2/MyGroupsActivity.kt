package com.example.mementov2

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mementov2.databinding.ActivityMyGroupsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class MyGroupsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyGroupsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    private val TAG = "MyGroupsDebug"

    private var cachedGroups: List<Group> = emptyList()
    private var showHiddenGroups: Boolean = false

    data class Group(
        @DocumentId val id: String = "", // Automatically holds the Firestore Document ID
        val name: String = "",
        val joinCode: String = "",
        val photoLimitPerUser: Int = 1,
        val open: Boolean = true,
        val members: List<String> = listOf(),
        val ownerId: String = "",
        val closingTime: Timestamp? = null,
        val photosTakenByUser: Map<String, Long> = emptyMap(),
        val hiddenBy: List<String> = emptyList()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setBackgroundColor(ContextCompat.getColor(this, R.color.background))

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        supportActionBar?.elevation = 0f
        supportActionBar?.title = "My Groups"

        loadMyGroups()

        val createButton = findViewById<Button>(R.id.btn_create_group_main)
        createButton.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }

        val joinButton = findViewById<Button>(R.id.btn_join_group_main)
        joinButton.setOnClickListener {
            startActivity(Intent(this, JoinGroupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadMyGroups()
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

    private fun loadMyGroups() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("groups")
            .whereArrayContains("members", currentUserId)
            .orderBy("closingTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                Log.d(TAG, "Fetched ${result.size()} total groups from Firestore")
                cachedGroups = result.mapNotNull { document ->
                    try {
                        document.toObject(Group::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing group: ${e.message}")
                        null
                    }
                }
                updateGroupsList()
            }
            .addOnFailureListener { exception ->
                val errorMessage = exception.message ?: "Unknown error"
                Log.e(TAG, "Loading error: $errorMessage")
                Toast.makeText(this, "Loading error: $errorMessage", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateGroupsList() {
        val container = findViewById<LinearLayout>(R.id.groups_list_container)
        val toggleContainer = findViewById<ViewGroup>(R.id.hidden_groups_toggle_container)
        
        container.removeAllViews()
        toggleContainer.removeAllViews()
        
        val currentUserId = auth.currentUser?.uid ?: return

        val visibleGroups = cachedGroups.filter { !it.hiddenBy.contains(currentUserId) }
        val hiddenGroups = cachedGroups.filter { it.hiddenBy.contains(currentUserId) }

        Log.d(TAG, "Visible: ${visibleGroups.size}, Hidden: ${hiddenGroups.size}, UserID: $currentUserId")

        if (visibleGroups.isEmpty() && hiddenGroups.isEmpty()) {
            renderEmptyState(container, "You haven't joined any group yet.")
            toggleContainer.visibility = View.GONE
            return
        }

        if (visibleGroups.isNotEmpty()) {
            renderListSegment(container, visibleGroups)
        } else {
            renderEmptyState(container, "No visible groups.")
        }

        if (hiddenGroups.isNotEmpty()) {
            Log.d(TAG, "Showing hidden groups toggle")
            toggleContainer.visibility = View.VISIBLE
            renderHiddenToggle(toggleContainer)
            
            if (showHiddenGroups) {
                renderListSegment(container, hiddenGroups)
            }
        } else {
            Log.d(TAG, "Hiding toggle container")
            toggleContainer.visibility = View.GONE
        }
    }

    private fun renderEmptyState(container: LinearLayout, message: String) {
        val text = TextView(this).apply {
            this.text = message
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, 64, 0, 64)
        }
        container.addView(text)
    }

    private fun renderHiddenToggle(container: ViewGroup) {
        val toggleButton = TextView(this).apply {
            text = if (showHiddenGroups) "Hide hidden groups ▲" else "Show hidden groups ▼"
            textSize = 14f
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
            
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                showHiddenGroups = !showHiddenGroups
                updateGroupsList()
            }
        }
        container.addView(toggleButton)
    }

    private fun renderListSegment(container: LinearLayout, groups: List<Group>) {
        var lastClosingTime: Date? = null

        for (group in groups) {
            val currentClosingTime = group.closingTime?.toDate()

            if (currentClosingTime != null && (lastClosingTime == null || !isSameDay(currentClosingTime, lastClosingTime))) {
                addDateHeader(container, currentClosingTime)
                lastClosingTime = currentClosingTime
            }

            createGroupItemView(group, container)
        }
    }

    private fun addDateHeader(container: LinearLayout, date: Date) {
        val header = TextView(this).apply {
            text = dateFormat.format(date).uppercase()
            textSize = 14f
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(16, 48, 16, 16)
            setTextColor(ContextCompat.getColor(context, R.color.secondary_variant))
        }
        container.addView(header)
    }

    private fun createGroupItemView(group: Group, container: LinearLayout) {
        val currentUserId = auth.currentUser?.uid ?: return
        val isHidden = group.hiddenBy.contains(currentUserId)

        val photosTakenByUser = group.photosTakenByUser[currentUserId]?.toInt() ?: 0
        val remainingPhotos = group.photoLimitPerUser - photosTakenByUser
        
        val totalMembers = group.members.size
        val totalPhotos = group.photosTakenByUser.values.sum()

        val statusText: String
        val statusBgColor: Int
        val statusTextColor: Int = ContextCompat.getColor(this, R.color.white)

        if (group.open && remainingPhotos > 0) {
            statusText = "$remainingPhotos LEFT"
            statusBgColor = ContextCompat.getColor(this, R.color.text_primary)
        } else if (!group.open) {
            statusText = "FEED OPEN"
            statusBgColor = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        } else {
            statusText = "FULL"
            statusBgColor = ContextCompat.getColor(this, R.color.secondary)
        }

        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.group_item_background)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 32, 32, 32)
            
            if (isHidden) alpha = 0.5f

            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }

            setOnClickListener {
                val intent = Intent(context, GroupDetailActivity::class.java)
                intent.putExtra("GROUP_CODE", group.joinCode)
                context.startActivity(intent)
            }

            setOnLongClickListener {
                showGroupOptionsDialog(group)
                true
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }

        val nameView = TextView(this).apply {
            text = group.name
            typeface = Typeface.DEFAULT_BOLD
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }

        val detailsView = TextView(this).apply {
            text = "$totalMembers members • $totalPhotos photos"
            typeface = Typeface.SANS_SERIF
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 4, 0, 0)
        }

        textContainer.addView(nameView)
        textContainer.addView(detailsView)

        val statusView = TextView(this).apply {
            text = statusText
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(statusTextColor)
            setPadding(24, 8, 24, 8)
            
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(statusBgColor)
            }
            
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                 setMargins(16, 0, 0, 0)
            }
        }

        itemLayout.addView(textContainer)
        itemLayout.addView(statusView)
        container.addView(itemLayout)
    }

    private fun showGroupOptionsDialog(group: Group) {
        val currentUserId = auth.currentUser?.uid ?: return
        val isOwner = group.ownerId == currentUserId
        val isHidden = group.hiddenBy.contains(currentUserId)

        val options: Array<String>
        
        if (isHidden) {
            options = arrayOf("Un-hide Group")
        } else if (isOwner) {
            options = arrayOf("Hide Group", "Delete Group")
        } else {
            options = arrayOf("Hide Group")
        }

        AlertDialog.Builder(this)
            .setTitle(group.name)
            .setItems(options) { _, which ->
                if (isHidden) {
                    if (which == 0) unhideGroup(group)
                } else if (isOwner) {
                    when (which) {
                        0 -> hideGroup(group)
                        1 -> confirmDeleteGroup(group)
                    }
                } else {
                    if (which == 0) hideGroup(group)
                }
            }
            .show()
    }

    private fun hideGroup(group: Group) {
        val currentUserId = auth.currentUser?.uid ?: return
        // USE group.id HERE instead of group.joinCode to be 100% safe
        val docId = if (group.id.isNotEmpty()) group.id else group.joinCode
        
        Log.d(TAG, "Attempting to hide group with ID: $docId")
        
        db.collection("groups").document(docId)
            .update("hiddenBy", FieldValue.arrayUnion(currentUserId))
            .addOnSuccessListener { 
                Log.d(TAG, "Group hidden successfully")
                Toast.makeText(this, "Group hidden", Toast.LENGTH_SHORT).show()
                loadMyGroups() 
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Hide failed: ${e.message}")
                Toast.makeText(this, "Hide failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun unhideGroup(group: Group) {
        val currentUserId = auth.currentUser?.uid ?: return
        val docId = if (group.id.isNotEmpty()) group.id else group.joinCode

        db.collection("groups").document(docId)
            .update("hiddenBy", FieldValue.arrayRemove(currentUserId))
            .addOnSuccessListener { 
                Toast.makeText(this, "Group un-hidden", Toast.LENGTH_SHORT).show()
                loadMyGroups() 
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Unhide failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun deleteGroup(group: Group) {
        val docId = if (group.id.isNotEmpty()) group.id else group.joinCode
        db.collection("groups").document(docId)
            .delete()
            .addOnSuccessListener { 
                Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show()
                loadMyGroups() 
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteGroup(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("Delete Group?")
            .setMessage("This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteGroup(group) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
