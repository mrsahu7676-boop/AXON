package com.axon.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Settings screen — API key input, notification access guide, history clear.
 */
class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("axon_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "AXON Settings"
        }

        setupApiKeyField()
        setupNotificationAccess()
        setupClearHistory()
        setupVersionInfo()
    }

    private fun setupApiKeyField() {
        val etApiKey    = findViewById<EditText>(R.id.etApiKey)
        val btnSaveKey  = findViewById<Button>(R.id.btnSaveKey)
        val tvKeyStatus = findViewById<TextView>(R.id.tvKeyStatus)

        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        if (savedKey.isNotBlank()) {
            etApiKey.setText(savedKey)
            tvKeyStatus.text = "✓ Gemini API Key saved"
            tvKeyStatus.setTextColor(getColor(R.color.axon_primary))
        }

        btnSaveKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isBlank()) {
                tvKeyStatus.text = "⚠ API key cannot be empty"
                tvKeyStatus.setTextColor(getColor(R.color.axon_error))
                return@setOnClickListener
            }
            // Gemini keys start with AIza
            if (!key.startsWith("AIza")) {
                tvKeyStatus.text = "⚠ Invalid key format (should start with AIza)"
                tvKeyStatus.setTextColor(getColor(R.color.axon_error))
                return@setOnClickListener
            }
            prefs.edit().putString("gemini_api_key", key).apply()
            tvKeyStatus.text = "✓ Gemini API Key saved successfully!"
            tvKeyStatus.setTextColor(getColor(R.color.axon_primary))
            Toast.makeText(this, "Gemini API Key saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNotificationAccess() {
        val btnNotifAccess = findViewById<Button>(R.id.btnNotifAccess)
        val tvNotifStatus  = findViewById<TextView>(R.id.tvNotifStatus)

        val hasAccess = isNotificationAccessGranted()
        tvNotifStatus.text = if (hasAccess) "✓ Notification Access granted" else "✗ Not granted yet"
        tvNotifStatus.setTextColor(
            if (hasAccess) getColor(R.color.axon_primary) else getColor(R.color.axon_error)
        )

        btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun setupClearHistory() {
        val btnClear = findViewById<Button>(R.id.btnClearHistory)
        btnClear.setOnClickListener {
            // Signal MainActivity to clear via shared pref flag
            prefs.edit().putBoolean("clear_history_requested", true).apply()
            Toast.makeText(this, "Conversation history cleared!", Toast.LENGTH_SHORT).show()
            btnClear.text = "✓ Cleared"
        }
    }

    private fun setupVersionInfo() {
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = "AXON v1.0 · Gemini 3 Flash"
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(packageName)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        // Refresh notification status when returning from settings
        val tvNotifStatus = findViewById<TextView>(R.id.tvNotifStatus)
        val hasAccess = isNotificationAccessGranted()
        tvNotifStatus.text = if (hasAccess) "✓ Notification Access granted" else "✗ Not granted yet"
        tvNotifStatus.setTextColor(
            if (hasAccess) getColor(R.color.axon_primary) else getColor(R.color.axon_error)
        )
    }
}
