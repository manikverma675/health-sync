package com.healthsync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var healthManager: HealthConnectManager
    private lateinit var statusText: TextView

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        lifecycleScope.launch {
            if (healthManager.hasPermissions()) {
                updateStatus("Health Connect connected.")
            } else if (granted.isNotEmpty()) {
                updateStatus(
                    "Health Connect partially connected. Some health fields may show blank."
                )
            } else {
                updateStatus("Health Connect permissions are still off. Opening Health Connect settings...")
                openHealthConnectPermissions()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        healthManager = HealthConnectManager(this)
        statusText = findViewById(R.id.statusText)
        findViewById<ViewGroup>(R.id.contentStack).scheduleLayoutAnimation()
        findViewById<View>(R.id.contentRoot).animate()
            .alpha(1f)
            .setDuration(260)
            .start()

        refreshStatusDisplay()

        findViewById<Button>(R.id.btnConnectHealth).setOnClickListener {
            lifecycleScope.launch {
                when (healthManager.availability()) {
                    HealthConnectManager.Availability.AVAILABLE -> {
                        try {
                            if (!healthManager.hasPermissions()) {
                                healthPermissionLauncher.launch(healthManager.permissions)
                            } else {
                                updateStatus("Health Connect already connected.")
                            }
                        } catch (e: Exception) {
                            updateStatus("Health Connect failed: ${e.message ?: e.javaClass.simpleName}")
                        }
                    }
                    HealthConnectManager.Availability.INSTALL_OR_UPDATE_REQUIRED -> {
                        openHealthConnectInstall()
                    }
                    HealthConnectManager.Availability.UNAVAILABLE -> {
                        updateStatus(
                            "Health Connect is not available on this phone. " +
                            "Update Android and Google Play services, then try again."
                        )
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnConnectDrive).setOnClickListener {
            try {
                updateStatus("Choose Google Drive and save as health_data.json.")
                @Suppress("DEPRECATION")
                startActivityForResult(createDriveFileIntent(), RC_DRIVE_FILE)
            } catch (e: ActivityNotFoundException) {
                updateStatus("No file picker found. Install Google Drive and try again.")
            }
        }

        findViewById<Button>(R.id.btnSyncNow).setOnClickListener {
            lifecycleScope.launch {
                if (!healthManager.hasPermissions()) {
                    updateStatus("Step 1: Connect Health Connect first. Opening Health Connect settings...")
                    openHealthConnectPermissions()
                    return@launch
                }
                if (!DriveClient.hasFile(this@MainActivity)) {
                    updateStatus("Step 2: Connect Google Drive first.")
                    return@launch
                }
                updateStatus("Syncing to Google Drive...")
                try {
                    val snapshot = healthManager.readTodaySnapshot()
                    withContext(Dispatchers.IO) {
                        DriveClient.syncSnapshot(applicationContext, snapshot)
                    }
                    val rawRecordCount = snapshot.rawRecords.values.sumOf { it.size }
                    val rawTypeCount = snapshot.rawRecords.count { it.value.isNotEmpty() }
                    updateStatus(
                        "Synced to Drive!\n" +
                        "Steps: ${snapshot.steps ?: "--"}\n" +
                        "HR: ${snapshot.heartRateAvg ?: "--"} bpm\n" +
                        "Calories: ${snapshot.caloriesTotal ?: "--"} kcal\n" +
                        "Sleep: ${snapshot.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "--"}\n" +
                        "Raw records: $rawRecordCount across $rawTypeCount types"
                    )
                } catch (e: Exception) {
                    updateStatus("Sync failed: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            lifecycleScope.launch {
                if (!healthManager.hasPermissions()) {
                    updateStatus("Connect Health Connect before starting auto sync.")
                    openHealthConnectPermissions()
                    return@launch
                }
                if (!DriveClient.hasFile(this@MainActivity)) {
                    updateStatus("Connect Google Drive before starting auto sync.")
                    return@launch
                }
                SyncWorker.schedule(this@MainActivity)
                updateStatus("Background sync active — uploading to Google Drive every 15 min.")
            }
        }
    }

    @Deprecated("Uses legacy activity result API for document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_DRIVE_FILE) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                DriveClient.saveFileUri(this, uri, data.flags)
                updateStatus("Google Drive file connected.\nYour file: health_data.json")
                refreshStatusDisplay()
            } else {
                updateStatus("Google Drive file selection cancelled.")
            }
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        statusText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_slide_in))
    }

    private fun refreshStatusDisplay() {
        val hasDriveFile = DriveClient.hasFile(this)
        lifecycleScope.launch {
            val healthAvailability = healthManager.availability()
            val hasHealth = runCatching { healthManager.hasPermissions() }.getOrDefault(false)
            statusText.text = buildString {
                appendLine("Health Connect: ${healthStatusText(healthAvailability, hasHealth)}")
                appendLine("Google Drive: ${if (hasDriveFile) "File connected" else "Tap button below"}")
                if (hasHealth && hasDriveFile) {
                    appendLine("\nReady to sync. Tap 'Sync Now' or 'Start Auto Sync'.")
                }
            }
        }
    }

    private fun openHealthConnectInstall() {
        updateStatus("Health Connect needs to be installed or updated. Opening Play Store...")
        try {
            startActivity(healthManager.installOrUpdateIntent())
        } catch (e: ActivityNotFoundException) {
            updateStatus("Open Play Store and install or update Health Connect, then try again.")
        }
    }

    private fun openHealthConnectPermissions() {
        try {
            startActivity(healthManager.managePermissionsIntent())
        } catch (e: ActivityNotFoundException) {
            updateStatus(
                "Open Health Connect settings manually:\n" +
                "Settings > Security & privacy > Privacy > Health Connect > App permissions > Health Sync"
            )
        }
    }

    private fun healthStatusText(
        availability: HealthConnectManager.Availability,
        hasPermissions: Boolean
    ): String {
        return when {
            hasPermissions -> "Connected"
            availability == HealthConnectManager.Availability.AVAILABLE -> "Tap button below"
            availability == HealthConnectManager.Availability.INSTALL_OR_UPDATE_REQUIRED -> {
                "Install or update required"
            }
            else -> "Unavailable on this phone"
        }
    }

    private fun createDriveFileIntent(): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "health_data.json")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    companion object {
        private const val RC_DRIVE_FILE = 100
    }
}
