plugins {
    alias(libs.plugins.skypass.android.library)
    alias(libs.plugins.skypass.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
}
