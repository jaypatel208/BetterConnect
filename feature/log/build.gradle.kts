plugins {
    alias(libs.plugins.betterconnect.android.feature)
}

android {
    namespace = "dev.jay.betterconnect.feature.log"
}

dependencies {
    implementation(project(":core:data"))
}
