package be.mygod.pogoplusplus

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import be.mygod.pogoplusplus.App.Companion.app
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.snackbar.Snackbar

class MainPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var permissionBluetooth: TwoStatePreference
    private fun Preference.remove() = parent!!.removePreference(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_main)
        permissionBluetooth = findPreference("permission.bluetooth")!!
        if (Build.VERSION.SDK_INT >= 31) permissionBluetooth.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            false
        } else permissionBluetooth.remove()
        findPreference<Preference>("permission.notification")!!.apply {
            setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                })
                true
            }
        }
        findPreference<Preference>("game")!!.setOnPreferenceClickListener {
            val intent = GameNotificationService.gameIntent
            if (intent == null) {
                Snackbar.make(requireView(), "You don't even haf teh gaem!", Snackbar.LENGTH_SHORT).show()
            } else startActivity(intent)
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

    private val requestBluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionBluetooth.isChecked = it
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 31) permissionBluetooth.isChecked = requireContext().checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}
