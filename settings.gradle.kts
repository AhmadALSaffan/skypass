pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Skypass"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:domain")
include(":core:auth")
include(":core:data")
include(":core:network")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":feature:onboarding")
include(":feature:auth")
include(":feature:home")
include(":feature:results")
include(":feature:booking")
include(":feature:payment")
include(":feature:confirmation")
include(":feature:explore")
include(":feature:hotels")
include(":feature:cars")
include(":feature:trips")
include(":feature:profile")
include(":feature:saved")
