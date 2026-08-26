plugins {
    alias(libs.plugins.betterconnect.android.library)
    alias(libs.plugins.betterconnect.android.compose)
}

android {
    namespace = "dev.jay.betterconnect.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    implementation(libs.kotlinx.collections.immutable)
}
