package tr.com.necatiefeaslan.saglikliyasam

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import tr.com.necatiefeaslan.saglikliyasam.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Giriş yapılıp yapılmadığını ilk açılışta kontrol et
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (currentUser != null && isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish() // LoginActivity'yi kapat
            return // Metodun geri kalanını çalıştırmayı durdur
        }

        supportActionBar?.hide() // ActionBar'ı gizle
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            // Tüm hata mesajlarını temizle
            binding.emailLayout.error = null
            binding.passwordLayout.error = null

            // Input validasyonu
            var hasError = false

            if (email.isEmpty()) {
                binding.emailLayout.error = "E-posta alanı boş bırakılamaz"
                hasError = true
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailLayout.error = "Geçerli bir e-posta adresi giriniz"
                hasError = true
            }

            if (password.isEmpty()) {
                binding.passwordLayout.error = "Şifre alanı boş bırakılamaz"
                hasError = true
            }

            if (hasError) {
                return@setOnClickListener
            }

            // Giriş işlemi
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Giriş başarılı, SharedPreferences'e kaydet
                        val sharedPreferences = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putBoolean("isLoggedIn", true)  // Oturum açma durumunu kaydet
                        editor.apply()

                        // MainActivity'ye yönlendir
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("RESTART_STEP_SERVICE", true) // Adım servisi yeniden başlatma sinyali
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Giriş başarısız: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}