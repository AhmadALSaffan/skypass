import java.util.Properties

plugins {
    alias(libs.plugins.skypass.android.library)
    alias(libs.plugins.skypass.hilt)
}

// Google OAuth "Web application" client ID, from local.properties (google.webClientId=...).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("google.webClientId", "")}\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
}
