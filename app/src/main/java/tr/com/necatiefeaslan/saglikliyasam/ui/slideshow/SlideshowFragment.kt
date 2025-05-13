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
        binding.progressBarGunlukSu.max = gunlukHedef
        binding.progressBarGunlukSu.progress = toplam
        if (toplam >= gunlukHedef) {
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
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = calendar.time
        db.collection("su")
            .whereEqualTo("kullaniciId", userId)
            .get()
            .addOnSuccessListener { docs ->
                val gunlukMap = mutableMapOf<String, Pair<Int, Int>>() // tarih -> Pair(miktar, hedefsu)
                for (i in 0..6) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(monday.time + i * 24 * 60 * 60 * 1000))
                    gunlukMap[date] = Pair(0, 0)
                }
                for (doc in docs) {
                    val su = doc.toObject(Su::class.java)
                    if (gunlukMap.containsKey(su.tarih)) {
                        gunlukMap[su.tarih] = Pair(
                            gunlukMap[su.tarih]!!.first + su.miktar,
                            su.hedefsu
                        )
                    }
                }
                updateHaftalikOzetUI(gunlukMap, days)
            }
    }

    private fun updateHaftalikOzetUI(gunlukMap: Map<String, Pair<Int, Int>>, days: Array<String>) {
        binding.layoutHaftalikOzet.removeAllViews()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        for (i in 0..6) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(calendar.timeInMillis + i * 24 * 60 * 60 * 1000))
            val (miktar, hedef) = gunlukMap[date] ?: Pair(0, 0)
            val layout = LinearLayout(requireContext())
            layout.orientation = LinearLayout.VERTICAL
            layout.gravity = android.view.Gravity.CENTER
            val circle = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal)
            circle.max = if (hedef > 0) hedef else 1
            circle.progress = miktar
            circle.layoutParams = LinearLayout.LayoutParams(80, 80)
            val dayText = TextView(requireContext())
            dayText.text = days[i]
            dayText.setTextColor(resources.getColor(android.R.color.white, null))
            dayText.textAlignment = View.TEXT_ALIGNMENT_CENTER
            val miktarText = TextView(requireContext())
            miktarText.text = "$miktar/$hedef ml"
            miktarText.setTextColor(resources.getColor(android.R.color.white, null))
            miktarText.textAlignment = View.TEXT_ALIGNMENT_CENTER
            layout.addView(circle)
            layout.addView(dayText)
            layout.addView(miktarText)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(8, 0, 8, 0)
            layout.layoutParams = params
            binding.layoutHaftalikOzet.addView(layout)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}