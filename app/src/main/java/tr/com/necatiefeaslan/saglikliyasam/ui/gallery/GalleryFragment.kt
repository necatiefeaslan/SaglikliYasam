package tr.com.necatiefeaslan.saglikliyasam.ui.gallery

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import tr.com.necatiefeaslan.saglikliyasam.MainActivity
import tr.com.necatiefeaslan.saglikliyasam.R
import tr.com.necatiefeaslan.saglikliyasam.databinding.FragmentGalleryBinding

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private var selectedImageUri: Uri? = null
    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.imageViewProfileGallery.setImageURI(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSelectPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.buttonSavePhoto.setOnClickListener {
            uploadImageToFirebase()
        }
    }

    private fun uploadImageToFirebase() {
        val imageUri = selectedImageUri ?: run {
            showToast("Lütfen önce bir fotoğraf seçin")
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            showToast("Kullanıcı girişi yapılmamış")
            return
        }

        binding.buttonSavePhoto.isEnabled = false
        binding.buttonSavePhoto.text = "Yükleniyor..."

        val storageRef = storage.reference
            .child("profile_images/$userId.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                getDownloadUrl(storageRef, userId)
            }
            .addOnFailureListener { e ->
                Log.e("GalleryFragment", "Upload failed", e)
                showToast("Yükleme hatası: ${e.localizedMessage}")
                binding.buttonSavePhoto.isEnabled = true
                binding.buttonSavePhoto.text = "Fotoğrafı Kaydet"
            }
    }

    private fun getDownloadUrl(storageRef: StorageReference, userId: String) {
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            updateUserProfile(userId, uri.toString())
        }.addOnFailureListener { e ->
            Log.e("GalleryFragment", "URL alınamadı", e)
            showToast("URL alınamadı: ${e.localizedMessage}")
            binding.buttonSavePhoto.isEnabled = true
            binding.buttonSavePhoto.text = "Fotoğrafı Kaydet"
        }
    }

    private fun updateUserProfile(userId: String, imageUrl: String) {
        val userData = hashMapOf(
            "profilUrl" to imageUrl,
        )

        db.collection("kullanicilar").document(userId)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                showToast("Profil fotoğrafı güncellendi!")
                binding.buttonSavePhoto.isEnabled = true
                binding.buttonSavePhoto.text = "Fotoğrafı Kaydet"
                
                // Navigation drawer'daki profil resmini güncelle
                updateNavigationHeaderProfileImage(imageUrl)
            }
            .addOnFailureListener { e ->
                Log.e("GalleryFragment", "Firestore error", e)
                showToast("Güncelleme hatası: ${e.localizedMessage}")
                binding.buttonSavePhoto.isEnabled = true
                binding.buttonSavePhoto.text = "Fotoğrafı Kaydet"
            }
    }
    
    private fun updateNavigationHeaderProfileImage(imageUrl: String) {
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            val navView = mainActivity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view)
            val headerView = navView.getHeaderView(0)
            val imageViewProfile = headerView.findViewById<ImageView>(R.id.imageViewProfile)
            
            Glide.with(mainActivity)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(imageViewProfile)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}