plugins {
    alias(libs.plugins.betterconnect.android.feature)
}

android {
    namespace = "dev.jay.betterconnect.feature.onboarding"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))
}
