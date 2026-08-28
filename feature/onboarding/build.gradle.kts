plugins {
    alias(libs.plugins.betterconnect.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jay.betterconnect.feature.onboarding"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))

    // full is Nav3 from the start; onboarding is the entry point that introduces it.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
