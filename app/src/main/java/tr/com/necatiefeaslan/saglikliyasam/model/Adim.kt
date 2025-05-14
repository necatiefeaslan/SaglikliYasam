package tr.com.necatiefeaslan.saglikliyasam.model

data class Adim(
    val id: String = "",
    val tarih: String = "", // yyyy-MM-dd
    val adimsayisi: Int = 0,
    val kullaniciId: String = "",
    val hedefadim: Int = 0
) 