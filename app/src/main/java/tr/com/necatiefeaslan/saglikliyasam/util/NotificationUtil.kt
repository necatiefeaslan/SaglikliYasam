package tr.com.necatiefeaslan.saglikliyasam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import tr.com.necatiefeaslan.saglikliyasam.R

class NotificationUtil(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "saglikliyasam_alerts_channel"
        private const val TAG = "NotificationUtil"
    }
    
    fun showNotification(title: String, message: String, notificationId: Int) {
        try {
            createNotificationChannel()
            
            // Varsayılan bildirim sesi
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setSound(defaultSoundUri)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notificationBuilder.build())
            
            Log.d(TAG, "Bildirim gösterildi: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim gösterme hatası: ${e.message}", e)
        }
    }
    
    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Sağlıklı Yaşam Uyarıları"
                val descriptionText = "Sağlıklı Yaşam uygulaması uyarı bildirimleri"
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
} 