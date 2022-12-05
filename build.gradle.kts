plugins {
    id("com.android.application") version "7.4.0-rc01" apply false
    id("com.github.ben-manes.versions") version "0.44.0"
    id("org.jetbrains.kotlin.android") version "1.7.21" apply false
}

buildscript {
    dependencies {
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.2")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.5")
        classpath("com.google.gms:google-services:4.3.14")
    }
}
