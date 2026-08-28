plugins {
    `kotlin-dsl`
}

group = "dev.jay.betterconnect.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "betterconnect.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "betterconnect.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "betterconnect.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "betterconnect.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "betterconnect.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidScreenshot") {
            id = "betterconnect.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("jvmLibrary") {
            id = "betterconnect.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
