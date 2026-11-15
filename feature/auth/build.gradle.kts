plugins {
    alias(libs.plugins.skypass.android.feature)
}

dependencies {
    implementation(projects.core.auth)
    testImplementation(projects.core.testing)
}
