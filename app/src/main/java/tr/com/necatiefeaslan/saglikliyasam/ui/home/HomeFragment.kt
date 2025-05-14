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
import tr.com.necatiefeaslan.saglikliyasam.R
import tr.com.necatiefeaslan.saglikliyasam.databinding.FragmentHomeBinding
import tr.com.necatiefeaslan.saglikliyasam.model.Adim
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(), SensorEventListener {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""
    private var gunlukHedef = 8000
    private var adimSayisi = 0
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var initialStepCount: Int? = null

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
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        getGunlukHedef()
        getGunlukToplam()
        getHaftalikOzet()
        binding.buttonAdimHedefGuncelle.setOnClickListener { showHedefGuncelleDialog() }
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            if (initialStepCount == null) {
                initialStepCount = event.values[0].toInt()
            }
            val stepsToday = event.values[0].toInt() - (initialStepCount ?: 0)
            adimSayisi = stepsToday
            updateGunlukAdimUI()
            kaydetAdimFirestore(stepsToday)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

    private fun getGunlukToplam() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        db.collection("adim").document(docId).get()
            .addOnSuccessListener { doc ->
                adimSayisi = if (doc.exists()) {
                    doc.toObject(Adim::class.java)?.adimsayisi ?: 0
                } else {
                    0
                }
                updateGunlukAdimUI()
            }
            .addOnFailureListener { updateGunlukAdimUI() }
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

    private fun kaydetAdimFirestore(adim: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docId = "${userId}_$today"
        val ref = db.collection("adim").document(docId)
        ref.get().addOnSuccessListener { doc ->
            val hedef = if (doc.exists()) doc.toObject(Adim::class.java)?.hedefadim ?: gunlukHedef else gunlukHedef
            val adimKayit = Adim(
                id = docId,
                tarih = today,
                adimsayisi = adim,
                kullaniciId = userId,
                hedefadim = hedef
            )
            ref.set(adimKayit)
        }
    }

    private fun getHaftalikOzet() {
        val calendar = Calendar.getInstance()
        val days = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        val userId = this.userId
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val monday = calendar.time
        db.collection("adim")
            .whereEqualTo("kullaniciId", userId)
            .get()
            .addOnSuccessListener { docs ->
                val gunlukMap = mutableMapOf<String, Pair<Int, Int>>() // tarih -> Pair(adimsayisi, hedefadim)
                for (i in 0..6) {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(monday.time + i * 24 * 60 * 60 * 1000))
                    gunlukMap[date] = Pair(0, 0)
                }
                for (doc in docs) {
                    val adim = doc.toObject(Adim::class.java)
                    if (gunlukMap.containsKey(adim.tarih)) {
                        gunlukMap[adim.tarih] = Pair(
                            gunlukMap[adim.tarih]!!.first + adim.adimsayisi,
                            adim.hedefadim
                        )
                    }
                }
                updateHaftalikOzetUI(gunlukMap, days)
            }
    }

    private fun updateHaftalikOzetUI(gunlukMap: Map<String, Pair<Int, Int>>, days: Array<String>) {
        binding.layoutHaftalikAdimOzet.removeAllViews()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        for (i in 0..6) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(calendar.timeInMillis + i * 24 * 60 * 60 * 1000))
            val (adim, hedef) = gunlukMap[date] ?: Pair(0, 0)
            val percent = if (hedef > 0) (adim * 100 / hedef).coerceAtMost(100) else 0

            val frame = android.widget.FrameLayout(requireContext())
            val circle = com.google.android.material.progressindicator.CircularProgressIndicator(requireContext())
            circle.indicatorSize = 130
            circle.trackThickness = 14
            circle.max = 100
            circle.progress = percent
            circle.setIndicatorColor(androidx.core.content.ContextCompat.getColor(requireContext(), tr.com.necatiefeaslan.saglikliyasam.R.color.teal_200))
            circle.trackColor = androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white)
            circle.layoutParams = android.widget.FrameLayout.LayoutParams(130, 120, android.view.Gravity.CENTER)

            val percentText = android.widget.TextView(requireContext())
            percentText.text = "%$percent"
            percentText.setTextColor(android.graphics.Color.BLACK)
            percentText.textSize = 13f
            percentText.setTypeface(null, android.graphics.Typeface.BOLD)
            percentText.gravity = android.view.Gravity.CENTER
            percentText.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.view.Gravity.CENTER
            )

            frame.addView(circle)
            frame.addView(percentText)

            val layout = android.widget.LinearLayout(requireContext())
            layout.orientation = android.widget.LinearLayout.VERTICAL
            layout.gravity = android.view.Gravity.CENTER
            layout.setVerticalGravity(android.view.Gravity.TOP)
            layout.addView(frame)

            val dayText = android.widget.TextView(requireContext())
            dayText.text = days[i]
            dayText.setTextColor(resources.getColor(android.R.color.white, null))
            dayText.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            dayText.setPadding(0, 12, 0, 0)
            layout.addView(dayText)

            val params = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(8, 0, 8, 0)
            layout.layoutParams = params
            binding.layoutHaftalikAdimOzet.addView(layout)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}