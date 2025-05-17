package tr.com.necatiefeaslan.saglikliyasam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import tr.com.necatiefeaslan.saglikliyasam.R
import android.util.Log

class SuHatirlaticiWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val CHANNEL_ID = "su_hatirlatici_channel"
        private const val NOTIFICATION_ID = 2222
        private const val TAG = "SuHatirlaticiWorker"
    }

    override fun doWork(): Result {
        try {
            Log.d(TAG, "Su hatırlatıcı başladı - ${java.util.Date()}")
            createNotificationChannel()
            showNotification()
            Log.d(TAG, "Su hatırlatıcı bildirimi gönderildi")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Su hatırlatıcı hatası: ${e.message}", e)
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Su Hatırlatıcı"
                val descriptionText = "Su içme hatırlatmaları için bildirim kanalı"
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

    private fun showNotification() {
        try {
            // Sabit bildirim sıklığı (30 dakika)
            val timeText = "30 dakikada"
            
            // Varsayılan bildirim sesi
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // ic_water yerine varsayılan simge kullan
                .setContentTitle("Su İçme Zamanı")
                .setContentText("Sağlıklı kalmak için $timeText bir su için!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .setSound(defaultSoundUri)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            
            Log.d(TAG, "Su hatırlatıcı bildirimi gösterildi: Sıklık = 30 dk")
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim gösterme hatası: ${e.message}", e)
        }
    }
} 