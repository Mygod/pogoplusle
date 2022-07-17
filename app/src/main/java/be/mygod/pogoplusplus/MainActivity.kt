package be.mygod.pogoplusplus

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import be.mygod.pogoplusplus.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var suppressListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressListener) return@setOnCheckedChangeListener
            if (isChecked) MaterialAlertDialogBuilder(this).apply {
                binding.serviceSwitch.isChecked = false
                setTitle("Grant control to this app?")
                setMessage("This app needs to read and control notifications and screen for system Settings and the game in order to skip pairing dialog and more. No data is collected through this process except for crash logs.\n\nTo grant the permission, click 'Agree', then click on this app and turn on the main switch that says 'Use PoGo+LE'.")
                setNegativeButton("Reject", null)
                setPositiveButton("Accept") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }.create().show() else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    override fun onResume() {
        super.onResume()
        suppressListener = true
        binding.serviceSwitch.isChecked = MainService.isRunning
        suppressListener = false
    }
}
