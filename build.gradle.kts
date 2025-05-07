plugins {
    id("com.android.application") version "8.10.0" apply false
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.google.firebase.crashlytics") version "3.0.3" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}
