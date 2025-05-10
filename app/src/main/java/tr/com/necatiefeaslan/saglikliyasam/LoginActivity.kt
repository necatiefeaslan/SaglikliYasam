package tr.com.necatiefeaslan.saglikliyasam

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
        supportActionBar?.hide() // ActionBar'ı gizle
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

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
                        startActivity(Intent(this, MainActivity::class.java))
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