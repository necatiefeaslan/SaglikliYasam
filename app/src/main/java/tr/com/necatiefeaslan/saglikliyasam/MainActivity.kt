package tr.com.necatiefeaslan.saglikliyasam

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import tr.com.necatiefeaslan.saglikliyasam.util.SuHatirlaticiWorker
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate başladı")
        
        // Layout bağlama
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        // Giriş kontrolü
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)
        
        if (currentUser == null || !isLoggedIn) {
            // Kullanıcı giriş yapmamışsa LoginActivity'ye yönlendir
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() 
            return
        }

        // Adım sayacı servisini kontrol et - yeniden başlatma talebi varsa
        val shouldRestartStepService = intent.getBooleanExtra("RESTART_STEP_SERVICE", false)
        if (shouldRestartStepService) {
            Log.d(TAG, "Adım sayacı servisi yeniden başlatılıyor")
            val stopIntent = Intent(this, StepCounterService::class.java)
            stopService(stopIntent)
        }

        // Navigation Drawer ve Navigation Component ayarları
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        // Kullanıcı bilgilerini header'a yerleştir
        loadUserInfoToHeader(navView, auth)

        // Adım sayacı servisini başlat
        startStepCounterService()
        
        // Su hatırlatıcı bildirimi için WorkManager başlat
        setupWaterReminderWorker()
        
        // Android 13+ için bildirim izni iste
        requestNotificationPermissionIfNeeded()
        
        Log.d(TAG, "MainActivity onCreate tamamlandı")
    }
    
    private fun loadUserInfoToHeader(navView: NavigationView, auth: FirebaseAuth) {
        try {
            val headerView = navView.getHeaderView(0)
            val imageViewProfile = headerView.findViewById<ImageView>(R.id.imageViewProfile)
            val textViewName = headerView.findViewById<TextView>(R.id.textViewName)
            val textViewEmail = headerView.findViewById<TextView>(R.id.textViewEmail)
            
            val db = FirebaseFirestore.getInstance()
            val userId = auth.currentUser?.uid
            if (userId != null) {
                db.collection("kullanicilar").document(userId).get()
                    .addOnSuccessListener { document ->
                        try {
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Kullanıcı verisi işleme hatası: ${e.message}", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Kullanıcı verisi çekme hatası: ${e.message}", e)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Header bilgileri yükleme hatası: ${e.message}", e)
        }
    }
    
    private fun checkStepCounterPermissionAndStart() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val permission = android.Manifest.permission.ACTIVITY_RECOGNITION
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
                } else {
                    startStepCounterService()
                }
            } else {
                startStepCounterService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Adım sayacı izin kontrolü hatası: ${e.message}", e)
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bildirim izni isteme hatası: ${e.message}", e)
        }
    }

    private fun requestSmsPermission() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "SMS izni isteme hatası: ${e.message}", e)
        }
    }
    
    private fun checkUserPhoneNumber() {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val db = FirebaseFirestore.getInstance()
            
            db.collection("kullanicilar").document(userId).get()
                .addOnSuccessListener { document ->
                    try {
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Telefon numarası kontrolü hatası: ${e.message}", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Kullanıcı verisi çekme hatası: ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Telefon numarası kontrolü genel hatası: ${e.message}", e)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        try {
            when (requestCode) {
                PERMISSION_REQUEST_SMS -> {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // SMS izni verildi
                        Snackbar.make(
                            binding.root,
                            "SMS izni verildi, pil uyarıları alacaksınız",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        // SMS izni reddedildi
                        Snackbar.make(
                            binding.root,
                            "SMS izni reddedildi, pil uyarıları almayacaksınız",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                1001 -> { // Adım sayacı izni
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        startStepCounterService()
                    } else {
                        Toast.makeText(this, "Adım sayacı için fiziksel aktivite izni gerekli", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "İzin sonucu işleme hatası: ${e.message}", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            menuInflater.inflate(R.menu.main, menu)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Menü oluşturma hatası: ${e.message}", e)
            return false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.action_logout -> {
                    // StepCounterService'i durdur
                    val stopIntent = Intent(this, StepCounterService::class.java)
                    stopService(stopIntent)
                    Log.d(TAG, "Çıkış yapılırken adım sayacı servisi durduruldu")
                    
                    // Firebase ile çıkış yap
                    FirebaseAuth.getInstance().signOut()
                    
                    // LoginActivity'ye yönlendir
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    
                    // SharedPreferences'teki giriş bilgisini sıfırla
                    val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putBoolean("isLoggedIn", false)
                    editor.apply()

                    finish()
                    return true
                }
                R.id.action_change_password -> {
                    // Şifre değiştirme ekranına git
                    val navController = findNavController(R.id.nav_host_fragment_content_main)
                    navController.navigate(R.id.nav_change_password)
                    return true
                }
                else -> return super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Menü işleme hatası: ${e.message}", e)
            return false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        try {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        } catch (e: Exception) {
            Log.e(TAG, "NavigateUp hatası: ${e.message}", e)
            return super.onSupportNavigateUp()
        }
    }

    private fun setupWaterReminderWorker() {
        try {
            Log.d(TAG, "Su hatırlatıcı WorkManager ayarlanıyor")
            
            // Sabit bildirim sıklığı (30 dakika)
            val reminderMinutes = 30
            
            // WorkManager'ı varsayılan değerle başlat
            val workRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(
                reminderMinutes.toLong(), TimeUnit.MINUTES
            )
                .setInitialDelay(reminderMinutes.toLong(), TimeUnit.MINUTES)
                .build()
                
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "water_reminder_periodic",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.d(TAG, "Su hatırlatıcı ayarlandı: $reminderMinutes dakika")
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager kurulumu hatası: ${e.message}", e)
            Toast.makeText(this, "Su hatırlatıcı ayarlanamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStepCounterService() {
        try {
            Log.d(TAG, "Adım sayacı servisi başlatılıyor")
            
            // Mevcut kullanıcı kimliğini al
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            Log.d(TAG, "Aktif kullanıcı: $userId")
            
            val intent = Intent(this, StepCounterService::class.java)
            
            // API 26+ için foreground servis başlatma
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Log.d(TAG, "Adım sayacı servisi başlatma komutu gönderildi")
        } catch (e: Exception) {
            Log.e(TAG, "Adım sayacı servisi başlatma hatası: ${e.message}", e)
            Toast.makeText(this, "Adım sayacı başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_SMS = 1
    }
}