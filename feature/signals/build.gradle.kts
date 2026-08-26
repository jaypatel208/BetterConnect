plugins {
    alias(libs.plugins.betterconnect.android.feature)
}

android {
    namespace = "dev.jay.betterconnect.feature.signals"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:link"))
}
