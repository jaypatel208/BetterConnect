plugins {
    alias(libs.plugins.betterconnect.jvm.library)
}

dependencies {
    api(project(":core:model"))
}
