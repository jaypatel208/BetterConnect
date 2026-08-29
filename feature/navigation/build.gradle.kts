plugins {
    alias(libs.plugins.betterconnect.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jay.betterconnect.feature.navigation"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
