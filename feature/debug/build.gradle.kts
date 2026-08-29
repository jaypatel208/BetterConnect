plugins {
    alias(libs.plugins.betterconnect.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jay.betterconnect.feature.debug"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)
}
