package com.hydrosync.smsgateway

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object WebhookDispatcher {
    private val executor = Executors.newSingleThreadExecutor()
    private val recentMessages = ConcurrentHashMap<String, Long>()

    fun dispatchIncomingSms(
        context: Context,
        sender: String,
        messageBody: String,
        sendSms: Boolean = true,
        isManualTest: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext

        if (!isManualTest && !GatewayConfig.isEnabled(appContext)) {
            GatewayLogStore.append(appContext, "IGNORED (Service OFF) from $sender")
            onComplete?.invoke()
            return
        }

        val cleanSender = sender.trim()
        if (!isManualTest && GatewayConfig.shouldIgnorePromoSenders(appContext)) {
            val hasLetters = cleanSender.any { it.isLetter() }
            val digitCount = cleanSender.count { it.isDigit() }
            if (hasLetters || digitCount < 7) {
                GatewayLogStore.append(appContext, "SKIPPED promo/short sender '$cleanSender'")
                onComplete?.invoke()
                return
            }
        }

        // Deduplicate static + dynamic broadcast receivers firing for the same SMS
        if (!isManualTest) {
            val dedupKey = "${cleanSender}::${messageBody.trim()}"
            val now = System.currentTimeMillis()
            val prev = recentMessages[dedupKey]
            if (prev != null && (now - prev) < 6000L) {
                onComplete?.invoke()
                return
            }
            recentMessages[dedupKey] = now
        }

        // Local keyword filter check if configured
        val keywordsRaw = GatewayConfig.getKeywords(appContext)
        if (!isManualTest && keywordsRaw.isNotEmpty()) {
            val keywords = keywordsRaw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val bodyLower = messageBody.lowercase()
            if (keywords.isNotEmpty() && keywords.none { bodyLower.contains(it) }) {
                GatewayLogStore.append(appContext, "SKIPPED '$cleanSender' (no keyword match: \"$messageBody\")")
                onComplete?.invoke()
                return
            }
        }

        val webhookUrl = GatewayConfig.getWebhookUrl(appContext)
        val secret = GatewayConfig.getSecret(appContext)

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(webhookUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    if (secret.isNotEmpty()) {
                        setRequestProperty("X-Gateway-Secret", secret)
                    }
                    connectTimeout = 12000
                    readTimeout = 20000
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("phone", cleanSender)
                    put("message", messageBody)
                    if (secret.isNotEmpty()) {
                        put("secret", secret)
                    }
                    if (!sendSms) {
                        put("send_sms", false)
                    }
                    if (isManualTest) {
                        put("bypass_cooldown", true)
                    }
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    ""
                }

                val jsonRes = try {
                    JSONObject(responseText)
                } catch (_: Exception) {
                    null
                }

                if (code in 200..299 && jsonRes != null && jsonRes.optBoolean("success", false)) {
                    val skipped = jsonRes.optBoolean("skipped", false)
                    val smsSent = jsonRes.optBoolean("sms_sent", false)
                    val replyPreview = jsonRes.optString("reply_message", "").replace("\n", " | ")
                    val statusMsg = when {
                        skipped -> "THROTTLED/SKIPPED: ${jsonRes.optString("reason")}"
                        smsSent -> "SMS SENT to ${jsonRes.optString("requester", cleanSender)} -> $replyPreview"
                        else -> "PREVIEW OK -> $replyPreview"
                    }
                    GatewayLogStore.append(appContext, "From $cleanSender: $statusMsg")
                } else {
                    val errMsg = jsonRes?.optString("error")?.ifEmpty { responseText } ?: "HTTP $code: $responseText"
                    GatewayLogStore.append(appContext, "ERROR ($code) for $cleanSender: $errMsg")
                }
            } catch (e: Exception) {
                GatewayLogStore.append(appContext, "NETWORK ERROR for $cleanSender: ${e.message}")
            } finally {
                conn?.disconnect()
                onComplete?.invoke()
            }
        }
    }
}
