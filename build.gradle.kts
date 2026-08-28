plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

// Coverage is aggregated at the root: `./gradlew koverHtmlReport`.
//
// No threshold is set. One will be agreed once there is a baseline to argue about - a number
// invented up front only teaches people to write tests that assert nothing.
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

dependencies {
    subprojects.forEach { kover(it) }
}
