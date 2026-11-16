plugins {
    alias(libs.plugins.skypass.android.feature)
}

dependencies {
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    testImplementation(projects.core.testing)
}
