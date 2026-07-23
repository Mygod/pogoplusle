package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import be.mygod.pogoplusplus.App.Companion.app
import be.mygod.pogoplusplus.util.ShizukuManager
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import timber.log.Timber

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_KEY_LEGACY = ":settings:fragment_args_key"
        private const val REQUEST_SHIZUKU_PAIRING = 1
        private const val STATE_PENDING_SHIZUKU_PAIRING_ENABLE = "pendingShizukuPairingEnable"
    }

    private data class PlatformState(
        val bluetoothMonitorEnabled: Boolean = false,
        val privilegedPairingEnabled: Boolean = false,
        val companionAssociationCount: Int = 0,
    )

    private val pairingServicesVisible = needsServicePairing(Build.VERSION.SDK_INT, Build.VERSION.SECURITY_PATCH)
    private val snackbarHostState = SnackbarHostState()
    private var platformState by mutableStateOf(PlatformState())
    private var showPairingDisclosure by mutableStateOf(false)
    private var pendingShizukuPairingEnable = false

    @get:RequiresApi(31)
    private val hasBluetoothPermission get() = checkSelfPermission(
        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private val hasBluetoothPrivilegedPermission get() = checkSelfPermission(
        Manifest.permission.BLUETOOTH_PRIVILEGED) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("InlinedApi")
    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        if (pendingShizukuPairingEnable) {
            pendingShizukuPairingEnable = false
            if (granted) enablePrivilegedPairing()
        }
        refreshPlatformState()
        if (!granted) showSnackbar(R.string.settings_permission_bluetooth_missing)
    }

    @SuppressLint("InlinedApi")
    private val requestCompanionBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        refreshPlatformState()
        if (granted) {
            if (Build.VERSION.SDK_INT >= 36) associateCompanion()
        } else showSnackbar(R.string.settings_permission_bluetooth_missing)
    }

    private val requestCompanionAssociation = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) { refreshPlatformState() }

    private val requestShizukuPermission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU_PAIRING) runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                app.setEnabled<ShizukuPairingReceiver>(true)
            } else {
                app.setEnabled<ShizukuPairingReceiver>(false)
                showSnackbar(R.string.settings_service_pairing_shizuku_missing)
            }
            refreshPlatformState()
        }
    }

    private val shizukuBinderReceived = Shizuku.OnBinderReceivedListener {
        runOnUiThread(::refreshPlatformState)
    }
    private val shizukuBinderDead = Shizuku.OnBinderDeadListener {
        runOnUiThread(::refreshPlatformState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShizukuPairingEnable = savedInstanceState?.getBoolean(
            STATE_PENDING_SHIZUKU_PAIRING_ENABLE) == true
        if (pairingServicesVisible) {
            Shizuku.addRequestPermissionResultListener(requestShizukuPermission)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived)
            Shizuku.addBinderDeadListener(shizukuBinderDead)
        }
        refreshPlatformState()
        setContent {
            PoGoPlusPlusTheme {
                val pairingServiceRunning by BluetoothPairingService.running.collectAsStateWithLifecycle()
                val gameNotificationServiceRunning by GameNotificationService.running.collectAsStateWithLifecycle()
                MainScreen(
                    state = MainUiState(
                        pairingServiceRunning = pairingServiceRunning,
                        gameNotificationServiceRunning = gameNotificationServiceRunning,
                        bluetoothMonitorEnabled = platformState.bluetoothMonitorEnabled,
                        privilegedPairingEnabled = platformState.privilegedPairingEnabled,
                        showPairingServices = pairingServicesVisible,
                        showCompanionAssociation = SfidaManager.companionDeviceSetupSupported,
                        companionAssociationCount = platformState.companionAssociationCount,
                    ),
                    snackbarHostState = snackbarHostState,
                    showPairingDisclosure = showPairingDisclosure,
                    onDismissPairingDisclosure = { showPairingDisclosure = false },
                    onAcceptPairingDisclosure = {
                        showPairingDisclosure = false
                        openAccessibilitySettings()
                    },
                    onPairingServiceChange = { shouldEnable ->
                        if (shouldEnable) showPairingDisclosure = true else openAccessibilitySettings()
                    },
                    onGameNotificationServiceChange = {
                        // https://stackoverflow.com/a/63914445/2245107
                        startActivity(Intent().apply {
                            val componentName = app.componentName<GameNotificationService>().flattenToString()
                            if (Build.VERSION.SDK_INT >= 30) {
                                action = Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
                                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName)
                            } else {
                                action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                putExtra(EXTRA_KEY_LEGACY, componentName)
                                putExtra(":settings:show_fragment_args", Bundle().apply {
                                    putString(EXTRA_KEY_LEGACY, componentName)
                                })
                            }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    },
                    onBluetoothMonitorChange = { shouldEnable ->
                        app.setEnabled<BluetoothReceiver>(shouldEnable)
                        refreshPlatformState()
                        if (shouldEnable && Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission) {
                            requestBluetoothPermission.launch(
                                bluetoothRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray())
                        }
                    },
                    onManageNotifications = {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    },
                    onLaunchGame = { startActivity(GameNotificationService.gameIntent) },
                    onCompanionAssociation = {
                        if (Build.VERSION.SDK_INT >= 36) {
                            if (hasBluetoothPermission) associateCompanion() else {
                                requestCompanionBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    },
                    onPrivilegedPairingChange = { shouldEnable ->
                        pendingShizukuPairingEnable = false
                        if (shouldEnable) enablePrivilegedPairing()
                        else app.setEnabled<ShizukuPairingReceiver>(false)
                        refreshPlatformState()
                    },
                    onOpenSource = { app.launchUrl(this, "https://github.com/Mygod/pogoplusle") },
                    onDonate = { app.launchUrl(this, "https://mygod.be/donate/") },
                    onOpenLicenses = { startActivity(Intent(this, OssLicensesMenuActivity::class.java)) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPlatformState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_SHIZUKU_PAIRING_ENABLE, pendingShizukuPairingEnable)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (pairingServicesVisible) {
            Shizuku.removeRequestPermissionResultListener(requestShizukuPermission)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceived)
            Shizuku.removeBinderDeadListener(shizukuBinderDead)
        }
        super.onDestroy()
    }

    private fun refreshPlatformState() {
        val bluetoothPermissionGranted = Build.VERSION.SDK_INT < 31 || hasBluetoothPermission
        val privilegedPairingComponentEnabled = pairingServicesVisible &&
            app.isEnabled<ShizukuPairingReceiver>(false)
        val shizukuAuthorized = bluetoothPermissionGranted && privilegedPairingComponentEnabled &&
            !hasBluetoothPrivilegedPermission && try {
                ShizukuManager.isAuthorized()
            } catch (e: RuntimeException) {
                Timber.w(e)
                false
            }
        platformState = PlatformState(
            bluetoothMonitorEnabled = bluetoothPermissionGranted && app.isEnabled<BluetoothReceiver>(),
            privilegedPairingEnabled = bluetoothPermissionGranted && privilegedPairingComponentEnabled &&
                (hasBluetoothPrivilegedPermission || shizukuAuthorized),
            companionAssociationCount = if (SfidaManager.companionDeviceSetupSupported) {
                SfidaManager.companionAssociationCount
            } else 0,
        )
    }

    private fun enablePrivilegedPairing() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission) {
            pendingShizukuPairingEnable = true
            requestBluetoothPermission.launch(
                bluetoothRuntimePermissions(Build.VERSION.SDK_INT).toTypedArray())
            return
        }
        if (hasBluetoothPrivilegedPermission) {
            app.setEnabled<ShizukuPairingReceiver>(true)
            return
        }
        try {
            if (ShizukuManager.isAuthorized()) {
                app.setEnabled<ShizukuPairingReceiver>(true)
                return
            }
            if (Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PAIRING)
                return
            }
        } catch (e: RuntimeException) {
            Timber.w(e)
        }
        showSnackbar(R.string.settings_service_pairing_shizuku_missing)
    }

    @RequiresApi(36)
    private fun associateCompanion() {
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    requestCompanionAssociation.launch(IntentSenderRequest.Builder(intentSender).build())
                } else Timber.w("Companion device association is pending after settings closed")
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) refreshPlatformState()
            }

            override fun onFailure(error: CharSequence?) {
                Timber.w("Companion device association failed: $error")
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    showSnackbar(R.string.settings_companion_association_failed)
                }
            }
        }
        try {
            if (!SfidaManager.associateCompanion(mainExecutor, callback)) {
                showSnackbar(R.string.settings_companion_association_unavailable)
            }
        } catch (e: SecurityException) {
            Timber.w(e)
            showSnackbar(R.string.settings_permission_bluetooth_missing)
        }
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })

    private fun showSnackbar(message: Int) {
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(getString(message), duration = SnackbarDuration.Long)
        }
    }
}
