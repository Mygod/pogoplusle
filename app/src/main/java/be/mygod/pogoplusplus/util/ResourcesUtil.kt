package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.content.res.Resources

@SuppressLint("DiscouragedApi")
fun Resources.findString(name: String, packageName: String?) = try {
    getString(getIdentifier(name, "string", packageName))
} catch (_: Resources.NotFoundException) {
    null
}
@SuppressLint("DiscouragedApi")
fun Resources.findString(name: String, packageName: String?, vararg formatArgs: Any) = try {
    getString(getIdentifier(name, "string", packageName), *formatArgs)
} catch (_: Resources.NotFoundException) {
    null
}
