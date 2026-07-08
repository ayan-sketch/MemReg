package com.memreg.net.data

data class Record(
    val id: Long,
    val city: String,
    val section: String,
    val fileNo: String?,
    val crn: String?,
    val title: String?,
    val ntn: String?,
    val cnic: String?,
    val pin: String?,
    val password: String?,
    val mail: String?,
    val pwdMail: String?
) {
    val displayFields: List<Pair<String, String>>
        get() = buildList {
            val letter = title?.trim()?.firstOrNull()?.uppercase()?.let { "$it - " } ?: ""
            fileNo?.let { add("File No" to "$letter$it") }
            title?.let { add("Title of the Case" to it) }
            ntn?.let { add("NTN" to it) }
            cnic?.let { add("CNIC No" to it) }
            pin?.let { add("Pin (IRIS)" to it) }
            password?.let { add("Password (IRIS)" to it) }
            mail?.let { add("Mail" to it) }
            pwdMail?.let { add("PWD (Mail)" to it) }
        }
}
