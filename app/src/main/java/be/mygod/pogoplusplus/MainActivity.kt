package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import be.mygod.pogoplusplus.App.Companion.app
import be.mygod.pogoplusplus.util.ShizukuManager
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import eu.darken.porter.sdk.PermissionState
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterConnectionLostException
import eu.darken.porter.sdk.PorterException
import eu.darken.porter.sdk.isGranted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_KEY_LEGACY = ":settings:fragment_args_key"
        private const val STATE_PENDING_SHIZUKU_PAIRING_ENABLE = "pendingShizukuPairingEnable"
        private const val STATE_PENDING_UNPAIR_DEVICE = "pendingUnpairDevice"
        /** Unexported alias of this activity declared in the manifest; MainActivity itself is exported. */
        private const val UNPAIR_ALIAS = "be.mygod.pogoplusplus.UnpairActivity"

        /** Opens PoGo+LE to unpair [device], creating its companion association first when needed. */
        fun makeUnpairPendingIntent(device: BluetoothDevice): PendingIntent = PendingIntent.getActivity(app, 0,
            Intent().apply {
                setClassName(app, UNPAIR_ALIAS)
                data = Uri.fromParts("mac", device.address, null)  // to differentiate as ID
                putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
    private var shizukuPairingReady = false
    private var enableJob: Job? = null
    private var pendingUnpairDevice: BluetoothDevice? = null

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
        val unpairDevice = pendingUnpairDevice
        pendingUnpairDevice = null
        refreshPlatformState()
        if (granted) {
            if (Build.VERSION.SDK_INT >= 36) {
                if (unpairDevice == null) associateCompanion() else unpair(unpairDevice)
            }
        } else showSnackbar(R.string.settings_permission_bluetooth_missing)
    }

    private val requestCompanionAssociation = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) {
        refreshPlatformState()
        // Unlike the association callback, this result reaches an activity recreated while the prompt was open.
        finishPendingUnpair()
    }

    /** Whether the current Porter or Shizuku server has granted access and can confirm pairing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val shizukuPairingReadyFlow = Porter.connection.flatMapLatest { connection ->
        connection?.permission?.mapLatest { permission ->
            permission.isGranted && try {
                ShizukuManager.canConfirmPairing(connection)
            } catch (e: PorterException) {
                Timber.w(e)
                false
            }
        } ?: flowOf(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShizukuPairingEnable = savedInstanceState?.getBoolean(
            STATE_PENDING_SHIZUKU_PAIRING_ENABLE) == true
        pendingUnpairDevice = savedInstanceState?.let {
            BundleCompat.getParcelable(it, STATE_PENDING_UNPAIR_DEVICE, BluetoothDevice::class.java)
        }
        if (savedInstanceState == null) handleIntent(intent)
        if (pairingServicesVisible) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shizukuPairingReadyFlow.collect { ready ->
                    shizukuPairingReady = ready
                    refreshPlatformState()
                }
            }
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
                            pendingUnpairDevice = null
                            if (hasBluetoothPermission) associateCompanion() else {
                                requestCompanionBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    },
                    onPrivilegedPairingChange = { shouldEnable ->
                        pendingShizukuPairingEnable = false
                        enableJob?.cancel()
                        if (shouldEnable) enablePrivilegedPairing()
                        else app.setEnabled<ShizukuPairingReceiver>(false)
                        refreshPlatformState()
                    },
                    onOpenSource = { app.launchUrl(this, "https://github.com/Mygod/pogoplusle") },
                    onDonate = { app.launchUrl(this, "https://mygod.be/donate/") },
                    onOpenLicenses = {
                        OssLicensesMenuActivity.setTheme(appColorScheme(this, false), appColorScheme(this, true),
                            Typography())
                        startActivity(Intent(this, OssLicensesMenuActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPlatformState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_SHIZUKU_PAIRING_ENABLE, pendingShizukuPairingEnable)
        outState.putParcelable(STATE_PENDING_UNPAIR_DEVICE, pendingUnpairDevice)
        super.onSaveInstanceState(outState)
    }

    private fun handleIntent(intent: Intent?) {
        // Only the unexported alias may unpair, since other apps can launch this exported activity with any device.
        // Relaunching from recents redelivers the original intent, which must not start another unpair.
        if (intent?.component?.className != UNPAIR_ALIAS || Build.VERSION.SDK_INT < 36 ||
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java) ?: return
        if (hasBluetoothPermission) unpair(device) else {
            pendingUnpairDevice = device
            requestCompanionBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    /** Unpairs [device] through its companion association, creating the association first when needed. */
    @RequiresApi(36)
    private fun unpair(device: BluetoothDevice) {
        if (SfidaManager.hasCompanionAssociation(device)) return removeBond(device)
        pendingUnpairDevice = device
        associateCompanion(device)
    }

    /**
     * Unpairs [pendingUnpairDevice] once its association exists. Cancellations leave it in place, since a late one
     * could otherwise clear a retried Unpair; a leftover device is harmless, as only its own association acts on it.
     */
    private fun finishPendingUnpair() {
        val device = pendingUnpairDevice ?: return
        if (!SfidaManager.hasCompanionAssociation(device)) return
        pendingUnpairDevice = null
        removeBond(device)
    }

    private fun removeBond(device: BluetoothDevice) {
        val removed = try {
            SfidaManager.disconnect(device)
        } catch (e: SecurityException) {
            Timber.d(e)
            false
        }
        if (!removed) showSnackbar(R.string.notification_action_disconnect_failed)
    }

    private fun refreshPlatformState() {
        val bluetoothPermissionGranted = Build.VERSION.SDK_INT < 31 || hasBluetoothPermission
        val privilegedPairingComponentEnabled = pairingServicesVisible &&
            app.isEnabled<ShizukuPairingReceiver>(false)
        val shizukuAuthorized = bluetoothPermissionGranted && privilegedPairingComponentEnabled &&
            !hasBluetoothPrivilegedPermission && shizukuPairingReady
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
        val connection = Porter.connection.value
        if (connection == null) {
            showSnackbar(R.string.settings_service_pairing_shizuku_missing)
            return
        }
        // Recreating this activity cancels a pending request and Porter drops its answer, but the server keeps the
        // grant, so enabling again succeeds without asking.
        enableJob?.cancel()
        enableJob = lifecycleScope.launch {
            val failure = try {
                var permission = connection.checkPermission()
                if (permission is PermissionState.Denied && !permission.permanentlyDenied) {
                    permission = connection.requestPermission()
                }
                when {
                    !permission.isGranted -> R.string.settings_service_pairing_shizuku_missing
                    ShizukuManager.canConfirmPairing(connection) -> null
                    else -> R.string.settings_service_pairing_shizuku_unsupported
                }
            } catch (e: PorterException) {
                if (e !is PorterConnectionLostException) Timber.w(e)
                R.string.settings_service_pairing_shizuku_missing
            }
            // Commit only if the toggle has not changed since.
            ensureActive()
            app.setEnabled<ShizukuPairingReceiver>(failure == null)
            if (failure != null) showSnackbar(failure)
            refreshPlatformState()
        }
    }

    /** Associates [unpairDevice] if given, else the first bonded accessory that has no association yet. */
    @RequiresApi(36)
    private fun associateCompanion(unpairDevice: BluetoothDevice? = null) {
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    requestCompanionAssociation.launch(IntentSenderRequest.Builder(intentSender).build())
                } else Timber.w("Companion device association is pending after settings closed")
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return
                refreshPlatformState()
                finishPendingUnpair()
            }

            override fun onFailure(error: CharSequence?) {
                Timber.w("Companion device association failed: $error")
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    showSnackbar(R.string.settings_companion_association_failed)
                }
            }
        }
        try {
            if (!SfidaManager.associateCompanion(mainExecutor, callback, unpairDevice)) {
                pendingUnpairDevice = null
                showSnackbar(R.string.settings_companion_association_unavailable)
            }
        } catch (e: SecurityException) {
            Timber.w(e)
            pendingUnpairDevice = null
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
