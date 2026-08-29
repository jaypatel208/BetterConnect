plugins {
    alias(libs.plugins.betterconnect.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jay.betterconnect.feature.devices"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
