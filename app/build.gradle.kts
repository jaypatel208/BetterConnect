import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.betterconnect.android.application)
    alias(libs.plugins.betterconnect.android.compose)
    alias(libs.plugins.betterconnect.android.hilt)
    alias(libs.plugins.secrets)
}

// docs/RELEASING.md: versionCode/versionName are passed in by CI (-PVERSION_CODE/-PVERSION_NAME,
// derived from the commit count) so every release build is traceable back to an exact commit.
// A plain local build with neither property falls back to these, unchanged from before.
val releaseVersionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
val releaseVersionName = findProperty("VERSION_NAME") as String? ?: "0.1"

// Release signing, docs/RELEASING.md: a real key goes in the git-ignored keystore.properties
// (CI writes it from repo secrets); its absence falls back to debug signing so the release
// build type still builds for anyone cloning the repo without release credentials.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "dev.jay.betterconnect"

    defaultConfig {
        applicationId = "dev.jay.betterconnect"
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // AGP 9 turns resValues and buildConfig off by default; the flavours use resValues for the
    // app label, and the debug menu's version-tap reads BuildConfig.VERSION_NAME.
    buildFeatures {
        resValues = true
        buildConfig = true
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
            // The secrets plugin below overrides this per real app variant from
            // secrets.properties/local.defaults.properties, but its variant-API injection does
            // not reach the unitTest component's own manifest merge - this flavour-level
            // default is what keeps that one resolvable too.
            manifestPlaceholders["MAPS_API_KEY"] = " "
        }
    }

    buildTypes {
        release {
            optimization { enable = false }
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

// docs/SETUP.md: a real key goes in the git-ignored secrets.properties; local.defaults.properties
// is the committed empty fallback so a keyless clone (and CI) still resolves MAPS_API_KEY.
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
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

    "fullImplementation"(project(":feature:onboarding"))
    "fullImplementation"(project(":feature:devices"))
    "fullImplementation"(project(":feature:navigation"))
    "fullImplementation"(project(":feature:debug"))
    "fullImplementation"(libs.androidx.navigation3.runtime)
    "fullImplementation"(libs.androidx.navigation3.ui)
    "fullImplementation"(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.windowsize)
}
