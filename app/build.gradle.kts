import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("androidx.room3")
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.compose")
}

val javaVersion = JavaVersion.VERSION_11

android {
    namespace = "be.mygod.pogoplusplus"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "be.mygod.pogoplusplus"
        minSdk = 28
        targetSdk = 37
        versionCode = 44
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources.localeFilters += listOf(
            "en", "fr", "de", "hi", "in", "zh-rTW", "it", "ja", "ko", "pt-rBR", "ru", "es-rES", "th",
            "tr", "es-rMX",
        )
    }
    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            optimization {
                enable = true
            }
            vcsInfo.include = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging.resources.excludes.add("**/*.kotlin_*")
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom-beta:2026.11.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.room3:room3-runtime:3.0.3")
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.3")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.firebase:firebase-analytics:24.0.0")
    implementation("com.google.firebase:firebase-crashlytics:21.1.0")
    implementation("com.github.d4rken-org.porter-api:sdk:0.9.0")
    implementation("com.github.d4rken-org.porter-api:shizuku-compat:0.9.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    ksp("androidx.room3:room3-compiler:3.0.3")
    compileOnly("de.robv.android.xposed:api:82")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
