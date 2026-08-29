plugins {
    alias(libs.plugins.betterconnect.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    api(project(":core:link"))
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
    api(libs.junit)
    // core:testing ships test utilities in its MAIN source set, so these are api deps here.
    api(libs.kotlinx.coroutines.test)
}
