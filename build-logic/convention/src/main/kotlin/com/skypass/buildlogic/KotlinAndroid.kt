package com.skypass.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Shared Android config for application and library modules. */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureJUnit5()
}

/** Local unit tests run on JUnit 5 (Jupiter). */
internal fun Project.configureJUnit5() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    dependencies {
        add("testImplementation", platform(libs.lib("junit5-bom")))
        add("testImplementation", libs.lib("junit5-jupiter"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testRuntimeOnly", libs.lib("junit5-platform-launcher"))
    }
}
