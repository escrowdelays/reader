package com.example.walletconnect.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object LocalBookStore {
    private const val PREFS_NAME = "local_books"
    private const val KEY_BOOKS = "books"

    data class LocalBook(
        val id: String,
        val name: String,
        val fileType: String,
        val addedAt: Long
    )

    fun addBook(context: Context, name: String, fileType: String): String {
        val id = "local_${System.currentTimeMillis()}"
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_BOOKS, "[]") ?: "[]"
            val arr = JSONArray(json)

            val book = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("fileType", fileType)
                put("addedAt", System.currentTimeMillis())
            }
            arr.put(book)
            prefs.edit().putString(KEY_BOOKS, arr.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error adding local book")
        }
        return id
    }

    fun getBooks(context: Context): List<LocalBook> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_BOOKS, "[]") ?: "[]"
            val arr = JSONArray(json)

            val result = mutableListOf<LocalBook>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    LocalBook(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        fileType = obj.getString("fileType"),
                        addedAt = obj.getLong("addedAt")
                    )
                )
            }
            result.sortedByDescending { it.addedAt }
        } catch (e: Exception) {
            Timber.e(e, "Error getting local books")
            emptyList()
        }
    }

    fun removeBook(context: Context, bookId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_BOOKS, "[]") ?: "[]"
            val arr = JSONArray(json)
            val newArr = JSONArray()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("id") != bookId) {
                    newArr.put(obj)
                }
            }
            prefs.edit().putString(KEY_BOOKS, newArr.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error removing local book")
        }
    }
}
