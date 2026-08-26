import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Shared Android + Kotlin config applied by both the application and library plugins.
 *
 * Note AGP 9 dropped the type parameters from [CommonExtension]; it is a plain interface
 * that both ApplicationExtension and LibraryExtension implement.
 */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = BuildConfig.COMPILE_SDK
        defaultConfig.minSdk = BuildConfig.MIN_SDK

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        testOptions.unitTests.isIncludeAndroidResources = true
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(COMMON_COMPILER_ARGS)
        }
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(BuildConfig.JVM_TARGET)) }
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(COMMON_COMPILER_ARGS)
        }
    }
}

/** The `android` extension, whichever concrete type this module uses. */
internal val Project.androidExtension: CommonExtension
    get() = extensions.getByName("android") as CommonExtension

private val COMMON_COMPILER_ARGS = listOf(
    "-Xconsistent-data-class-copy-visibility",
)
