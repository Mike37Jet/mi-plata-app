import com.android.build.gradle.LibraryExtension
import com.miplata.convention.configureDetekt
import com.miplata.convention.configureKotlinAndroid
import com.miplata.convention.library
import com.miplata.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Convention plugin de cualquier librería Android (`:core:*`, `:feature:*`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("io.gitlab.arturbosch.detekt")
            }

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // Las librerías no necesitan targetSdk; AGP lo avisa si se pone.
                    consumerProguardFiles("consumer-rules.pro")
                }

                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                        isReturnDefaultValues = true
                    }
                }
            }

            configureDetekt()

            dependencies {
                add("implementation", libs.library("kotlinx-coroutines-android"))
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
                add("testImplementation", libs.library("kotest-assertions-core"))
            }
        }
}
