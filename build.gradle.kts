plugins {
    id("com.android.application") version "8.10.0" apply false
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.google.firebase.crashlytics") version "3.0.3" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
