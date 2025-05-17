package tr.com.necatiefeaslan.saglikliyasam.util

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BatteryLevelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BatteryReceiver", "Alınan aksiyon: $action")
        
        // Pil düşük uyarısını dinle
        if (action == Intent.ACTION_BATTERY_LOW) {
            sendLowBatteryAlert(context)
            return
        }
        
        // Pil seviyesi değişimini dinle
        if (action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()
            
            Log.d("BatteryReceiver", "Pil seviyesi: $batteryPct%")
            
            // Pil %10'un altına düştüğünde
            if (batteryPct <= 10) {
                val prefs = context.getSharedPreferences("BatteryAlertPrefs", Context.MODE_PRIVATE)
                val wasBelow10 = prefs.getBoolean("was_below_10_percent", false)
                val lastAlertTime = prefs.getLong("last_alert_time", 0L)
                val currentTime = System.currentTimeMillis()
                
                // Son SMS'in üzerinden en az 1 saat geçtiyse ve daha önce uyarı verilmediyse
                if (!wasBelow10 || (currentTime - lastAlertTime > 3600000)) {
                    sendLowBatteryAlert(context)
                    
                    // Uyarı durumunu güncelle
                    prefs.edit()
                        .putBoolean("was_below_10_percent", true)
                        .putLong("last_alert_time", currentTime)
                        .apply()
                }
            } else if (batteryPct > 20) {
                // Pil %20'nin üzerine çıktığında durumu sıfırla
                val prefs = context.getSharedPreferences("BatteryAlertPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("was_below_10_percent", false).apply()
            }
        }
    }
    
    private fun sendLowBatteryAlert(context: Context) {
        // SMS izni kontrolü
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BatteryReceiver", "SMS izni yok")
            return
        }
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e("BatteryReceiver", "Kullanıcı girişi yapılmamış")
            return
        }
        
        Log.d("BatteryReceiver", "Telefon numarası alınıyor...")
        val db = FirebaseFirestore.getInstance()
        db.collection("kullanicilar").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val phoneNumber = doc.getString("telefon")
                    
                    if (!phoneNumber.isNullOrEmpty()) {
                        try {
                            Log.d("BatteryReceiver", "SMS gönderiliyor: $phoneNumber")
                            
                            val smsManager = SmsManager.getDefault()
                            val message = "⚠️ Sağlıklı Yaşam: Pil seviyeniz kritik seviyede! Lütfen cihazınızı şarj edin."
                            
                            // SMS gönderme
                            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                            
                            Toast.makeText(context, "Pil uyarı SMS'i gönderildi: $phoneNumber", Toast.LENGTH_LONG).show()
                            Log.d("BatteryReceiver", "SMS başarıyla gönderildi")
                        } catch (e: Exception) {
                            Log.e("BatteryReceiver", "SMS gönderme hatası: ${e.message}", e)
                            Toast.makeText(context, "SMS gönderilemedi: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("BatteryReceiver", "Telefon numarası bulunamadı")
                    }
                } else {
                    Log.e("BatteryReceiver", "Kullanıcı belgesi bulunamadı")
                }
            }
            .addOnFailureListener { e ->
                Log.e("BatteryReceiver", "Firestore veri çekme hatası: ${e.message}", e)
            }
    }
} 