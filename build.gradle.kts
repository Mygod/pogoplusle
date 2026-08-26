plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.github.ben-manes.versions") version "0.54.0"
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.13.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}
