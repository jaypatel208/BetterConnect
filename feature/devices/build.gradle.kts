plugins {
    alias(libs.plugins.betterconnect.android.feature)
}

android {
    namespace = "dev.jay.betterconnect.feature.devices"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ble"))

    // rememberLauncherForActivityResult - the system "turn on Bluetooth?" dialog.
    implementation(libs.androidx.activity.compose)
}
