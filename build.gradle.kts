plugins {
    id("com.android.application") version "9.5.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("com.google.gms.google-services") version "4.6.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.13.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
