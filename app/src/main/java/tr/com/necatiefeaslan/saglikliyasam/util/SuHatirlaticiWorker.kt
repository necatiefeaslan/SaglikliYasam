package tr.com.necatiefeaslan.saglikliyasam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import tr.com.necatiefeaslan.saglikliyasam.R

class SuHatirlaticiWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val CHANNEL_ID = "su_hatirlatici_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun doWork(): Result {
        createNotificationChannel()
        showNotification()
        
        // Hata ayıklama için log ekle
        android.util.Log.d("SuHatirlaticiWorker", "Su hatırlatıcı bildirimi gönderildi - ${java.util.Date()}")
        
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Su Hatırlatıcı"
            val descriptionText = "Su içme hatırlatmaları için bildirim kanalı"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        // Kullanıcının ayarladığı bildirim sıklığını al
        val prefs = applicationContext.getSharedPreferences("SaglikliYasamPrefs", Context.MODE_PRIVATE)
        val reminderMinutes = prefs.getInt("water_reminder_minutes", 60)
        
        // Kullanıcı ayarına göre zaman dilimini belirleme
        val timeText = when (reminderMinutes) {
            30 -> "30 dakikada"
            60 -> "saatte"
            120 -> "2 saatte"
            else -> "$reminderMinutes dakikada"
        }
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water)
            .setContentTitle("Su İçme Zamanı")
            .setContentText("Sağlıklı kalmak için $timeText bir su için!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
} 