package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.ui.text.AnnotatedString

data class DeviceContact(
    val name: String,
    val phoneNumber: String
)

fun fetchDeviceContacts(context: Context): List<DeviceContact> {
    val contactsList = mutableListOf<DeviceContact>()
    try {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "Sem Nome" else "Sem Nome"
                val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                if (number.isNotBlank()) {
                    contactsList.add(DeviceContact(name, number))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    // De-duplicate based on formatted digits to ensure clean entries
    return contactsList.distinctBy { it.phoneNumber.replace(Regex("[^0-9+]"), "") }
}

fun formatPhoneNumberForWhatsApp(phone: String): String {
    val digitsOnly = phone.replace(Regex("[^0-9]"), "")
    if (phone.startsWith("+")) {
        return digitsOnly
    }
    if (digitsOnly.length == 10 || digitsOnly.length == 11) {
        return "55$digitsOnly" // Brazilian standard default if no country code present
    }
    if (digitsOnly.startsWith("55") && digitsOnly.length >= 12) {
        return digitsOnly
    }
    return digitsOnly
}

fun sendWhatsAppDirect(
    context: Context,
    formattedPhone: String,
    text: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    // Force clipboard save
    try {
        clipboardManager.setText(AnnotatedString(text))
    } catch (e: Exception) {
        // Fallback or ignore
    }

    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(text)}")
    val intent = Intent(Intent.ACTION_VIEW, uri)

    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    var opened = false
    for (pkg in packages) {
        try {
            val pm = context.packageManager
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            val directIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
            }
            context.startActivity(directIntent)
            opened = true
            break
        } catch (e: Exception) {
            // Package is not installed, iterate
        }
    }

    if (!opened) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir o WhatsApp. O link direto foi gerado e o texto copiado!", Toast.LENGTH_LONG).show()
        }
    }
}
