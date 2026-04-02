package com.splitpay.util

import android.content.Context
import android.provider.ContactsContract

data class DeviceContact(
    val name: String,
    val phone: String
)

object ContactReader {

    fun read(context: Context): List<DeviceContact> {
        val contacts = mutableListOf<DeviceContact>()
        val seen = mutableSetOf<String>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        ) ?: return contacts

        cursor.use {
            val nameIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameIdx)  ?: continue
                val phone = it.getString(phoneIdx) ?: continue
                // One entry per contact name (first number found)
                if (seen.add(name)) {
                    contacts.add(DeviceContact(name = name, phone = phone))
                }
            }
        }
        return contacts
    }
}
