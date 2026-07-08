package com.memreg.net.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class DatabaseHelper private constructor(private val appContext: Context) {

    private var db: SQLiteDatabase

    init {
        val dbFile = appContext.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()
        appContext.assets.open(DB_NAME).use { input ->
            FileOutputStream(dbFile).use { output ->
                input.copyTo(output)
            }
        }
        db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun replaceDatabase(newDbFile: File): Boolean {
        return try {
            db.close()
            val target = appContext.getDatabasePath(DB_NAME)
            newDbFile.copyTo(target, overwrite = true)
            db = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun search(city: String?, query: String, limit: Int = 500): List<Record> {
        val words = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val args = mutableListOf<String>()
        val conditions = mutableListOf<String>()

        if (city != null && city != "All") {
            conditions.add("city = ?")
            args.add(city)
        }

        val searchOrs = mutableListOf<String>()
        for (word in words) {
            val colOrs = SEARCH_COLS.joinToString(" OR ") { "$it LIKE ?" }
            searchOrs.add("($colOrs)")
            for (i in SEARCH_COLS.indices) {
                args.add("%$word%")
            }
        }
        conditions.add("(${searchOrs.joinToString(" AND ")})")

        val sql = """
            SELECT * FROM records 
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY city, file_no
            LIMIT ?
        """.trimIndent()
        args.add(limit.toString())

        return try {
            queryDb(sql, args.toTypedArray())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun queryDb(sql: String, args: Array<String>): List<Record> {
        val cursor = db.rawQuery(sql, args)
        val results = mutableListOf<Record>()
        try {
            while (cursor.moveToNext()) {
                results.add(readRecord(cursor))
            }
        } finally {
            cursor.close()
        }
        return results
    }

    private fun readRecord(c: Cursor) = Record(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        city = c.getString(c.getColumnIndexOrThrow("city")),
        section = c.getString(c.getColumnIndexOrThrow("section")),
        fileNo = c.getString(c.getColumnIndexOrThrow("file_no")),
        crn = c.getString(c.getColumnIndexOrThrow("crn")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        ntn = c.getString(c.getColumnIndexOrThrow("ntn")),
        cnic = c.getString(c.getColumnIndexOrThrow("cnic")),
        pin = c.getString(c.getColumnIndexOrThrow("pin")),
        password = c.getString(c.getColumnIndexOrThrow("password")),
        mail = c.getString(c.getColumnIndexOrThrow("mail")),
        pwdMail = c.getString(c.getColumnIndexOrThrow("pwd_mail"))
    )

    val recordCount: Int
        get() {
            val cursor = db.rawQuery("SELECT count(*) FROM records", null)
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            return count
        }

    companion object {
        private const val DB_NAME = "memreg.db"
        private val SEARCH_COLS = listOf("title", "ntn", "cnic", "file_no", "pin", "password", "mail", "pwd_mail")

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
