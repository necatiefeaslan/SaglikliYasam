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
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            
            // Step sensor kontrolü
            if (stepSensor == null) {
                android.util.Log.e("StepCounterService", "Bu cihazda adım sensörü bulunamadı")
                stopSelf()
                return
            }
            
            // Her başlangıçta initialStepCount'u sıfırla
            initialStepCount = null
            
            // Firestore'dan kullanıcının mevcut adım verisini çek
            fetchFirestoreAdim()
            
            // Adım sensörünü başlat
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            
            // Bildirim oluştur ve foreground servisi başlat
            val notification = createNotification(0)
            startForeground(1, notification)
            
            android.util.Log.d("StepCounterService", "Adım sayacı servisi başlatıldı")
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "Servis başlatma hatası: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        try {
            if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val userId = auth.currentUser?.uid ?: return
                val prefs = getSharedPreferences("StepPrefs", Context.MODE_PRIVATE)
                val key = "${userId}_$today"
                
                // Bugün için kullanıcının başlangıç adım sayısını kontrol et
                if (initialStepCount == null) {
                    initialStepCount = prefs.getInt(key, -1)
                    if (initialStepCount == -1) {
                        // Kullanıcının bugünkü ilk girişi - başlangıç adımını kaydet
                        initialStepCount = event.values[0].toInt()
                        prefs.edit().putInt(key, initialStepCount!!).apply()
                        
                        // Firestore'dan mevcut adım verisini kontrol et
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val docId = "${userId}_$today"
                        db.collection("adim").document(docId).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                // Eğer kullanıcının bugün için zaten adım kaydı varsa, o değeri koru
                                firestoreAdim = doc.getLong("adimsayisi")?.toInt() ?: 0
                                updateNotification(firestoreAdim)
                            } else {
                                updateNotification(0)
                            }
                        }
                        return
                    }
                }
                
                // Bugün için attığı adım sayısını hesapla
                val stepsToday = event.values[0].toInt() - (initialStepCount ?: 0)
                updateNotification(stepsToday)
                kaydetAdimFirestore(stepsToday)
            }
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "Sensör veri işleme hatası: ${e.message}", e)
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
            
            // Adım sayısı negatif olamaz
            val gecerliAdim = if (adim < 0) 0 else adim
            
            val adimKayit = hashMapOf(
                "id" to docId,
                "tarih" to today,
                "adimsayisi" to gecerliAdim,
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
                
                // Bildirimi güncelle
                updateNotification(firestoreAdim)
            }
            .addOnFailureListener {
                // Başarısız olursa sıfır değer kullan
                firestoreAdim = 0
                updateNotification(0)
            }
    }
} 