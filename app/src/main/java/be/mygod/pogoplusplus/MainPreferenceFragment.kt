package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.TargetApi
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import be.mygod.pogoplusplus.App.Companion.app
import be.mygod.pogoplusplus.util.ShizukuManager
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import rikka.shizuku.Shizuku
import timber.log.Timber

class MainPreferenceFragment : PreferenceFragmentCompat() {
    companion object {
        var instance: MainPreferenceFragment? = null

        private const val EXTRA_KEY_LEGACY = ":settings:fragment_args_key"
        private const val REQUEST_SHIZUKU_PAIRING = 1
        private const val STATE_PENDING_SHIZUKU_PAIRING_ENABLE = "pendingShizukuPairingEnable"
    }

    private lateinit var servicePairing: TwoStatePreference
    private lateinit var serviceGameNotification: TwoStatePreference
    private lateinit var permissionBluetooth: TwoStatePreference
    private lateinit var servicePairingPrivileged: TwoStatePreference
    private var companionAssociation: Preference? = null
    private var pendingShizukuPairingEnable = false
    private fun Preference.remove() = parent!!.removePreference(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_main)
        pendingShizukuPairingEnable = savedInstanceState?.getBoolean(
            STATE_PENDING_SHIZUKU_PAIRING_ENABLE) == true
        findPreference<Preference>("play")?.setOnPreferenceClickListener {
            app.launchUrl(requireContext(), "https://github.com/Mygod/pogoplusle/discussions/46")
            true
        }
        val needsServicePairing = if (Build.VERSION.SDK_INT >= 31) true else {
            val array = Build.VERSION.SECURITY_PATCH.split('-', limit = 3)
            val y = array.getOrNull(0)?.toIntOrNull()
            val m = array.getOrNull(1)?.toIntOrNull()
            y == null || y > 2020 || y == 2020 && (m == null || m >= 11)
        }
        servicePairing = findPreference("service.pairing")!!
        servicePairingPrivileged = findPreference("service.pairingPrivileged")!!
        findPreference<Preference>("bluetooth.companionAssociation")!!.let { preference ->
            if (SfidaManager.companionDeviceSetupSupported) {
                companionAssociation = preference
                preference.setOnPreferenceClickListener {
                    if (hasBluetoothPermission) associateCompanion() else {
                        requestCompanionBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    true
                }
            } else preference.remove()
        }
        if (needsServicePairing) {
            servicePairing.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.bluetooth_pairing_service_disclosure_title)
                    val base = getText(R.string.bluetooth_pairing_service_disclosure)
                    setMessage(if (Build.VERSION.SDK_INT >= 33) SpannableStringBuilder(base).apply {
                        append("\n\n")
                        append(getText(R.string.bluetooth_pairing_service_disclosure_restricted_settings))
                    } else base)
                    setNegativeButton(resources.getIdentifier("decline", "string", "android"), null)
                    setPositiveButton(resources.getIdentifier("accept", "string", "android")) { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }.create().show() else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                false
            }
            servicePairingPrivileged.setOnPreferenceChangeListener { _, newValue ->
                val shouldEnable = newValue as Boolean
                pendingShizukuPairingEnable = false
                if (shouldEnable) enablePrivilegedPairing() else {
                    app.setEnabled<ShizukuPairingReceiver>(false)
                    true
                }
            }
            Shizuku.addRequestPermissionResultListener(requestShizukuPermission)
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived)
            Shizuku.addBinderDeadListener(shizukuBinderDead)
        } else {
            servicePairing.remove()
            // we only hit here if API < 31, in which case neither entries would be needed
            servicePairingPrivileged.parent!!.remove()
        }
        serviceGameNotification = findPreference("service.gameNotification")!!
        serviceGameNotification.setOnPreferenceChangeListener { _, _ ->
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
            false
        }
        permissionBluetooth = findPreference("permission.bluetooth")!!
        permissionBluetooth.setOnPreferenceChangeListener { _, newValue ->
            val shouldEnable = newValue as Boolean
            app.setEnabled<BluetoothReceiver>(shouldEnable)
            if (shouldEnable && Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission) {
                requestBluetoothPermission.launch(if (Build.VERSION.SDK_INT >= 33) {
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
                } else arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                false
            } else true
        }
        findPreference<Preference>("permission.notification")!!.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
            true
        }
        findPreference<Preference>("game")!!.setOnPreferenceClickListener {
            startActivity(GameNotificationService.gameIntent)
            true
        }
        findPreference<Preference>("misc.source")!!.setOnPreferenceClickListener {
            app.launchUrl(requireContext(), "https://github.com/Mygod/pogoplusle")
            true
        }
        findPreference<Preference>("misc.donate")!!.setOnPreferenceClickListener {
            app.launchUrl(requireContext(), "https://mygod.be/donate/")
            true
        }
        findPreference<Preference>("misc.licenses")!!.setOnPreferenceClickListener {
            startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(listView) { list, insets ->
            insets.apply {
                list.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_SHIZUKU_PAIRING_ENABLE, pendingShizukuPairingEnable)
        super.onSaveInstanceState(outState)
    }

    @get:RequiresApi(31)
    private val hasBluetoothPermission get() = requireContext().checkSelfPermission(
        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private val hasBluetoothPrivilegedPermission get() = requireContext().checkSelfPermission(
        Manifest.permission.BLUETOOTH_PRIVILEGED) == PackageManager.PERMISSION_GRANTED
    @TargetApi(31)
    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        permissionBluetooth.isChecked = granted && app.isEnabled<BluetoothReceiver>()
        if (pendingShizukuPairingEnable) {
            pendingShizukuPairingEnable = false
            servicePairingPrivileged.isChecked = granted && enablePrivilegedPairing()
        } else updatePrivilegedPairingSwitch()
        if (!granted) Snackbar.make(requireView(), R.string.settings_permission_bluetooth_missing,
            Snackbar.LENGTH_LONG).show()
    }

    @TargetApi(31)
    private val requestCompanionBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        permissionBluetooth.isChecked = granted && app.isEnabled<BluetoothReceiver>()
        updatePrivilegedPairingSwitch()
        if (granted) {
            if (Build.VERSION.SDK_INT >= 36) associateCompanion()
        } else Snackbar.make(requireView(), R.string.settings_permission_bluetooth_missing,
            Snackbar.LENGTH_LONG).show()
    }

    private val requestShizukuPermission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU_PAIRING) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                app.setEnabled<ShizukuPairingReceiver>(false)
                view?.let {
                    Snackbar.make(it, R.string.settings_service_pairing_shizuku_missing, Snackbar.LENGTH_LONG).show()
                }
            } else app.setEnabled<ShizukuPairingReceiver>(true)
            updatePrivilegedPairingSwitch()
        }
    }

    private val requestCompanionAssociation = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()) { updateCompanionAssociationPreference() }

    @RequiresApi(36)
    private fun associateCompanion() {
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                if (isAdded && lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    requestCompanionAssociation.launch(IntentSenderRequest.Builder(intentSender).build())
                } else Timber.w("Companion device association is pending after settings closed")
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (isAdded && lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    updateCompanionAssociationPreference()
                }
            }

            override fun onFailure(error: CharSequence?) {
                Timber.w("Companion device association failed: $error")
                view?.let {
                    Snackbar.make(it, R.string.settings_companion_association_failed, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        try {
            if (!SfidaManager.associateCompanion(requireContext().mainExecutor, callback)) {
                Snackbar.make(requireView(), R.string.settings_companion_association_unavailable,
                    Snackbar.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Timber.w(e)
            Snackbar.make(requireView(), R.string.settings_permission_bluetooth_missing,
                Snackbar.LENGTH_LONG).show()
        }
    }

    private fun updateCompanionAssociationPreference() {
        val preference = companionAssociation ?: return
        if (Build.VERSION.SDK_INT >= 36) {
            val count = SfidaManager.companionAssociationCount
            preference.summary = if (count == 0) getString(R.string.settings_companion_association_summary)
            else getString(R.string.settings_companion_association_summary_count, count)
        }
    }

    private val shizukuBinderReceived = Shizuku.OnBinderReceivedListener { updatePrivilegedPairingSwitch() }
    private val shizukuBinderDead = Shizuku.OnBinderDeadListener { updatePrivilegedPairingSwitch() }

    private fun enablePrivilegedPairing(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission) {
            pendingShizukuPairingEnable = true
            requestBluetoothPermission.launch(if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            } else arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return false
        }
        if (hasBluetoothPrivilegedPermission) {
            app.setEnabled<ShizukuPairingReceiver>(true)
            return true
        }
        try {
            if (ShizukuManager.isAuthorized()) {
                app.setEnabled<ShizukuPairingReceiver>(true)
                return true
            }
            if (Shizuku.pingBinder() && !Shizuku.isPreV11() && !Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PAIRING)
                return false
            }
        } catch (e: RuntimeException) {
            Timber.w(e)
        }
        view?.let {
            Snackbar.make(it, R.string.settings_service_pairing_shizuku_missing, Snackbar.LENGTH_LONG).show()
        }
        return false
    }

    private fun updatePrivilegedPairingSwitch() {
        servicePairingPrivileged.isChecked = (Build.VERSION.SDK_INT < 31 || hasBluetoothPermission) &&
                app.isEnabled<ShizukuPairingReceiver>(false) &&
                (hasBluetoothPrivilegedPermission || ShizukuManager.isAuthorized())
    }

    override fun onStart() {
        super.onStart()
        instance = this
        updateSwitches()
        permissionBluetooth.isChecked = (Build.VERSION.SDK_INT < 31 || hasBluetoothPermission) &&
                app.isEnabled<BluetoothReceiver>()
        updatePrivilegedPairingSwitch()
        updateCompanionAssociationPreference()
    }

    fun updateSwitches() {
        servicePairing.isChecked = BluetoothPairingService.instance != null
        serviceGameNotification.isChecked = GameNotificationService.isRunning
    }

    override fun onStop() {
        instance = null
        super.onStop()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(requestShizukuPermission)
        Shizuku.removeBinderReceivedListener(shizukuBinderReceived)
        Shizuku.removeBinderDeadListener(shizukuBinderDead)
        super.onDestroy()
    }
}
