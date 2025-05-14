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
import tr.com.necatiefeaslan.saglikliyasam.util.BatteryLevelReceiver
import android.os.Build

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
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

        // Su hatırlatıcı bildirimi için WorkManager başlat
        val workRequest = PeriodicWorkRequestBuilder<SuHatirlaticiWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "su_hatirlatici",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )

        // Runtime SMS izni iste
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1)
        }

        // BatteryLevelReceiver'ı kodda register et
        val batteryReceiver = BatteryLevelReceiver()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        // Adım servisi için izin kontrolü ve başlatma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION), 1001)
            } else {
                val intent = Intent(this, StepCounterService::class.java)
                ContextCompat.startForegroundService(this, intent)
            }
        } else {
            val intent = Intent(this, StepCounterService::class.java)
            ContextCompat.startForegroundService(this, intent)
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
}