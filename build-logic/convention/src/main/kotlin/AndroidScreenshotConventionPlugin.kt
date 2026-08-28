import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Roborazzi screenshot tests, on the JVM via Robolectric.
 *
 * These run in `./gradlew test` like any other unit test, so a component regressing in dark mode
 * or at a large font scale fails the build instead of waiting for someone to open that screen.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")

        androidExtension.testOptions.unitTests.isIncludeAndroidResources = true

        // Baselines are source, not build output. The default lands them in build/outputs,
        // which is gitignored - so nothing would ever be committed and CI would have no
        // reference image to compare against.
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }

        dependencies {
            add("testImplementation", libs.findLibrary("roborazzi").get())
            add("testImplementation", libs.findLibrary("roborazzi.compose").get())
            add("testImplementation", libs.findLibrary("roborazzi.junit.rule").get())
            add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("testImplementation", libs.findLibrary("androidx.compose.ui.test.junit4").get())
            add("testImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
        }
    }
}
