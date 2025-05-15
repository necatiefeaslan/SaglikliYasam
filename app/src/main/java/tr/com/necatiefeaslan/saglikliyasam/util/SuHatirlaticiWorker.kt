package tr.com.necatiefeaslan.saglikliyasam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import tr.com.necatiefeaslan.saglikliyasam.R

class SuHatirlaticiWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "su_hatirlatici"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Su Hatırlatıcı", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Bildirimi göster
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Su İçme Zamanı!")
            .setContentText("Düzenli su içmeyi unutmayın!")
            .setSmallIcon(R.drawable.ic_water)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(100, notification)
        
        return Result.success()
    }
} 