package tr.com.necatiefeaslan.saglikliyasam

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import tr.com.necatiefeaslan.saglikliyasam.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide() // ActionBar'ı gizle
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.registerButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val surname = binding.surnameEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val phone = binding.phoneEditText.text.toString().trim()
            val birthYear = binding.birthYearEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            // Tüm hata mesajlarını temizle
            binding.nameLayout.error = null
            binding.surnameLayout.error = null
            binding.emailLayout.error = null
            binding.phoneLayout.error = null
            binding.birthYearLayout.error = null
            binding.passwordLayout.error = null

            // Input validasyonu
            var hasError = false

            if (name.isEmpty()) {
                binding.nameLayout.error = "Ad alanı boş bırakılamaz"
                hasError = true
            }

            if (surname.isEmpty()) {
                binding.surnameLayout.error = "Soyad alanı boş bırakılamaz"
                hasError = true
            }

            if (email.isEmpty()) {
                binding.emailLayout.error = "E-posta alanı boş bırakılamaz"
                hasError = true
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailLayout.error = "Geçerli bir e-posta adresi giriniz"
                hasError = true
            }

            if (phone.isEmpty()) {
                binding.phoneLayout.error = "Telefon alanı boş bırakılamaz"
                hasError = true
            } else if (phone.length < 10) {
                binding.phoneLayout.error = "Geçerli bir telefon numarası giriniz"
                hasError = true
            }

            if (birthYear.isEmpty()) {
                binding.birthYearLayout.error = "Doğum tarihi boş bırakılamaz"
                hasError = true
            }

            if (password.isEmpty()) {
                binding.passwordLayout.error = "Şifre alanı boş bırakılamaz"
                hasError = true
            } else if (password.length < 6) {
                binding.passwordLayout.error = "Şifre en az 6 karakter olmalıdır"
                hasError = true
            }

            if (hasError) {
                return@setOnClickListener
            }

            // Kayıt işlemi
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Kullanıcı bilgilerini Firestore'a kaydet (Türkçe alan adları ve 'kullanicilar' koleksiyonu)
                        val kullanici = hashMapOf(
                            "userId" to auth.currentUser!!.uid,
                            "ad" to name,
                            "soyad" to surname,
                            "e_posta" to email,
                            "telefon" to phone,
                            "dogum_tarihi" to binding.birthYearEditText.text.toString()
                        )

                        db.collection("kullanicilar")
                            .document(auth.currentUser!!.uid)
                            .set(kullanici)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Kayıt başarılı! Giriş yapabilirsiniz.", Toast.LENGTH_SHORT).show()
                                // Login ekranına yönlendir
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // Hata mesajlarını kullanıcı dostu hale getir
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthUserCollisionException -> "Bu e-posta adresi zaten kullanımda"
                            is FirebaseAuthWeakPasswordException -> "Şifre çok zayıf"
                            is FirebaseAuthInvalidCredentialsException -> "Geçersiz e-posta formatı"
                            else -> "Kayıt başarısız: ${task.exception?.message}"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Doğum tarihi alanı için takvim ikonuna ve inputa tıklama
        binding.birthYearLayout.setEndIconOnClickListener {
            showDatePicker()
        }
        binding.birthYearEditText.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val datePicker = android.app.DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formatted = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
            binding.birthYearEditText.setText(formatted)
        }, year, month, day)
        datePicker.show()
    }
} 