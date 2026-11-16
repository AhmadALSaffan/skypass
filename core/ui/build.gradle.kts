plugins {
    alias(libs.plugins.skypass.android.library.compose)
}

// Screens' shared pieces that need both the design system and the domain (forms, validation text).
dependencies {
    api(projects.core.designsystem)
    api(projects.core.domain)
}
