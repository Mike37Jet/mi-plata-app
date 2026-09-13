import com.miplata.convention.configureDetekt
import com.miplata.convention.configureKotlinJvm
import com.miplata.convention.library
import com.miplata.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Librería Kotlin/JVM pura. Se usa en `:core:domain`.
 *
 * NO aplica ningún plugin de Android. Esa ausencia es la que hace cumplir la
 * regla de dependencia de docs/01: en un módulo con este plugin, `Context`,
 * `Room` o `Compose` simplemente no existen en el classpath. Romper la
 * arquitectura deja de ser una cuestión de disciplina y pasa a ser un error de
 * compilación.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("io.gitlab.arturbosch.detekt")
            }

            configureKotlinJvm()
            configureDetekt()

            dependencies {
                add("implementation", libs.library("kotlinx-coroutines-core"))
                add("implementation", libs.library("kotlinx-datetime"))

                add("testImplementation", libs.library("junit-jupiter"))
                add("testRuntimeOnly", libs.library("junit-platform-launcher"))
                add("testImplementation", libs.library("kotest-assertions-core"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
        }
}
