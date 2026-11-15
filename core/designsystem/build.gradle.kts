plugins {
    alias(libs.plugins.skypass.android.library.compose)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
}
