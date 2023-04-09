package be.mygod.pogoplusplus

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import be.mygod.librootkotlinx.NoShellException
import be.mygod.pogoplusplus.util.RootManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class App : Application() {
    companion object {
        lateinit var app: App
    }

    val bluetooth by lazy { getSystemService<BluetoothManager>()!! }

    override fun onCreate() {
        super.onCreate()
        app = this
        // overhead of debug mode is minimal: https://github.com/Kotlin/kotlinx.coroutines/blob/f528898/docs/debugging.md#debug-mode
        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        when (val codename = Build.VERSION.CODENAME) {
            "REL" -> { }
            else -> FirebaseCrashlytics.getInstance().apply {
                setCustomKey("codename", codename)
                setCustomKey("preview_sdk", Build.VERSION.PREVIEW_SDK_INT)
            }
        }
        Timber.plant(object : Timber.DebugTree() {
            @SuppressLint("LogNotTimber")
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (t == null) {
                    if (priority != Log.DEBUG || BuildConfig.DEBUG) Log.println(priority, tag, message)
                    FirebaseCrashlytics.getInstance().log("${"XXVDIWEF".getOrElse(priority) { 'X' }}/$tag: $message")
                } else {
                    if (priority >= Log.WARN || priority == Log.DEBUG) {
                        Log.println(priority, tag, message)
                        Log.w(tag, message, t)
                    }
                    if (priority >= Log.INFO && t !is NoShellException) {
                        FirebaseCrashlytics.getInstance().recordException(t)
                    }
                }
            }
        })
        GameNotificationService.updateNotificationChannels()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND) GlobalScope.launch {
            RootManager.closeExisting()
        }
    }

    inline fun <reified T> componentName() = ComponentName(this, T::class.java)
    inline fun <reified T> isEnabled(defaultEnabled: Boolean = true) =
        when (packageManager.getComponentEnabledSetting(componentName<T>())) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaultEnabled
            else -> false
        }
    inline fun <reified T> setEnabled(value: Boolean) = packageManager.setComponentEnabledSetting(componentName<T>(),
        if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP)

    private val customTabsIntent by lazy {
        CustomTabsIntent.Builder().apply {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(getColor(R.color.primaryLightColor))
            }.build())
            setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, CustomTabColorSchemeParams.Builder().apply {
                setToolbarColor(getColor(R.color.primaryDarkColor))
            }.build())
        }.build()
    }
    fun launchUrl(context: Context, url: String) {
        try {
            return app.customTabsIntent.launchUrl(context, url.toUri())
        } catch (e: RuntimeException) {
            Timber.d(e)
        }
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
