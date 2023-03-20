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
        versionCode = 17
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "DONATIONS", "true")
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
        viewBinding = true
    }
    flavorDimensions.add("freedom")
    productFlavors {
        create("freedom") {
            dimension = "freedom"
            resourceConfigurations.addAll(arrayOf("en", "zh-rCN", "zh-rTW"))
        }
        create("google") {
            dimension = "freedom"
            versionNameSuffix = "-g"
            buildConfigField("boolean", "DONATIONS", "false")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.2")
    implementation("androidx.browser:browser:1.5.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.0")    // TODO fix dependency
    implementation("androidx.preference:preference:1.2.0")
    implementation("be.mygod.librootkotlinx:librootkotlinx:1.0.2")
    implementation("com.android.billingclient:billing-ktx:5.1.0")
    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")
    implementation("com.google.android.material:material:1.9.0-beta01")
    implementation("com.google.firebase:firebase-analytics-ktx:21.2.0")
    implementation("com.google.firebase:firebase-crashlytics:18.3.5")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
