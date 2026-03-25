plugins {
    id("com.android.application") version "9.1.0" apply false
    id("com.github.ben-manes.versions") version "0.53.0"
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}

buildscript {
    dependencies {
        classpath("com.google.android.gms:oss-licenses-plugin:0.11.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}
