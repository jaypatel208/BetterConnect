plugins {
    alias(libs.plugins.betterconnect.android.application)
    alias(libs.plugins.betterconnect.android.compose)
    alias(libs.plugins.betterconnect.android.hilt)
}

android {
    namespace = "dev.jay.betterconnect"

    defaultConfig {
        applicationId = "dev.jay.betterconnect"
        versionCode = 1
        versionName = "0.1"
    }

    // AGP 9 turns resValues off by default; the flavours use it to set the app label.
    buildFeatures {
        resValues = true
    }

    flavorDimensions += "mode"
    productFlavors {
        create("diag") {
            dimension = "mode"
            // Must install alongside the official Bajaj app and a future full build.
            applicationIdSuffix = ".diag"
            versionNameSuffix = "-diag"
            resValue("string", "app_name", "BC Diag")
        }
        create("full") {
            dimension = "mode"
            resValue("string", "app_name", "Better Connect")
        }
    }

    buildTypes {
        release {
            optimization { enable = false }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:ble"))
    implementation(project(":core:link"))
    implementation(project(":core:domain"))
    implementation(project(":core:protocol"))

    // Diagnostic surfaces exist only in the diag variant; the full build does not ship them.
    "diagImplementation"(project(":feature:connect"))
    "diagImplementation"(project(":feature:signals"))
    "diagImplementation"(project(":feature:log"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.windowsize)
}
