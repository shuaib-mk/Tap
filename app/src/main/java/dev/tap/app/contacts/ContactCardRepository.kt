package dev.tap.app.contacts

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Persists the user's personal digital contact card.
 */
class ContactCardRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getContactCard(): ContactCard {
        val jsonStr = prefs.getString(KEY_CARD_JSON, null) ?: return ContactCard.createDefault()
        return try {
            val json = JSONObject(jsonStr)
            val socialMap = mutableMapOf<String, String>()
            if (json.has("socialLinks")) {
                val socialJson = json.getJSONObject("socialLinks")
                socialJson.keys().forEach { k ->
                    socialMap[k] = socialJson.optString(k, "")
                }
            }

            val selectedSet = mutableSetOf<String>()
            if (json.has("selectedFields")) {
                val arr = json.getJSONArray("selectedFields")
                for (i in 0 until arr.length()) {
                    selectedSet.add(arr.getString(i))
                }
            } else {
                selectedSet.addAll(listOf(
                    ContactCard.FIELD_DISPLAY_NAME,
                    ContactCard.FIELD_JOB_TITLE,
                    ContactCard.FIELD_COMPANY,
                    ContactCard.FIELD_PHONE,
                    ContactCard.FIELD_EMAIL,
                    ContactCard.FIELD_WEBSITE,
                    ContactCard.FIELD_NOTES,
                    ContactCard.FIELD_SOCIAL
                ))
            }

            ContactCard(
                id = json.optString("id", ""),
                displayName = json.optString("displayName", ""),
                jobTitle = json.optString("jobTitle", ""),
                company = json.optString("company", ""),
                phone = json.optString("phone", ""),
                email = json.optString("email", ""),
                website = json.optString("website", ""),
                notes = json.optString("notes", ""),
                socialLinks = socialMap,
                selectedFields = selectedSet
            )
        } catch (_: Exception) {
            ContactCard.createDefault()
        }
    }

    fun saveContactCard(card: ContactCard) {
        val json = JSONObject()
        json.put("id", card.id)
        json.put("displayName", card.displayName)
        json.put("jobTitle", card.jobTitle)
        json.put("company", card.company)
        json.put("phone", card.phone)
        json.put("email", card.email)
        json.put("website", card.website)
        json.put("notes", card.notes)

        val socialJson = JSONObject()
        card.socialLinks.forEach { (k, v) -> socialJson.put(k, v) }
        json.put("socialLinks", socialJson)

        val selectedArr = org.json.JSONArray()
        card.selectedFields.forEach { selectedArr.put(it) }
        json.put("selectedFields", selectedArr)

        prefs.edit().putString(KEY_CARD_JSON, json.toString()).apply()
    }

    fun resetToDefault(): ContactCard {
        val def = ContactCard.createDefault()
        saveContactCard(def)
        return def
    }

    companion object {
        private const val PREFS_NAME = "tap_contact_card_prefs"
        private const val KEY_CARD_JSON = "key_contact_card_json"

        @Volatile
        private var instance: ContactCardRepository? = null

        fun getInstance(context: Context): ContactCardRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactCardRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
