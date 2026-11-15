import java.util.Properties

plugins {
    alias(libs.plugins.skypass.android.library)
    alias(libs.plugins.skypass.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase URL + publishable key come from local.properties (never committed):
//   supabase.url=https://xxxx.supabase.co
//   supabase.key=sb_publishable_...
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps.getProperty("supabase.key", "")}\"")
    }
}

dependencies {
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth)
    api(libs.supabase.postgrest)
    api(libs.supabase.realtime)
    api(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
