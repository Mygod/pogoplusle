plugins {
    id("com.android.application") version "7.4.2" apply false
    id("com.github.ben-manes.versions") version "0.46.0"
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:bundletool:1.14.0")  // TODO: fix 1.13.1+
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.4")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
        classpath("com.google.gms:google-services:4.3.15")
    }
}
