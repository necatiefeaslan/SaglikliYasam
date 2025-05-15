package tr.com.necatiefeaslan.saglikliyasam.ui.slideshow

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import tr.com.necatiefeaslan.saglikliyasam.R
import tr.com.necatiefeaslan.saglikliyasam.databinding.FragmentSlideshowBinding
import tr.com.necatiefeaslan.saglikliyasam.model.Su
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.core.content.ContextCompat
import android.text.TextUtils
import android.util.Log

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""
    private var gunlukHedef = 2000 // Varsayılan hedef (ml)
    private var seciliBardakMiktari = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getGunlukHedef()
        getGunlukToplam()
        getHaftalikOzet()

        binding.buttonSuEkle.setOnClickListener { showSuEkleDialog() }
        binding.buttonHedefGuncelle.setOnClickListener { showHedefGuncelleDialog() }

        // Seçili bardakla ekle butonu bardak türü kartlarının altına
        val bardakBtnPlaceholder = binding.root.findViewById<FrameLayout>(R.id.placeholderBardakBtn)
        val seciliBardakBtn = com.google.android.material.button.MaterialButton(requireContext())
        seciliBardakBtn.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        seciliBardakBtn.text = "Seçili bardakla ekle (${seciliBardakMiktari} ml)"
        seciliBardakBtn.setOnClickListener {
            ekleSu(seciliBardakMiktari)
        }
        bardakBtnPlaceholder.removeAllViews()
        bardakBtnPlaceholder.addView(seciliBardakBtn)

        // Bardak türü kartlarına tıklama
        val bardaklar = listOf(
            Pair(binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBardak100), 100),
            Pair(binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBardak200), 200),
            Pair(binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBardak300), 300),
            Pair(binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBardak500), 500)
        )
        bardaklar.forEach { (card, miktar) ->
            card.setOnClickListener {
                seciliBardakMiktari = miktar
                // Vurgulama
                bardaklar.forEach { (c, _) -> c.strokeWidth = 0 }
                card.strokeWidth = 8
                card.strokeColor = resources.getColor(R.color.purple_500, null)
                seciliBardakBtn.text = "Seçili bardakla ekle (${seciliBardakMiktari} ml)"
            }
        }
        // Varsayılan vurgulama
        bardaklar[0].first.strokeWidth = 8
        bardaklar[0].first.strokeColor = resources.getColor(R.color.purple_500, null)
    }

    private fun getGunlukHedef() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        db.collection("su").document(docId).get()
            .addOnSuccessListener { doc ->
                gunlukHedef = if (doc.exists()) {
                    doc.toObject(Su::class.java)?.hedefsu ?: 0
                } else {
                    0
                }
                updateGunlukHedefUI()
            }
            .addOnFailureListener { updateGunlukHedefUI() }
    }

    private fun getGunlukToplam() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("su")
            .whereEqualTo("kullaniciId", userId)
            .whereEqualTo("tarih", today)
            .get()
            .addOnSuccessListener { docs ->
                var toplam = 0
                for (doc in docs) {
                    val su = doc.toObject(Su::class.java)
                    toplam += su.miktar
                }
                updateGunlukHedefUI(toplam)
            }
            .addOnFailureListener { updateGunlukHedefUI(0) }
    }

    private fun updateGunlukHedefUI(toplam: Int = 0) {
        binding.textViewGunlukHedef.text = "$toplam / $gunlukHedef ml"
        binding.progressBarGunlukSu.max = if (gunlukHedef > 0) gunlukHedef else 1
        binding.progressBarGunlukSu.progress = toplam
        if (gunlukHedef > 0 && toplam >= gunlukHedef) {
            Toast.makeText(requireContext(), "Tebrikler bugünkü su hedefinizi tamamladınız!", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSuEkleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_su_ekle, null)
        val input = dialogView.findViewById<EditText>(R.id.editTextSuMiktar)
        AlertDialog.Builder(requireContext())
            .setTitle("Su Ekle")
            .setView(dialogView)
            .setPositiveButton("Ekle") { _, _ ->
                val miktar = input.text.toString().toIntOrNull() ?: 0
                if (miktar > 0) ekleSu(miktar)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun ekleSu(miktar: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        val ref = db.collection("su").document(docId)
        ref.get().addOnSuccessListener { doc ->
            val eskiMiktar = if (doc.exists()) doc.toObject(Su::class.java)?.miktar ?: 0 else 0
            val su = Su(
                id = docId,
                tarih = today,
                miktar = eskiMiktar + miktar,
                kullaniciId = userId,
                hedefsu = gunlukHedef
            )
            ref.set(su)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Su eklendi", Toast.LENGTH_SHORT).show()
                    getGunlukToplam()
                    getHaftalikOzet()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Hata oluştu", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showHedefGuncelleDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Yeni hedef (ml)"
        input.setPadding(32)
        AlertDialog.Builder(requireContext())
            .setTitle("Günlük Hedefi Güncelle")
            .setView(input)
            .setPositiveButton("Güncelle") { _, _ ->
                val yeniHedef = input.text.toString().toIntOrNull()
                if (yeniHedef != null && yeniHedef > 0) {
                    gunlukHedef = yeniHedef
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val docId = "${userId}_$today"
                    val ref = db.collection("su").document(docId)
                    ref.get().addOnSuccessListener { doc ->
                        val eskiMiktar = if (doc.exists()) doc.toObject(Su::class.java)?.miktar ?: 0 else 0
                        val su = Su(
                            id = docId,
                            tarih = today,
                            miktar = eskiMiktar,
                            kullaniciId = userId,
                            hedefsu = gunlukHedef
                        )
                        ref.set(su)
                            .addOnSuccessListener {
                                getGunlukToplam()
                                getHaftalikOzet()
                                Toast.makeText(requireContext(), "Hedef güncellendi", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun getHaftalikOzet() {
        val calendar = Calendar.getInstance()
        val days = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        val userId = this.userId
        
        // Mevcut haftanın başını (Pazartesi) bul
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = calendar.time
        
        // Haftalık verileri almak için tarih aralığı oluştur
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = dateFormat.format(monday)
        
        // Pazar günü için tarihi hesapla
        val sundayCalendar = Calendar.getInstance()
        sundayCalendar.firstDayOfWeek = Calendar.MONDAY
        sundayCalendar.time = monday
        sundayCalendar.add(Calendar.DAY_OF_WEEK, 6) // Pazar (haftanın son günü)
        val endDate = dateFormat.format(sundayCalendar.time)
        
        // Veri günlüğü - hata ayıklama için
        Log.d("SlideshowFragment", "Tarih aralığı: $startDate - $endDate")
        
        // Hafta için boş veri haritası oluştur
        val gunlukMap = mutableMapOf<String, Pair<Int, Int>>()
        calendar.time = monday // Pazartesiye dön
        
        // Önce tüm haftayı sıfır değerleriyle doldur
        for (i in 0..6) {
            val currentDate = dateFormat.format(calendar.time)
            gunlukMap[currentDate] = Pair(0, 2000) // Varsayılan
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Firestore'dan haftalık su verilerini al
        db.collection("su")
            .whereEqualTo("kullaniciId", userId)
            .get()
            .addOnSuccessListener { docs ->
                // Tüm verileri işle ve sadece bu haftaya ait olanları filtrele
                for (doc in docs) {
                    val su = doc.toObject(Su::class.java)
                    val suTarih = su.tarih
                    
                    // Haftaya ait mi kontrol et
                    if (suTarih >= startDate && suTarih <= endDate) {
                        // Bu tarih için mevcut değerleri al
                        val eskiDeger = gunlukMap[suTarih] ?: Pair(0, 2000)
                        // Yeni değerleri kaydet
                        gunlukMap[suTarih] = Pair(su.miktar, su.hedefsu)
                        
                        // Log ile kontrol et
                        Log.d("SlideshowFragment", "Su verisi: Tarih=$suTarih, Miktar=${su.miktar}, Hedef=${su.hedefsu}")
                    }
                }
                
                // Günlük verileri göster
                for ((tarih, deger) in gunlukMap) {
                    Log.d("SlideshowFragment", "Grafik verisi: $tarih -> Miktar=${deger.first}, Hedef=${deger.second}")
                }
                
                updateHaftalikOzetUI(gunlukMap, days)
            }
            .addOnFailureListener { e ->
                Log.e("SlideshowFragment", "Veri çekme hatası", e)
                updateHaftalikOzetUI(gunlukMap, days)
            }
    }

    private fun updateHaftalikOzetUI(gunlukMap: Map<String, Pair<Int, Int>>, days: Array<String>) {
        binding.layoutHaftalikOzet.removeAllViews()
        
        // Haftanın her günü için görsel bileşenleri oluştur
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (i in 0..6) {
            val currentDate = Date(calendar.timeInMillis)
            val dateStr = dateFormat.format(currentDate)
            val (miktar, hedef) = gunlukMap[dateStr] ?: Pair(0, 2000)
            
            // Yüzde hesapla (minimum 0, maksimum 100)
            val percent = if (hedef > 0) {
                ((miktar.toFloat() / hedef.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else 0

            // Frame layout oluştur
            val frame = FrameLayout(requireContext())
            
            // Dairesel ilerleme göstergesi
            val circle = CircularProgressIndicator(requireContext())
            circle.indicatorSize = 100 // Daha küçük boyut
            circle.trackThickness = 10 // Daha ince çizgi
            circle.max = 100
            circle.progress = percent
            circle.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.white))
            circle.trackColor = ContextCompat.getColor(requireContext(), R.color.white_transparent_50)
            
            val circleParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            circleParams.gravity = Gravity.CENTER
            circle.layoutParams = circleParams
            
            // Yüzde metni
            val percentText = TextView(requireContext())
            percentText.text = "%$percent"
            percentText.setTextColor(Color.WHITE)
            percentText.textSize = 12f
            percentText.typeface = Typeface.DEFAULT_BOLD
            
            val textParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            textParams.gravity = Gravity.CENTER
            percentText.layoutParams = textParams
            
            frame.addView(circle)
            frame.addView(percentText)
            
            // Gün adını içeren dikey layout
            val layout = LinearLayout(requireContext())
            layout.orientation = LinearLayout.VERTICAL
            layout.gravity = Gravity.CENTER
            
            layout.addView(frame)
            
            // Gün metni
            val dayText = TextView(requireContext())
            dayText.text = days[i]
            dayText.setTextColor(Color.WHITE)
            dayText.textAlignment = View.TEXT_ALIGNMENT_CENTER
            dayText.setPadding(0, 10, 0, 0)
            
            val dayParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayText.layoutParams = dayParams
            
            layout.addView(dayText)
            
            // Ana layout'a ekle
            val columnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            columnParams.setMargins(4, 0, 4, 0)
            layout.layoutParams = columnParams
            
            binding.layoutHaftalikOzet.addView(layout)
            
            // Sonraki güne geç
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}