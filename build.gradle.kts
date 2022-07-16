plugins {
    id("com.android.application") version "7.3.0-beta05" apply false
    id("com.github.ben-manes.versions") version "0.42.0"
    id("org.jetbrains.kotlin.android") version "1.7.10" apply false
}

buildscript {
    dependencies {
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.1")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.5")
        classpath("com.google.gms:google-services:4.3.13")
    }
}
