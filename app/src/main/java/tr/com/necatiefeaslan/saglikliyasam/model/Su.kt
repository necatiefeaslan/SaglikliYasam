package tr.com.necatiefeaslan.saglikliyasam.model

data class Su(
    val id: String = "",
    val tarih: String = "", // veya Timestamp, Firestore ile uyumlu olacak şekilde
    val miktar: Int = 0, // ml
    val kullaniciId: String = "",
    val hedefsu: Int = 0 // ml
) 