package be.mygod.pogoplusplus.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.IllegalFormatException

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

@SuppressLint("DiscouragedApi")
fun Context.findStrings(name: String, vararg formatArgs: Any): Set<String> {
    val id = resources.getIdentifier(name, "string", packageName)
    if (id == 0) return emptySet()
    val baseConfiguration = Configuration(resources.configuration)
    return buildSet {
        fun addString(resources: Resources) {
            add(if (formatArgs.isEmpty()) resources.getString(id) else resources.getString(id, *formatArgs))
        }
        addString(resources)
        for (localeTag in assets.locales.orEmpty()) {
            if (localeTag.isEmpty()) continue
            addString(createConfigurationContext(Configuration(baseConfiguration).apply {
                setLocales(LocaleList.forLanguageTags(localeTag))
            }).resources)
        }
    }
}
