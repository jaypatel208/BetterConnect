plugins {
    alias(libs.plugins.betterconnect.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:protocol"))
    api(project(":core:link"))

    testImplementation(project(":core:testing"))
}
