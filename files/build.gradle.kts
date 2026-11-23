plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.tvfiles"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tvfiles"
        minSdk = 26 // Philips TPM171E runs Android 8.0
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Deliberately no UI libraries: the TV has very little free RAM.
dependencies {
    implementation(project(":shared"))
    testImplementation(libs.junit)
}
