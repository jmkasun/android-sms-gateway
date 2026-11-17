package com.hydrosync.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsGatewayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }.trim()

        val pendingResult = goAsync()
        WebhookDispatcher.dispatchIncomingSms(
            context = context,
            sender = sender,
            messageBody = fullBody,
            sendSms = true,
            isManualTest = false
        ) {
            try {
                pendingResult.finish()
            } catch (_: Exception) {
            }
        }
    }
}
