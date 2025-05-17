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
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StepCounterService : Service(), SensorEventListener {
    
    private val TAG = "StepCounterService"
    private val NOTIFICATION_ID = 9999
    private val CHANNEL_ID = "saglikliyasam_service_channel"
    
    // Adım sayacı değişkenleri
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val initialStepCountMap = mutableMapOf<String, Int>()
    private var currentSteps: Int = 0
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "StepCounterService onCreate")
        
        try {
            // Sensör yöneticisini başlat
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            
            // Sensör kontrolü
            if (stepSensor == null) {
                Log.w(TAG, "Bu cihazda adım sayacı sensörü bulunamadı")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sensör başlatma hatası: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand çağrıldı")
        
        try {
            // Foreground servisi başlat
            val notification = createNotification(0)
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground servis başlatıldı")
            
            // Adım sensörünü kaydet (eğer varsa)
            stepSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Adım sensörü dinleniyor")
            }
            
            // Veritabanından kayıtlı adım verilerini çek
            fetchFirestoreSteps()
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand hatası: ${e.message}", e)
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Sensör kaydını kaldır
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Servis ve sensör dinlemesi durduruluyor")
        } catch (e: Exception) {
            Log.e(TAG, "Servis durdurma hatası: ${e.message}", e)
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        try {
            // Sadece adım sensörü olaylarını işle
            if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                val totalSteps = event.values[0].toInt()
                processSteps(totalSteps)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sensör verisi işleme hatası: ${e.message}", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sensör hassasiyeti değişikliğini işleme gerek yok
    }
    
    private fun processSteps(totalSteps: Int) {
        try {
            // Bugünün tarihi
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Kullanıcı bilgilerini al
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            
            // Kullanıcı+tarih için benzersiz anahtar
            val prefs = getSharedPreferences("StepPrefs", Context.MODE_PRIVATE)
            val key = "${userId}_$today"
            
            // Kullanıcı için mevcut başlangıç değeri var mı kontrol et
            if (!initialStepCountMap.containsKey(key)) {
                // SharedPreferences'dan değeri oku
                val savedInitialCount = prefs.getInt(key, -1)
                
                // İlk kez çalıştırılıyorsa başlangıç değerini kaydet
                if (savedInitialCount == -1) {
                    initialStepCountMap[key] = totalSteps
                    prefs.edit().putInt(key, totalSteps).apply()
                    Log.d(TAG, "Yeni kullanıcı-gün için başlangıç adım sayısı kaydedildi: $key = $totalSteps")
                    return
                } else {
                    // Kaydedilmiş değeri kullan
                    initialStepCountMap[key] = savedInitialCount
                    Log.d(TAG, "Kaydedilmiş başlangıç adım sayısı yüklendi: $key = $savedInitialCount")
                }
            }
            
            // Bugün için adım sayısını hesapla
            val initialCount = initialStepCountMap[key] ?: totalSteps
            currentSteps = totalSteps - initialCount
            if (currentSteps < 0) currentSteps = 0 // Negatif değer kontrolü
            
            // Bildirimi güncelle
            updateNotification(currentSteps)
            
            // Firestore'a kaydet
            saveStepsToFirestore(currentSteps)
            
        } catch (e: Exception) {
            Log.e(TAG, "Adım işleme hatası: ${e.message}", e)
        }
    }
    
    private fun createNotification(steps: Int): Notification {
        try {
            // Bildirim kanalı oluştur
            createNotificationChannel()
            
            // Bildirim oluştur
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Adım Sayacı")
                .setContentText("Bugün $steps adım attınız")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                
            return builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim oluşturma hatası: ${e.message}", e)
            
            // Fallback bildirim
            val fallbackBuilder = NotificationCompat.Builder(this, "default")
                .setContentTitle("SağlıklıYaşam")
                .setContentText("Adım sayacı çalışıyor")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                
            return fallbackBuilder.build()
        }
    }
    
    private fun updateNotification(steps: Int) {
        try {
            val notification = createNotification(steps)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim güncelleme hatası: ${e.message}", e)
        }
    }
    
    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Adım Sayacı"
                val description = "Adım sayacı bildirimleri"
                val importance = NotificationManager.IMPORTANCE_LOW
                
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    this.description = description
                }
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim kanalı oluşturma hatası: ${e.message}", e)
        }
    }
    
    private fun saveStepsToFirestore(steps: Int) {
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()
            val docId = "${userId}_$today"
            
            // Firestore dökümanını al veya oluştur
            val adimKoleksiyonu = db.collection("adim")
            val docRef = adimKoleksiyonu.document(docId)
            
            docRef.get().addOnSuccessListener { docSnapshot ->
                try {
                    // Varsayılan hedef adım
                    val hedefAdim = if (docSnapshot.exists()) 
                        docSnapshot.getLong("hedefadim")?.toInt() ?: 8000 
                    else 
                        8000
                    
                    // Yeni verileri hazırla
                    val adimKayit = hashMapOf(
                        "id" to docId,
                        "tarih" to today,
                        "adimsayisi" to steps,
                        "kullaniciId" to userId,
                        "hedefadim" to hedefAdim
                    )
                    
                    // Veritabanına kaydet
                    docRef.set(adimKayit)
                        .addOnSuccessListener {
                            Log.d(TAG, "Adım verileri Firestore'a kaydedildi: $steps")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Adım verileri kaydedilemedi: ${e.message}", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Firestore veri işleme hatası: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Adım kaydetme hatası: ${e.message}", e)
        }
    }
    
    private fun fetchFirestoreSteps() {
        try {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()
            val docId = "${userId}_$today"
            
            db.collection("adim").document(docId).get()
                .addOnSuccessListener { docSnapshot ->
                    try {
                        if (docSnapshot.exists()) {
                            val adimSayisi = docSnapshot.getLong("adimsayisi")?.toInt() ?: 0
                            updateNotification(adimSayisi)
                            Log.d(TAG, "Firestore'dan adım verisi alındı: $adimSayisi")
                        } else {
                            updateNotification(0)
                            Log.d(TAG, "Bugün için Firestore'da adım verisi bulunamadı")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Firestore veri işleme hatası: ${e.message}", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore veri çekme hatası: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Adım verisi çekme hatası: ${e.message}", e)
        }
    }
} 