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
            val channel = NotificationChannel(channelId, "Su Hatırlatıcı", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Su Hatırlatıcı")
            .setContentText("Suyunuzu içtiniz mi?")
            .setSmallIcon(R.drawable.ic_water)
            .build()
        notificationManager.notify(1, notification)
        return Result.success()
    }
} 