import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.dependencies

/**
 * Pure JVM module: no Android SDK, so tests run in milliseconds and can be exhaustive.
 * All logic that can be wrong lives in modules using this plugin.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnit()
            // Empty test tasks are noise, not a signal.
            failOnNoDiscoveredTests.set(false)
            testLogging {
                events("failed")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}
