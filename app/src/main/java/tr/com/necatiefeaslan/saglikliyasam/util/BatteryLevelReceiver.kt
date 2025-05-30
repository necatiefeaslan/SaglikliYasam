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
        try {
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
                if (level == -1 || scale == -1) return
                
                val batteryPct = level * 100 / scale.toFloat()
                
                Log.d("BatteryReceiver", "Pil seviyesi: $batteryPct%")
                
                // Pil %10'un altına düştüğünde
                if (batteryPct <= 10) {
                    val prefs = context.getSharedPreferences("BatteryAlertPrefs", Context.MODE_PRIVATE)
                    val wasBelow10 = prefs.getBoolean("was_below_10_percent", false)
                    
                    // Sadece ilk kez %10'un altına düşerse bildirim gönder
                    if (!wasBelow10) {
                        sendLowBatteryAlert(context)
                        
                        // Uyarı durumunu güncelle - sadece bir kez
                        prefs.edit()
                            .putBoolean("was_below_10_percent", true)
                            .apply()
                        
                        Log.d("BatteryReceiver", "Pil %10 altına ilk kez düştü, bildirim gönderildi")
                    } else {
                        Log.d("BatteryReceiver", "Pil zaten %10 altında, tekrar bildirim gönderilmeyecek")
                    }
                } else if (batteryPct > 20) {
                    // Pil %20'nin üzerine çıktığında durumu sıfırla
                    val prefs = context.getSharedPreferences("BatteryAlertPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("was_below_10_percent", false).apply()
                    Log.d("BatteryReceiver", "Pil %20'nin üzerine çıktı, alarm durumu sıfırlandı")
                }
            }
        } catch (e: Exception) {
            Log.e("BatteryReceiver", "Pil durumu işlenirken hata oluştu: ${e.message}", e)
        }
    }
    
    private fun sendLowBatteryAlert(context: Context) {
        try {
            // SMS izni kontrolü - izin yoksa bile devam et
            val hasSmsPermission = ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasSmsPermission) {
                Log.w("BatteryReceiver", "SMS izni yok, sadece telefon numarası kontrol edilecek")
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
                    try {
                        if (doc != null && doc.exists()) {
                            val phoneNumber = doc.getString("telefon")
                            
                            if (!phoneNumber.isNullOrEmpty()) {
                                if (hasSmsPermission) {
                                    try {
                                        Log.d("BatteryReceiver", "SMS gönderiliyor: $phoneNumber")
                                        
                                        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                            context.getSystemService(SmsManager::class.java)
                                        } else {
                                            SmsManager.getDefault()
                                        }
                                        
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
                                    // SMS izni olmadığında bildirim gösterelim
                                    val notificationUtil = NotificationUtil(context)
                                    notificationUtil.showNotification(
                                        "Pil Seviyesi Düşük",
                                        "Pil seviyeniz kritik seviyede! Lütfen cihazınızı şarj edin.",
                                        3333
                                    )
                                    Log.d("BatteryReceiver", "SMS izni olmadığı için bildirim gösterildi")
                                }
                            } else {
                                Log.e("BatteryReceiver", "Telefon numarası bulunamadı")
                                // Telefon numarası yoksa da bildirim gösterelim
                                val notificationUtil = NotificationUtil(context)
                                notificationUtil.showNotification(
                                    "Pil Seviyesi Düşük",
                                    "Pil seviyeniz kritik seviyede! Lütfen cihazınızı şarj edin.",
                                    3333
                                )
                            }
                        } else {
                            Log.e("BatteryReceiver", "Kullanıcı belgesi bulunamadı")
                        }
                    } catch (e: Exception) {
                        Log.e("BatteryReceiver", "Firestore verisi işlenirken hata: ${e.message}", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BatteryReceiver", "Firestore veri çekme hatası: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e("BatteryReceiver", "Pil uyarısı gönderirken genel hata: ${e.message}", e)
        }
    }
} 