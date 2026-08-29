plugins {
    alias(libs.plugins.betterconnect.android.library)
    alias(libs.plugins.betterconnect.android.hilt)
}

android {
    namespace = "dev.jay.betterconnect.core.ble"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    api(project(":core:link"))

    // BlePermissions/FullPermissions/SpecialAccess need ContextCompat/NotificationManagerCompat.
    implementation(libs.androidx.core)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
}
