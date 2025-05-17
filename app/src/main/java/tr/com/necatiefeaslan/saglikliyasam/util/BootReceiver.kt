package tr.com.necatiefeaslan.saglikliyasam.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Sabit bildirim sıklığı (30 dakika)
            val reminderMinutes = 30
            
            // Su hatırlatıcıyı yeniden başlat
            val workRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(
                reminderMinutes.toLong(), TimeUnit.MINUTES
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "water_reminder_periodic",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            // Adım sayacı servisini başlat
            val serviceIntent = Intent(context, tr.com.necatiefeaslan.saglikliyasam.StepCounterService::class.java)
            context.startService(serviceIntent)
        }
    }
} 