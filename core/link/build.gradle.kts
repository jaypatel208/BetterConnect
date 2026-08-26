plugins {
    alias(libs.plugins.betterconnect.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    api(libs.kotlinx.coroutines.core)
}

dependencies {
    testImplementation(project(":core:testing"))
}
