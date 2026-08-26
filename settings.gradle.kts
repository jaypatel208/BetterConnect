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

rootProject.name = "Better Connect"

include(":app")

include(":core:model")
include(":core:protocol")
include(":core:link")
include(":core:domain")
include(":core:ble")
include(":core:data")
include(":core:designsystem")
include(":feature:connect")
include(":feature:signals")
include(":feature:log")
include(":core:testing")
