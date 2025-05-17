package tr.com.necatiefeaslan.saglikliyasam

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import tr.com.necatiefeaslan.saglikliyasam.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import tr.com.necatiefeaslan.saglikliyasam.ui.changepassword.ChangePasswordFragment
import tr.com.necatiefeaslan.saglikliyasam.ui.settings.SettingsFragment
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import tr.com.necatiefeaslan.saglikliyasam.util.SuHatirlaticiWorker
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tema ayarlarını uygula
        val themePrefs = getSharedPreferences("SaglikliYasamPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = themePrefs.getBoolean("dark_theme", false)
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Giriş yapılıp yapılmadığını kontrol et
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (currentUser != null && isLoggedIn) {
            // Kullanıcı giriş yapmışsa herhangi bir şey yapmaya gerek yok, MainActivity açılacak.
        } else {
            // Kullanıcı giriş yapmamışsa veya SharedPreferences'ta giriş bilgisi yoksa LoginActivity'ye yönlendir
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // MainActivity'yi kapat
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_settings
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // --- Kullanıcı bilgilerini header'a yerleştir ---
        val headerView = navView.getHeaderView(0)
        val imageViewProfile = headerView.findViewById<ImageView>(R.id.imageViewProfile)
        val textViewName = headerView.findViewById<TextView>(R.id.textViewName)
        val textViewEmail = headerView.findViewById<TextView>(R.id.textViewEmail)

        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("kullanicilar").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val ad = document.getString("ad") ?: ""
                        val soyad = document.getString("soyad") ?: ""
                        val eposta = document.getString("e_posta") ?: ""
                        val profilUrl = document.getString("profilUrl")
                        textViewName.text = "$ad $soyad"
                        textViewEmail.text = eposta

                        if (!profilUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profilUrl)
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .circleCrop()
                                .into(imageViewProfile)
                        } else {
                            imageViewProfile.setImageResource(R.drawable.ic_person)
                        }
                    }
                }
        }

        // Su hatırlatıcı bildirimi için WorkManager başlat (kullanıcı tercihine göre)
        setupWaterReminderWorker()

        // SMS izinlerini iste
        requestSmsPermission()

        // BatteryLevelReceiver'ı programatik olarak kaydet
        // Manifest'teki kayıta ek olarak, bazı telefonlar için daha güvenli
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(tr.com.necatiefeaslan.saglikliyasam.util.BatteryLevelReceiver(), filter)
        
        // Adım servisi için izin kontrolü ve başlatma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION), 1001)
            } else {
                startStepCounterService()
            }
        } else {
            startStepCounterService()
        }

        // Android 13+ için bildirim izni iste
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
        
        // Kullanıcının telefon numarasını kontrol et
        checkUserPhoneNumber()
    }

    private fun requestSmsPermission() {
        // Eğer SMS izni yoksa
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            // İzin istenmiş mi diye kontrol et
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
                // Kullanıcıya neden izin istediğimizi açıkla
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("SMS İzni Gerekli")
                    .setMessage("Pil seviyeniz düştüğünde size SMS gönderebilmemiz için bu izne ihtiyacımız var.")
                    .setPositiveButton("İzin Ver") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.SEND_SMS),
                            PERMISSION_REQUEST_SMS
                        )
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            } else {
                // İzni doğrudan iste
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.SEND_SMS),
                    PERMISSION_REQUEST_SMS
                )
            }
        }
    }
    
    private fun checkUserPhoneNumber() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        
        db.collection("kullanicilar").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val phoneNumber = document.getString("telefon")
                    
                    if (phoneNumber.isNullOrEmpty()) {
                        // Kullanıcının telefon numarası yok, bilgilendirme göster
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Telefon Numarası Gerekli")
                            .setMessage("Pil uyarıları için telefon numaranızı profil ayarlarından ekleyin.")
                            .setPositiveButton("Tamam", null)
                            .show()
                    }
                }
            }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_SMS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // SMS izni verildi
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "SMS izni verildi, pil uyarıları alacaksınız",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    // SMS izni reddedildi
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        "SMS izni reddedildi, pil uyarıları almayacaksınız",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_logout -> {
                // Firebase ile çıkış yap
                FirebaseAuth.getInstance().signOut()

                // StepCounterService'i durdur
                val stopIntent = Intent(this, StepCounterService::class.java)
                stopService(stopIntent)

                // Çıkış yaptıktan sonra, LoginActivity'ye yönlendir
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)

                // SharedPreferences'teki giriş bilgisini sıfırla
                val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putBoolean("isLoggedIn", false)
                editor.apply()

                finish()  // MainActivity'yi kapatıyoruz
                return true
            }
            R.id.action_change_password -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_change_password)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupWaterReminderWorker() {
        // Kullanıcının seçtiği bildirim sıklığını al
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val reminderMinutes = prefs.getInt(SettingsFragment.PREF_WATER_REMINDER_MINUTES, 60)
        
        // WorkManager'ı kullanıcı tercihleriyle başlat
        val workRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(
            reminderMinutes.toLong(), TimeUnit.MINUTES
        )
            .setInitialDelay(reminderMinutes.toLong(), TimeUnit.MINUTES)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SettingsFragment.WATER_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun startStepCounterService() {
        try {
            val intent = Intent(this, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            android.util.Log.d("MainActivity", "Adım sayacı servisi başlatıldı")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Adım sayacı servisi başlatılamadı: ${e.message}", e)
            Toast.makeText(this, "Adım sayacı başlatılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_SMS = 1
    }
}