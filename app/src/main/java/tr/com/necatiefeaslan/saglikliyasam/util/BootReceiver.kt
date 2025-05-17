package tr.com.necatiefeaslan.saglikliyasam.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tr.com.necatiefeaslan.saglikliyasam.ui.settings.SettingsFragment
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Kullanıcının seçtiği bildirim sıklığını al
            val prefs = context.getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
            val reminderMinutes = prefs.getInt(SettingsFragment.PREF_WATER_REMINDER_MINUTES, 60)
            
            // Su hatırlatıcıyı yeniden başlat
            val workRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(
                reminderMinutes.toLong(), TimeUnit.MINUTES
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SettingsFragment.WATER_REMINDER_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            // Adım sayacı servisini başlat
            val serviceIntent = Intent(context, tr.com.necatiefeaslan.saglikliyasam.StepCounterService::class.java)
            context.startService(serviceIntent)
        }
    }
} 