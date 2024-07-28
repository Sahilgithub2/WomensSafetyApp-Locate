package com.example.phoneauth

import android.app.AlertDialog
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AddContactsActivity : AppCompatActivity() {

    private lateinit var contactsListView: ListView
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var saveContactsBtn: Button
    private lateinit var searchIcon: ImageView
    private lateinit var databaseReference: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contacts)

        contactsListView = findViewById(R.id.contactsListView)
        saveContactsBtn = findViewById(R.id.saveContactsBtn)
        searchIcon = findViewById(R.id.searchIcon)

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )
        contactAdapter = ContactAdapter(this, cursor)
        contactsListView.adapter = contactAdapter

        saveContactsBtn.setOnClickListener {
            saveContactsToFirebase()
        }

        // Search icon click listener
        searchIcon.setOnClickListener {
            showSearchDialog()
        }
    }

    private fun showSearchDialog() {
        val searchView = SearchView(this)
        searchView.setIconifiedByDefault(false)
        searchView.queryHint = "Search contacts"

        AlertDialog.Builder(this)
            .setTitle("Search Contacts")
            .setView(searchView)
            .setPositiveButton("Search") { _, _ ->
                val query = searchView.query.toString()
                filterContacts(query)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterContacts(query: String) {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null
        )
        contactAdapter.changeCursor(cursor)
    }

    private fun saveContactsToFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val selectedContacts = contactAdapter.getSelectedContacts()
        Log.d("AddContactsActivity", "Selected contacts to save: $selectedContacts")

        if (selectedContacts.isEmpty()) {
            Toast.makeText(this, "No contacts selected", Toast.LENGTH_SHORT).show()
            return
        }

        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId).child("contacts")

        // Retrieve existing contacts and merge with new contacts
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val existingContacts = dataSnapshot.getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
                val mergedContacts = existingContacts.toMutableSet()
                mergedContacts.addAll(selectedContacts)
                databaseReference.setValue(mergedContacts.toList())
                    .addOnSuccessListener {
                        Toast.makeText(this@AddContactsActivity, "Contacts saved successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Toast.makeText(this@AddContactsActivity, "Failed to save contacts", Toast.LENGTH_SHORT).show()
                    }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("AddContactsActivity", "Database error: ${databaseError.message}")
                Toast.makeText(this@AddContactsActivity, "Failed to retrieve existing contacts", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
