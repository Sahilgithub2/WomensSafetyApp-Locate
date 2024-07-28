package com.example.phoneauth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator

class ShakeService : Service() {

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        Log.d("ShakeService", "Service created")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        shakeDetector = ShakeDetector {
            Log.d("ShakeService", "Shake detected!")
            sendSOSMessage()
        }

        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)

        // Call startForeground() here
        startForeground(1, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(shakeDetector)
        Log.d("ShakeService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun sendSOSMessage() {
        if (checkPermissions()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    Log.d("ShakeService", "Location obtained: $location")
                    retrieveContactsFromFirebase { contactPhoneNumbers ->
                        if (contactPhoneNumbers.isNotEmpty()) {
                            val sosMessage = generateSOSMessage(location)
                            Log.d("ShakeService", "SOS message: $sosMessage")
                            sendMessages(contactPhoneNumbers, sosMessage)
                        } else {
                            showToastAndLog("No contact phone numbers found")
                        }
                    }
                } ?: showToastAndLog("Failed to get location")
            }.addOnFailureListener {
                showToastAndLog("Failed to get location: ${it.message}")
            }
        } else {
            Log.d("ShakeService", "Permissions not granted")
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun retrieveContactsFromFirebase(callback: (List<String>) -> Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            FirebaseDatabase.getInstance().getReference("users").child(userId).child("contacts")
                .get().addOnSuccessListener { dataSnapshot ->
                    val contacts = dataSnapshot.getValue(object : GenericTypeIndicator<List<String>>() {})
                    callback(contacts ?: emptyList())
                }.addOnFailureListener { exception ->
                    Log.d("ShakeService", "get failed with ", exception)
                    callback(emptyList())
                }
        } ?: callback(emptyList())
    }

    private fun generateSOSMessage(location: Location): String {
        val locationLink = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
        return "Emergency! I need help immediately. I am in danger. Here is my location: $locationLink"
    }

    private fun sendMessages(contactPhoneNumbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        for (phoneNumber in contactPhoneNumbers) {
            try {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                showToastAndLog("SOS message sent to $phoneNumber")
            } catch (e: Exception) {
                showToastAndLog("Failed to send SOS message to $phoneNumber: ${e.message}")
            }
        }
    }

    private fun showToastAndLog(message: String) {
        Log.d("ShakeService", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "SHAKE_SERVICE_CHANNEL"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(notificationChannelId, "Shake Service Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Shake Service")
            .setContentText("Shake Service is running in the foreground")

            .build()
    }
}
