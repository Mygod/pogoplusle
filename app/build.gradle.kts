import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    kotlin("android")
    id("kotlin-parcelize")
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "be.mygod.pogoplusplus"
    compileSdk = 36
    defaultConfig {
        applicationId = "be.mygod.pogoplusplus"
        minSdk = 28
        targetSdk = 36
        versionCode = 39
        versionName = "1.3.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            vcsInfo.include = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    val javaVersion = JavaVersion.VERSION_11
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    kotlinOptions.jvmTarget = javaVersion.toString()
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }
    packagingOptions.resources.excludes.add("**/*.kotlin_*")
    flavorDimensions.add("freedom")
    productFlavors {
        create("freedom") {
            dimension = "freedom"
            androidResources.localeFilters += listOf("en", "zh-rCN", "zh-rTW")
        }
        create("google") {
            dimension = "freedom"
            versionNameSuffix = "-g"
            val prop = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) load(f.inputStream())
            }
            if (prop.containsKey("codeTransparency.storeFile")) bundle.codeTransparency.signing {
                storeFile = file(prop["codeTransparency.storeFile"]!!)
                storePassword = prop["codeTransparency.storePassword"] as? String
                keyAlias = prop["codeTransparency.keyAlias"] as? String
                keyPassword = if (prop.containsKey("codeTransparency.keyPassword")) {
                    prop["codeTransparency.keyPassword"] as? String
                } else storePassword
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.material3.android)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.compose.foundation:foundation-layout:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("be.mygod.librootkotlinx:librootkotlinx:1.2.1")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.firebase:firebase-analytics:22.4.0")
    implementation("com.google.firebase:firebase-crashlytics:19.4.4")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
