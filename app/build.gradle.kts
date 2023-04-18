import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    kotlin("android")
    id("kotlin-parcelize")
}

android {
    namespace = "be.mygod.pogoplusplus"
    buildToolsVersion = "33.0.1"
    compileSdk = 33
    defaultConfig {
        applicationId = "be.mygod.pogoplusplus"
        minSdk = 24
        targetSdk = 33
        versionCode = 18
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("debug") {
            isPseudoLocalesEnabled = true
        }
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
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
        viewBinding = true
    }
    packagingOptions.resources.excludes.add("**/*.kotlin_*")
    flavorDimensions.add("freedom")
    productFlavors {
        create("freedom") {
            dimension = "freedom"
            resourceConfigurations.addAll(arrayOf("en", "zh-rCN", "zh-rTW"))
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation("androidx.browser:browser:1.5.0")
    implementation("androidx.core:core-ktx:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")    // TODO fix dependency
    implementation("androidx.preference:preference:1.2.0")
    implementation("be.mygod.librootkotlinx:librootkotlinx:1.0.2")
    implementation("com.android.billingclient:billing-ktx:5.2.0")
    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")
    implementation("com.google.android.material:material:1.9.0-rc01")
    implementation("com.google.firebase:firebase-analytics-ktx:21.2.2")
    implementation("com.google.firebase:firebase-crashlytics:18.3.6")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
