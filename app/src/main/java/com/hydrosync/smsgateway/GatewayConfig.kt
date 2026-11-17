package com.hydrosync.smsgateway

import android.content.Context
import android.content.SharedPreferences

object GatewayConfig {
    private const val PREFS_NAME = "hydrosync_sms_gateway_prefs"
    private const val KEY_ENABLED = "gateway_enabled"
    private const val KEY_WEBHOOK_URL = "webhook_url"
    private const val KEY_SECRET = "webhook_secret"
    private const val KEY_KEYWORDS = "trigger_keywords"
    private const val KEY_IGNORE_PROMO = "ignore_alphanumeric_senders"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getWebhookUrl(context: Context): String {
        val defaultUrl = context.getString(R.string.default_webhook_url)
        val saved = prefs(context).getString(KEY_WEBHOOK_URL, defaultUrl) ?: defaultUrl
        return saved.trim().ifEmpty { defaultUrl }
    }

    fun setWebhookUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_WEBHOOK_URL, url.trim()).apply()
    }

    fun getSecret(context: Context): String {
        val defaultSecret = context.getString(R.string.default_secret)
        return (prefs(context).getString(KEY_SECRET, defaultSecret) ?: defaultSecret).trim()
    }

    fun setSecret(context: Context, secret: String) {
        prefs(context).edit().putString(KEY_SECRET, secret.trim()).apply()
    }

    fun getKeywords(context: Context): String {
        val defaultKeywords = context.getString(R.string.default_keywords)
        return (prefs(context).getString(KEY_KEYWORDS, defaultKeywords) ?: defaultKeywords).trim()
    }

    fun setKeywords(context: Context, keywords: String) {
        prefs(context).edit().putString(KEY_KEYWORDS, keywords.trim()).apply()
    }

    fun shouldIgnorePromoSenders(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_IGNORE_PROMO, true)
    }

    fun setIgnorePromoSenders(context: Context, ignore: Boolean) {
        prefs(context).edit().putBoolean(KEY_IGNORE_PROMO, ignore).apply()
    }
}
