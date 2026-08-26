plugins {
    alias(libs.plugins.betterconnect.android.feature)
}

android {
    namespace = "dev.jay.betterconnect.feature.connect"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))
    implementation(project(":core:link"))
}
