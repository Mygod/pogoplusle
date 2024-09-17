plugins {
    id("com.android.application") version "8.6.0" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}
