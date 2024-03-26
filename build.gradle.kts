plugins {
    id("com.android.application") version "8.3.1" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}
