plugins {
    alias(libs.plugins.skypass.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    testImplementation(projects.core.testing)
}
