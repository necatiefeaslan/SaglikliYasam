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
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        if (level in 1..10) {
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
                            Toast.makeText(context, "Pil uyarı SMS'i gönderildi", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "SMS gönderilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }
} 