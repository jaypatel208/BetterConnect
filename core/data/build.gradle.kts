plugins {
    alias(libs.plugins.betterconnect.android.library)
    alias(libs.plugins.betterconnect.android.hilt)
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
}

dependencies {
    testImplementation(project(":core:testing"))
}
