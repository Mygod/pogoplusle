package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import be.mygod.pogoplusplus.App.Companion.app
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
    }

    private lateinit var servicePairing: TwoStatePreference
    private lateinit var serviceGameNotification: TwoStatePreference
    private lateinit var permissionBluetooth: TwoStatePreference
    private lateinit var servicePairingShizuku: TwoStatePreference
    private var pendingShizukuPairingEnable = false
    private fun Preference.remove() = parent!!.removePreference(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_main)
        Shizuku.addRequestPermissionResultListener(requestShizukuPermission)
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
        servicePairingShizuku = findPreference("service.pairingRoot")!!
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
            servicePairingShizuku.setOnPreferenceChangeListener { _, newValue ->
                val shouldEnable = newValue as Boolean
                pendingShizukuPairingEnable = false
                if (shouldEnable) enableShizukuPairing() else {
                    app.setEnabled<BluetoothPairingReceiver>(false)
                    true
                }
            }
        } else {
            servicePairing.remove()
            // we only hit here if API < 31, in which case neither entries would be needed
            servicePairingShizuku.parent!!.remove()
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

    @get:RequiresApi(31)
    private val hasBluetoothPermission get() = requireContext().checkSelfPermission(
        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    @TargetApi(31)
    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        permissionBluetooth.isChecked = granted && app.isEnabled<BluetoothReceiver>()
        if (pendingShizukuPairingEnable) {
            pendingShizukuPairingEnable = false
            servicePairingShizuku.isChecked = granted && enableShizukuPairing()
        } else updateShizukuPairingSwitch()
        if (!granted) Snackbar.make(requireView(), R.string.settings_permission_bluetooth_missing,
            Snackbar.LENGTH_LONG).show()
    }

    private val requestShizukuPermission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU_PAIRING) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                app.setEnabled<BluetoothPairingReceiver>(true)
            } else {
                view?.let { Snackbar.make(it, R.string.settings_service_pairing_shizuku_missing,
                    Snackbar.LENGTH_LONG).show() }
            }
            updateShizukuPairingSwitch()
        }
    }

    private fun enableShizukuPairing(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission) {
            pendingShizukuPairingEnable = true
            requestBluetoothPermission.launch(if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            } else arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return false
        }
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    app.setEnabled<BluetoothPairingReceiver>(true)
                    return true
                }
                if (!Shizuku.shouldShowRequestPermissionRationale()) {
                    Shizuku.requestPermission(REQUEST_SHIZUKU_PAIRING)
                    return false
                }
            }
        } catch (e: RuntimeException) {
            Timber.w(e)
        }
        view?.let { Snackbar.make(it, R.string.settings_service_pairing_shizuku_missing,
            Snackbar.LENGTH_LONG).show() }
        return false
    }

    private fun updateShizukuPairingSwitch() {
        servicePairingShizuku.isChecked = (Build.VERSION.SDK_INT < 31 || hasBluetoothPermission) &&
                app.isEnabled<BluetoothPairingReceiver>(false)
    }

    override fun onStart() {
        super.onStart()
        instance = this
        updateSwitches()
        permissionBluetooth.isChecked = (Build.VERSION.SDK_INT < 31 || hasBluetoothPermission) &&
                app.isEnabled<BluetoothReceiver>()
        updateShizukuPairingSwitch()
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
        super.onDestroy()
    }
}
