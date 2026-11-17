package com.hydrosync.smsgateway

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var switchGatewayEnabled: SwitchMaterial
    private lateinit var switchIgnorePromo: SwitchMaterial
    private lateinit var tvPermissionStatus: TextView
    private lateinit var etWebhookUrl: TextInputEditText
    private lateinit var etSecret: TextInputEditText
    private lateinit var etKeywords: TextInputEditText
    private lateinit var tvLogs: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionStatus()
        syncForegroundService()
    }

    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchGatewayEnabled = findViewById(R.id.switchGatewayEnabled)
        switchIgnorePromo = findViewById(R.id.switchIgnorePromo)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        etWebhookUrl = findViewById(R.id.etWebhookUrl)
        etSecret = findViewById(R.id.etSecret)
        etKeywords = findViewById(R.id.etKeywords)
        tvLogs = findViewById(R.id.tvLogs)

        loadConfigToUi()
        refreshPermissionStatus()
        refreshLogs()
        syncForegroundService()

        switchGatewayEnabled.setOnCheckedChangeListener { _, isChecked ->
            GatewayConfig.setEnabled(this, isChecked)
            syncForegroundService()
            GatewayLogStore.append(this, if (isChecked) "Gateway ENABLED" else "Gateway PAUSED")
        }

        findViewById<MaterialButton>(R.id.btnGrantPermissions).setOnClickListener {
            requestRequiredPermissions()
        }

        findViewById<MaterialButton>(R.id.btnBatteryExempt).setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        findViewById<MaterialButton>(R.id.btnSaveConfig).setOnClickListener {
            saveConfigFromUi()
        }

        findViewById<MaterialButton>(R.id.btnTestWebhook).setOnClickListener {
            saveConfigFromUi(silent = true)
            Toast.makeText(this, "Testing webhook preview...", Toast.LENGTH_SHORT).show()
            WebhookDispatcher.dispatchIncomingSms(
                context = this,
                sender = "+94760000000",
                messageBody = "LEVEL",
                sendSms = false,
                isManualTest = true
            )
        }

        findViewById<MaterialButton>(R.id.btnClearLogs).setOnClickListener {
            GatewayLogStore.clear(this)
        }

        if (!hasSmsPermissions()) {
            requestRequiredPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshLogs()
        ContextCompat.registerReceiver(
            this,
            logUpdateReceiver,
            IntentFilter(GatewayLogStore.ACTION_LOGS_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logUpdateReceiver)
        } catch (_: Exception) {
        }
    }

    private fun loadConfigToUi() {
        switchGatewayEnabled.isChecked = GatewayConfig.isEnabled(this)
        switchIgnorePromo.isChecked = GatewayConfig.shouldIgnorePromoSenders(this)
        etWebhookUrl.setText(GatewayConfig.getWebhookUrl(this))
        etSecret.setText(GatewayConfig.getSecret(this))
        etKeywords.setText(GatewayConfig.getKeywords(this))
    }

    private fun saveConfigFromUi(silent: Boolean = false) {
        val url = etWebhookUrl.text?.toString()?.trim().orEmpty()
        val secret = etSecret.text?.toString()?.trim().orEmpty()
        val keywords = etKeywords.text?.toString()?.trim().orEmpty()

        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            Toast.makeText(this, "Enter a valid http/https Webhook URL", Toast.LENGTH_SHORT).show()
            return
        }

        GatewayConfig.setWebhookUrl(this, url)
        GatewayConfig.setSecret(this, secret)
        GatewayConfig.setKeywords(this, keywords)
        GatewayConfig.setIgnorePromoSenders(this, switchIgnorePromo.isChecked)

        if (!silent) {
            Toast.makeText(this, "Gateway settings saved", Toast.LENGTH_SHORT).show()
            GatewayLogStore.append(this, "Configuration saved -> $url")
        }
    }

    private fun hasSmsPermissions(): Boolean {
        val receiveGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        return receiveGranted && readGranted
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun refreshPermissionStatus() {
        if (hasSmsPermissions()) {
            tvPermissionStatus.text = "SMS Permission: GRANTED (Ready)"
            tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
        } else {
            tvPermissionStatus.text = "SMS Permission: REQUIRED — Tap 'Grant SMS Access'"
            tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
        }
    }

    private fun syncForegroundService() {
        if (GatewayConfig.isEnabled(this) && hasSmsPermissions()) {
            GatewayForegroundService.start(this)
        } else {
            GatewayForegroundService.stop(this)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already exempted from battery optimization!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refreshLogs() {
        tvLogs.text = GatewayLogStore.readAll(this)
    }
}
