package be.mygod.pogoplusplus

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import be.mygod.pogoplusplus.App.Companion.app
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainPreferenceFragment : PreferenceFragmentCompat() {
    companion object {
        var instance: MainPreferenceFragment? = null

        private const val EXTRA_KEY_LEGACY = ":settings:fragment_args_key"
    }

    private lateinit var servicePairing: TwoStatePreference
    private lateinit var serviceGameNotification: TwoStatePreference
    private lateinit var permissionBluetooth: TwoStatePreference
    private fun Preference.remove() = parent!!.removePreference(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_main)
        val needsServicePairing = if (Build.VERSION.SDK_INT >= 26) {
            val array = Build.VERSION.SECURITY_PATCH.split('-', limit = 3)
            val y = array.getOrNull(0)?.toIntOrNull()
            val m = array.getOrNull(1)?.toIntOrNull()
            y == null || y > 2020 || y == 2020 && (m == null || m >= 11)
        } else false
        servicePairing = findPreference("service.pairing")!!
        if (needsServicePairing) servicePairing.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle("Grant control to this app?")
                setMessage(R.string.bluetooth_pairing_service_disclosure)
                setNegativeButton("Reject", null)
                setPositiveButton("Accept") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }.create().show() else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            false
        } else servicePairing.remove()
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
                    putExtra(":settings:show_fragment_args", bundleOf(EXTRA_KEY_LEGACY to componentName))
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
            EBegFragment().show(parentFragmentManager, "EBegFragment")
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
        permissionBluetooth.isChecked = granted
        if (!granted) Snackbar.make(requireView(), "Missing Nearby devices permission", Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        instance = this
        updateSwitches()
        permissionBluetooth.isChecked = (Build.VERSION.SDK_INT < 31 || hasBluetoothPermission) &&
                app.isEnabled<BluetoothReceiver>()
    }

    fun updateSwitches() {
        servicePairing.isChecked = BluetoothPairingService.instance != null
        serviceGameNotification.isChecked = GameNotificationService.isRunning
    }

    override fun onPause() {
        instance = null
        super.onPause()
    }
}
