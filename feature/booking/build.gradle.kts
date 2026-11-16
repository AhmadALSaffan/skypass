plugins {
    alias(libs.plugins.skypass.android.feature)
}

dependencies {
    implementation(projects.core.ui)
    testImplementation(projects.core.testing)
}
