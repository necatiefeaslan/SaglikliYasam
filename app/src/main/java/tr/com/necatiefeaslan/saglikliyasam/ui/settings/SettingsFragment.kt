package tr.com.necatiefeaslan.saglikliyasam.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import tr.com.necatiefeaslan.saglikliyasam.R
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import tr.com.necatiefeaslan.saglikliyasam.util.SuHatirlaticiWorker
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchDarkTheme: SwitchMaterial
    private lateinit var radioGroup: android.widget.RadioGroup
    private lateinit var saveButton: MaterialButton
    private lateinit var rootView: View

    companion object {
        const val PREFS_NAME = "SaglikliYasamPrefs"
        const val PREF_DARK_THEME = "dark_theme"
        const val PREF_WATER_REMINDER_MINUTES = "water_reminder_minutes"
        const val WATER_REMINDER_WORK_NAME = "water_reminder_periodic"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_settings, container, false)

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        switchDarkTheme = rootView.findViewById(R.id.switchDarkTheme)
        radioGroup = rootView.findViewById(R.id.radioGroupWaterReminder)
        saveButton = rootView.findViewById(R.id.buttonSaveSettings)

        // Mevcut ayarları yükle
        loadSettings()

        saveButton.setOnClickListener {
            saveSettings()
        }

        return rootView
    }

    private fun loadSettings() {
        // Tema ayarı
        val isDarkTheme = prefs.getBoolean(PREF_DARK_THEME, false)
        switchDarkTheme.isChecked = isDarkTheme

        // Su hatırlatıcı sıklığı
        val waterReminderMinutes = prefs.getInt(PREF_WATER_REMINDER_MINUTES, 60)
        when (waterReminderMinutes) {
            30 -> rootView.findViewById<RadioButton>(R.id.radio30Min).isChecked = true
            60 -> rootView.findViewById<RadioButton>(R.id.radio1Hour).isChecked = true
            120 -> rootView.findViewById<RadioButton>(R.id.radio2Hour).isChecked = true
        }
    }

    private fun saveSettings() {
        val editor = prefs.edit()

        // Tema ayarlarını kaydet
        val isDarkTheme = switchDarkTheme.isChecked
        editor.putBoolean(PREF_DARK_THEME, isDarkTheme)
        
        // Tema değişikliğini uygula
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Su hatırlatıcı süresini kaydet
        var waterReminderMinutes = 60 // varsayılan değer
        when (radioGroup.checkedRadioButtonId) {
            R.id.radio30Min -> waterReminderMinutes = 30
            R.id.radio1Hour -> waterReminderMinutes = 60
            R.id.radio2Hour -> waterReminderMinutes = 120
        }
        editor.putInt(PREF_WATER_REMINDER_MINUTES, waterReminderMinutes)

        editor.apply()

        // Su hatırlatıcı worker'ı güncelle
        scheduleWaterReminder(waterReminderMinutes)

        Toast.makeText(context, "Ayarlar kaydedildi", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleWaterReminder(minutes: Int) {
        val workManager = WorkManager.getInstance(requireContext())
        
        // Eski işi iptal et
        workManager.cancelUniqueWork(WATER_REMINDER_WORK_NAME)
        
        // Yeni işi zamanla
        val waterReminderRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(
            minutes.toLong(), TimeUnit.MINUTES
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            WATER_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            waterReminderRequest
        )
    }
} 