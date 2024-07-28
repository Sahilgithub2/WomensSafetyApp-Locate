package com.example.phoneauth


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import androidx.annotation.RequiresApi as RequiresApi1
import androidx.viewpager2.widget.ViewPager2
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.abs


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var addContactsBtn: Button
    private lateinit var alertBtn: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewPager: ViewPager2
    private val imageList = listOf(
        R.drawable.image1, // Replace with your image resources
        R.drawable.image2,
        R.drawable.image3,
        R.drawable.image4
    )

    @RequiresApi1(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity", "onCreate called")



        auth = FirebaseAuth.getInstance()
//        signOutBtn = findViewById(R.id.signOutBtn)
        alertBtn = findViewById(R.id.alertBtn)
        //addContactsBtn = findViewById(R.id.addContactsBtn)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = ImageSliderAdapter(imageList)
        Log.d("MainActivity", "ViewPager2 adapter set with ${imageList.size} images")

        val timer = Timer()
        timer.schedule(timerTask {
            runOnUiThread {
                if (viewPager.currentItem < imageList.size - 1) {
                    viewPager.currentItem += 1
                } else {
                    viewPager.currentItem = 0
                }
            }
        }, 3000, 3000) // Change slide interval here (3000ms = 3s)

        bottomNav.menu.findItem(R.id.navigation_sign_out).setOnMenuItemClickListener {
            Log.d("MainActivity", "Sign out button clicked")
            auth.signOut()
            startActivity(Intent(this, PhoneActivity::class.java))
            true // Return true to indicate the event has been handled
        }
        val pageTransformer = ViewPager2.PageTransformer { page, position ->
            val scale = 0.85f + (1 - abs(position)) * 0.15f
            page.scaleY = scale
            page.alpha = 0.5f + (1 - abs(position))
        }
        viewPager.setPageTransformer(pageTransformer)



        bottomNav.menu.findItem(R.id.nav_contacts).setOnMenuItemClickListener {
            startActivity(Intent(this, AddContactsActivity::class.java))
            true
        }

        alertBtn.setOnClickListener {
            Log.d("MainActivity", "SOS menu item clicked")
            sendSOSMessage()
        }

        requestPermissions()
        startShakeService()
    }

    override fun onResume() {
        super.onResume()
        requestPermissions()
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ), 1)
    }

    @RequiresApi1(Build.VERSION_CODES.TIRAMISU)
    private fun sendSOSMessage() {
        Log.d("MainActivity", "sendSOSMessage called")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Permissions not granted, requesting permissions")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                Log.d("MainActivity", "Location obtained: $location")
                retrieveContactsFromFirebase { contactPhoneNumbers ->
                    if (contactPhoneNumbers.isNotEmpty()) {
                        val sosMessage = generateSOSMessage(location)
                        Log.d("MainActivity", "SOS message: $sosMessage")

                        val smsManager = SmsManager.getDefault()
                        for (phoneNumber in contactPhoneNumbers) {
                            try {
                                smsManager.sendTextMessage(phoneNumber, null, sosMessage, null, null)
                                Log.d("MainActivity", "SOS message sent to $phoneNumber")
                                Toast.makeText(this, "SOS message sent to $phoneNumber", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to send SOS message to $phoneNumber: ${e.message}")
                                Toast.makeText(this, "Failed to send SOS message to $phoneNumber", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Log.d("MainActivity", "No contact phone numbers found")
                        Toast.makeText(this, "No contact phone numbers found", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.d("MainActivity", "Failed to get location")
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Log.d("MainActivity", "Failed to get location: ${it.message}")
            Toast.makeText(this, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retrieveContactsFromFirebase(callback: (List<String>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().getReference("users").child(userId)
        db.child("contacts").get().addOnSuccessListener { dataSnapshot ->
            val contacts = dataSnapshot.getValue(object : GenericTypeIndicator<List<String>>() {})
            callback(contacts ?: emptyList())
        }.addOnFailureListener { exception ->
            Log.d("MainActivity", "get failed with ", exception)
            callback(emptyList())
        }
    }

    private fun generateSOSMessage(location: Location?): String {
        val latitude = location?.latitude
        val longitude = location?.longitude
        val locationLink = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        return "Emergency! I need help immediately. I am in danger. Here is my location: $locationLink"
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("MainActivity", "All permissions granted")
                    // Proceed with location-related tasks here if needed
                } else {
                    Log.d("MainActivity", "Permissions denied")
                    Toast.makeText(this, "Location and SMS permissions are required for this app", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun startShakeService() {
        val intent = Intent(this, ShakeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

