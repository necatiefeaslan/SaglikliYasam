package tr.com.necatiefeaslan.saglikliyasam.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.telephony.SmsManager
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BatteryLevelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val prefs = context.getSharedPreferences("BatteryAlertPrefs", Context.MODE_PRIVATE)
        val wasBelow10 = prefs.getBoolean("was_below_10_percent", false)
        
        if (level in 1..10) {
            // Pil seviyesi %10'un altında
            if (!wasBelow10) {
                // Daha önce %10'un altına düşmediyse SMS gönder
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val db = FirebaseFirestore.getInstance()
                db.collection("kullanicilar").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val phoneNumber = doc.getString("telefon")
                        if (!phoneNumber.isNullOrEmpty()) {
                            try {
                                val smsManager = SmsManager.getDefault()
                                val message = "Uyarı: Pil seviyeniz %10'un altına düştü!"
                                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                                
                                // Pil durumunu güncelle
                                prefs.edit().putBoolean("was_below_10_percent", true).apply()
                                
                                Toast.makeText(context, "Pil uyarı SMS'i gönderildi", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "SMS gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
        } else if (level > 10) {
            // Pil seviyesi %10'un üzerine çıktı, durumu sıfırla
            if (wasBelow10) {
                prefs.edit().putBoolean("was_below_10_percent", false).apply()
            }
        }
    }
} 