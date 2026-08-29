plugins {
    alias(libs.plugins.betterconnect.android.library)
    alias(libs.plugins.betterconnect.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jay.betterconnect.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    api(project(":core:link"))
    api(project(":core:domain"))
    api(project(":core:ble"))
    api(project(":core:testing"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}

dependencies {
    testImplementation(project(":core:testing"))
}
