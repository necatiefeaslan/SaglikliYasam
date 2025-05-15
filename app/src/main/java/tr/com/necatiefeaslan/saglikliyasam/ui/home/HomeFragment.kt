package tr.com.necatiefeaslan.saglikliyasam.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import tr.com.necatiefeaslan.saglikliyasam.R
import tr.com.necatiefeaslan.saglikliyasam.databinding.FragmentHomeBinding
import tr.com.necatiefeaslan.saglikliyasam.model.Adim
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""
    private var gunlukHedef = 8000
    private var adimSayisi = 0
    private var adimListener: ListenerRegistration? = null
    private var haftalikListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getGunlukHedef()
        listenGunlukAdim()
        listenHaftalikOzet()
        binding.buttonAdimHedefGuncelle.setOnClickListener { showHedefGuncelleDialog() }
    }

    private fun getGunlukHedef() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        db.collection("adim").document(docId).get()
            .addOnSuccessListener { doc ->
                gunlukHedef = if (doc.exists()) {
                    doc.toObject(Adim::class.java)?.hedefadim ?: 0
                } else {
                    0
                }
                updateGunlukAdimUI()
            }
            .addOnFailureListener { updateGunlukAdimUI() }
    }

    private fun listenGunlukAdim() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        adimListener = db.collection("adim").document(docId)
            .addSnapshotListener { doc, _ ->
                adimSayisi = if (doc != null && doc.exists()) {
                    doc.toObject(Adim::class.java)?.adimsayisi ?: 0
                } else {
                    0
                }
                updateGunlukAdimUI()
            }
    }

    private fun listenHaftalikOzet() {
        haftalikListener?.remove()
        haftalikListener = db.collection("adim")
            .whereEqualTo("kullaniciId", userId)
            .addSnapshotListener { _, _ ->
                getHaftalikOzet()
            }
    }

    private fun updateGunlukAdimUI() {
        binding.textViewGunlukAdim.text = "$adimSayisi / $gunlukHedef adım"
        binding.progressBarGunlukAdim.max = gunlukHedef
        binding.progressBarGunlukAdim.progress = adimSayisi
    }

    private fun showHedefGuncelleDialog() {
        val input = android.widget.EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Yeni hedef (adım)"
        input.setPadding(32, 32, 32, 32)
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Günlük Hedefi Güncelle")
            .setView(input)
            .setPositiveButton("Güncelle") { _, _ ->
                val yeniHedef = input.text.toString().toIntOrNull()
                if (yeniHedef != null && yeniHedef > 0) {
                    gunlukHedef = yeniHedef
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val docId = "${userId}_$today"
                    val ref = db.collection("adim").document(docId)
                    ref.get().addOnSuccessListener { doc ->
                        val eskiAdim = if (doc.exists()) doc.toObject(Adim::class.java)?.adimsayisi ?: 0 else 0
                        val adim = Adim(
                            id = docId,
                            tarih = today,
                            adimsayisi = eskiAdim,
                            kullaniciId = userId,
                            hedefadim = gunlukHedef
                        )
                        ref.set(adim)
                            .addOnSuccessListener {
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
        
        calendar.add(Calendar.DAY_OF_WEEK, 6) // Pazar (haftanın son günü)
        val endDate = dateFormat.format(calendar.time)
        
        // Firestore'dan haftalık adım verilerini al
        db.collection("adim")
            .whereEqualTo("kullaniciId", userId)
            .whereGreaterThanOrEqualTo("tarih", startDate)
            .whereLessThanOrEqualTo("tarih", endDate)
            .get()
            .addOnSuccessListener { docs ->
                // Haftanın her günü için tarih -> (adım sayısı, hedef) eşleşmesi oluştur
                val gunlukMap = mutableMapOf<String, Pair<Int, Int>>()
                
                // Önce tüm haftayı sıfır değerleriyle doldur
                calendar.time = monday // Tekrar pazartesiye dön
                for (i in 0..6) {
                    val currentDate = Date(calendar.timeInMillis)
                    val dateStr = dateFormat.format(currentDate)
                    gunlukMap[dateStr] = Pair(0, 8000) // Varsayılan hedef 8000
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                
                // Firestore'dan gelen verileri ekle
                for (doc in docs) {
                    val adim = doc.toObject(Adim::class.java)
                    if (adim.tarih.isNotEmpty()) {
                        // Eğer bu tarih için veri varsa, onu güncelle
                        gunlukMap[adim.tarih] = Pair(adim.adimsayisi, adim.hedefadim)
                    }
                }
                
                updateHaftalikOzetUI(gunlukMap, days)
            }
            .addOnFailureListener {
                // Başarısız olursa boş harita ile devam et
                val gunlukMap = mutableMapOf<String, Pair<Int, Int>>()
                calendar.time = monday
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                
                for (i in 0..6) {
                    val currentDate = Date(calendar.timeInMillis)
                    val dateStr = dateFormat.format(currentDate)
                    gunlukMap[dateStr] = Pair(0, 8000)
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                
                updateHaftalikOzetUI(gunlukMap, days)
            }
    }

    private fun updateHaftalikOzetUI(gunlukMap: Map<String, Pair<Int, Int>>, days: Array<String>) {
        binding.layoutHaftalikAdimOzet.removeAllViews()
        
        // Haftanın her günü için görsel bileşenleri oluştur
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (i in 0..6) {
            val currentDate = Date(calendar.timeInMillis)
            val dateStr = dateFormat.format(currentDate)
            val (adim, hedef) = gunlukMap[dateStr] ?: Pair(0, 8000)
            
            // Yüzde hesapla (minimum 0, maksimum 100)
            val percent = if (hedef > 0) {
                ((adim.toFloat() / hedef.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else 0

            // Frame layout oluştur
            val frame = android.widget.FrameLayout(requireContext())
            
            // Dairesel ilerleme göstergesi
            val circle = com.google.android.material.progressindicator.CircularProgressIndicator(requireContext())
            circle.indicatorSize = 100 // Daha küçük boyut
            circle.trackThickness = 10 // Daha ince çizgi
            circle.max = 100
            circle.progress = percent
            circle.setIndicatorColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white))
            circle.trackColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white_transparent_50)
            
            val circleParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            circleParams.gravity = android.view.Gravity.CENTER
            circle.layoutParams = circleParams
            
            // Yüzde metni
            val percentText = android.widget.TextView(requireContext())
            percentText.text = "%$percent"
            percentText.setTextColor(android.graphics.Color.WHITE)
            percentText.textSize = 12f
            percentText.setTypeface(null, android.graphics.Typeface.BOLD)
            
            val textParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            textParams.gravity = android.view.Gravity.CENTER
            percentText.layoutParams = textParams
            
            frame.addView(circle)
            frame.addView(percentText)
            
            // Gün adını içeren dikey layout
            val layout = android.widget.LinearLayout(requireContext())
            layout.orientation = android.widget.LinearLayout.VERTICAL
            layout.gravity = android.view.Gravity.CENTER
            
            layout.addView(frame)
            
            // Gün metni
            val dayText = android.widget.TextView(requireContext())
            dayText.text = days[i]
            dayText.setTextColor(android.graphics.Color.WHITE)
            dayText.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            dayText.setPadding(0, 10, 0, 0)
            
            val dayParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dayText.layoutParams = dayParams
            
            layout.addView(dayText)
            
            // Ana layout'a ekle
            val columnParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            columnParams.setMargins(4, 0, 4, 0)
            layout.layoutParams = columnParams
            
            binding.layoutHaftalikAdimOzet.addView(layout)
            
            // Sonraki güne geç
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adimListener?.remove()
        haftalikListener?.remove()
        _binding = null
    }
}