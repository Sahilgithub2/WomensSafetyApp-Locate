package com.example.phoneauth

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.CursorAdapter
import android.widget.Toast

class ContactAdapter(context: Context, cursor: Cursor?) : CursorAdapter(context, cursor, 0) {

    private val selectedContacts = mutableSetOf<String>()

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.contact_item, parent, false)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

        if (phoneIndex != -1 && nameIndex != -1) {
            val phoneNumber = cursor.getString(phoneIndex)
            val contactName = cursor.getString(nameIndex)
            val checkBox = view.findViewById<CheckBox>(R.id.contact_checkbox)

            checkBox.text = "$contactName - $phoneNumber"
            checkBox.isChecked = selectedContacts.contains(phoneNumber)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedContacts.add(phoneNumber)
                } else {
                    selectedContacts.remove(phoneNumber)
                }
            }

            // Apply fade-in animation
            val fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in)
            view.startAnimation(fadeIn)
        } else {
            Toast.makeText(context, "Phone number or name column not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun addSelectedContact(phoneNumber: String) {
        selectedContacts.add(phoneNumber)
        notifyDataSetChanged()
    }

    fun getSelectedContacts(): List<String> {
        return selectedContacts.toList()
    }
}
