package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.content.res.Resources

@SuppressLint("DiscouragedApi")
fun Resources.findString(name: String, packageName: String?, vararg formatArgs: Any) = try {
    getString(getIdentifier(name, "string", packageName), *formatArgs)
} catch (e: Resources.NotFoundException) {
    null
}
