plugins {
    alias(libs.plugins.skypass.android.application.compose)
    alias(libs.plugins.skypass.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.skypass"

    defaultConfig {
        applicationId = "com.skypass"
        versionCode = 204
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures.buildConfig = true

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // No release keystore yet: sign with the debug key so the APK installs and Google
            // sign-in matches the SHA-1 registered for it. Swap in a real key before any store upload.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.results)
    implementation(projects.feature.booking)
    implementation(projects.feature.payment)
    implementation(projects.feature.confirmation)
    implementation(projects.feature.explore)
    implementation(projects.feature.hotels)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
