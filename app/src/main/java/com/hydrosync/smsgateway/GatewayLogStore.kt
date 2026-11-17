package com.hydrosync.smsgateway

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GatewayLogStore {
    const val ACTION_LOGS_UPDATED = "com.hydrosync.smsgateway.LOGS_UPDATED"
    private const val PREFS_NAME = "hydrosync_sms_gateway_logs"
    private const val KEY_LOGS = "log_entries_json"
    private const val MAX_LOGS = 40

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun append(context: Context, line: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val timestamp = timeFormat.format(Date())
        val entry = "[$timestamp] $line"

        val updated = JSONArray()
        updated.put(entry)
        for (i in 0 until minOf(arr.length(), MAX_LOGS - 1)) {
            updated.put(arr.optString(i))
        }

        prefs.edit().putString(KEY_LOGS, updated.toString()).apply()
        context.sendBroadcast(Intent(ACTION_LOGS_UPDATED).setPackage(context.packageName))
    }

    @Synchronized
    fun readAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        if (arr.length() == 0) {
            return "Waiting for incoming SMS queries..."
        }
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            if (i > 0) sb.append("\n\n")
            sb.append(arr.optString(i))
        }
        return sb.toString()
    }

    @Synchronized
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOGS, "[]").apply()
        context.sendBroadcast(Intent(ACTION_LOGS_UPDATED).setPackage(context.packageName))
    }
}
