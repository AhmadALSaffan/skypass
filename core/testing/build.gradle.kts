plugins {
    alias(libs.plugins.skypass.jvm.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit5.bom))
    api(libs.junit5.jupiter)
    api(libs.turbine)
}
