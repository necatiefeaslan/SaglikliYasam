package tr.com.necatiefeaslan.saglikliyasam

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StepCounterService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var initialStepCount: Int? = null
    private var firestoreAdim: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        fetchFirestoreAdim()
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        startForeground(1, createNotification(0))
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            val prefs = getSharedPreferences("StepPrefs", Context.MODE_PRIVATE)
            val key = "${userId}_$today"
            if (initialStepCount == null) {
                initialStepCount = prefs.getInt(key, -1)
                if (initialStepCount == -1) {
                    initialStepCount = event.values[0].toInt()
                    prefs.edit().putInt(key, initialStepCount!!).apply()
                }
            }
            val stepsToday = event.values[0].toInt() - (initialStepCount ?: 0)
            updateNotification(stepsToday)
            kaydetAdimFirestore(stepsToday)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(steps: Int): Notification {
        val channelId = "adim_bildirim"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Adım Takibi", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Adım Takibi")
            .setContentText("Bugünkü adımınız: $steps")
            .setSmallIcon(R.drawable.ic_steps) // Kendi adım ikonunu ekle
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(steps: Int) {
        val notification = createNotification(steps)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun kaydetAdimFirestore(adim: Int) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val docId = "${userId}_$today"
        val ref = db.collection("adim").document(docId)
        ref.get().addOnSuccessListener { doc ->
            val hedef = if (doc.exists()) doc.getLong("hedefadim")?.toInt() ?: 8000 else 8000
            val adimKayit = hashMapOf(
                "id" to docId,
                "tarih" to today,
                "adimsayisi" to adim,
                "kullaniciId" to userId,
                "hedefadim" to hedef
            )
            ref.set(adimKayit)
        }
    }

    private fun fetchFirestoreAdim() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val docId = "${userId}_$today"
        db.collection("adim").document(docId).get()
            .addOnSuccessListener { doc ->
                firestoreAdim = if (doc.exists()) doc.getLong("adimsayisi")?.toInt() ?: 0 else 0
            }
    }
} 