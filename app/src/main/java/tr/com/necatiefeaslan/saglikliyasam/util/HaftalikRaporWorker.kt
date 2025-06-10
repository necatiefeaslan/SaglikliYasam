package tr.com.necatiefeaslan.saglikliyasam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import tr.com.necatiefeaslan.saglikliyasam.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class HaftalikRaporWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val CHANNEL_ID = "haftalik_rapor_channel"
        private const val NOTIFICATION_ID = 3333
        private const val TAG = "HaftalikRaporWorker"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""

    override fun doWork(): Result {
        try {
            Log.d(TAG, "Haftalık rapor oluşturuluyor - ${Date()}")
            
            // Kullanıcı giriş yapmamışsa, bildirimi gösterme
            if (userId.isEmpty()) {
                Log.d(TAG, "Kullanıcı giriş yapmamış, rapor oluşturulmadı")
                return Result.success()
            }
            
            createNotificationChannel()
            getHaftalikRapor()
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Haftalık rapor hatası: ${e.message}", e)
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Haftalık Rapor"
                val descriptionText = "Haftalık adım ve su raporu bildirimleri"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Bildirim kanalı oluşturuldu")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim kanalı oluşturma hatası: ${e.message}", e)
        }
    }

    private fun getHaftalikRapor() {
        // Mevcut haftanın başını (Pazartesi) bul
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = calendar.time
        
        // Pazar günü için tarihi hesapla
        val sundayCalendar = Calendar.getInstance()
        sundayCalendar.firstDayOfWeek = Calendar.MONDAY
        sundayCalendar.time = monday
        sundayCalendar.add(Calendar.DAY_OF_WEEK, 6) // Pazar (haftanın son günü)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = dateFormat.format(monday)
        val endDate = dateFormat.format(sundayCalendar.time)
        
        Log.d(TAG, "Haftalık rapor için tarih aralığı: $startDate - $endDate")
        
        // Adım verilerini topla
        val adimMap = mutableMapOf<String, Pair<Int, Int>>() // Tarih -> (Adım, Hedef)
        
        // Su verilerini topla
        val suMap = mutableMapOf<String, Pair<Int, Int>>() // Tarih -> (Miktar, Hedef)
        
        // Tüm hafta için varsayılan değerlerle doldur
        calendar.time = monday
        for (i in 0..6) {
            val currentDate = dateFormat.format(calendar.time)
            adimMap[currentDate] = Pair(0, 8000) // Varsayılan adım hedefi
            suMap[currentDate] = Pair(0, 2000) // Varsayılan su hedefi
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Adım verilerini Firestore'dan çek
        db.collection("adim")
            .whereEqualTo("kullaniciId", userId)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    val adim = doc.toObject(tr.com.necatiefeaslan.saglikliyasam.model.Adim::class.java)
                    val adimTarih = adim.tarih
                    
                    // Haftaya ait mi kontrol et
                    if (adimTarih >= startDate && adimTarih <= endDate) {
                        adimMap[adimTarih] = Pair(adim.adimsayisi, adim.hedefadim)
                    }
                }
                
                // Su verilerini çek
                db.collection("su")
                    .whereEqualTo("kullaniciId", userId)
                    .get()
                    .addOnSuccessListener { suDocs ->
                        for (doc in suDocs) {
                            val su = doc.toObject(tr.com.necatiefeaslan.saglikliyasam.model.Su::class.java)
                            val suTarih = su.tarih
                            
                            // Haftaya ait mi kontrol et
                            if (suTarih >= startDate && suTarih <= endDate) {
                                suMap[suTarih] = Pair(su.miktar, su.hedefsu)
                            }
                        }
                        
                        // Bildirimi göster
                        showWeeklyReport(adimMap, suMap)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Su verisi çekme hatası: ${e.message}", e)
                        showWeeklyReport(adimMap, suMap) // Hata olsa bile eldeki verilerle raporu göster
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Adım verisi çekme hatası: ${e.message}", e)
            }
    }
    
    private fun showWeeklyReport(adimMap: Map<String, Pair<Int, Int>>, suMap: Map<String, Pair<Int, Int>>) {
        try {
            // Haftalık toplamları hesapla
            var toplamAdim = 0
            var toplamAdimHedef = 0
            var toplamSu = 0
            var toplamSuHedef = 0
            var adimHedefTamamlananGun = 0
            var suHedefTamamlananGun = 0
            
            for ((_, adimDeger) in adimMap) {
                toplamAdim += adimDeger.first
                toplamAdimHedef += adimDeger.second
                if (adimDeger.first >= adimDeger.second && adimDeger.second > 0) {
                    adimHedefTamamlananGun++
                }
            }
            
            for ((_, suDeger) in suMap) {
                toplamSu += suDeger.first
                toplamSuHedef += suDeger.second
                if (suDeger.first >= suDeger.second && suDeger.second > 0) {
                    suHedefTamamlananGun++
                }
            }
            
            // Ortalama hesapla
            val gunSayisi = adimMap.size
            val adimOrt = if (gunSayisi > 0) toplamAdim / gunSayisi else 0
            val suOrt = if (gunSayisi > 0) toplamSu / gunSayisi else 0
            
            // Haftalık başarı yüzdesi
            val adimBasariYuzdesi = if (toplamAdimHedef > 0) ((toplamAdim.toFloat() / toplamAdimHedef) * 100).roundToInt() else 0
            val suBasariYuzdesi = if (toplamSuHedef > 0) ((toplamSu.toFloat() / toplamSuHedef) * 100).roundToInt() else 0
            
            // Bildirim içeriği
            val title = "Haftalık Sağlık Raporunuz"
            val content = "Adım: $toplamAdim adım (hedefin %$adimBasariYuzdesi'i, $adimHedefTamamlananGun gün hedef tamamlandı)\n" +
                          "Su: $toplamSu ml (hedefin %$suBasariYuzdesi'i, $suHedefTamamlananGun gün hedef tamamlandı)"
            
            // Varsayılan bildirim sesi
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText("Haftalık adım ve su raporunuz hazır!")
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setSound(defaultSoundUri)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            
            Log.d(TAG, "Haftalık rapor bildirimi gösterildi")
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim gösterme hatası: ${e.message}", e)
        }
    }
} 