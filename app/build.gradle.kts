import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-parcelize")
}

val javaVersion = JavaVersion.VERSION_11

android {
    namespace = "be.mygod.pogoplusplus"
    compileSdk = 36
    defaultConfig {
        applicationId = "be.mygod.pogoplusplus"
        minSdk = 28
        targetSdk = 36
        versionCode = 42
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources.localeFilters += listOf("en", "zh-rCN", "zh-rTW")
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
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    packaging.resources.excludes.add("**/*.kotlin_*")
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("be.mygod.librootkotlinx:librootkotlinx:1.2.1")
    implementation("com.google.android.gms:play-services-oss-licenses:17.4.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.firebase:firebase-analytics:23.2.0")
    implementation("com.google.firebase:firebase-crashlytics:20.0.4")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
