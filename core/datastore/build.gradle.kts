plugins {
    alias(libs.plugins.skypass.android.library)
    alias(libs.plugins.skypass.hilt)
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
