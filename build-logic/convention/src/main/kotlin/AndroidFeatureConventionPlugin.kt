import com.miplata.convention.enforceFeatureDependencyRules
import com.miplata.convention.library
import com.miplata.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Todo lo que un módulo `:feature:*` necesita, en un solo plugin.
 *
 * Aquí se codifican las reglas de dependencia de docs/04: un feature depende de
 * `:core:domain` y de `:core:designsystem`, NUNCA de `:core:data` ni de otro
 * feature. Al centralizarlo, es imposible que un feature se salte la regla por
 * descuido — tendría que añadir la dependencia a mano y eso se ve en el PR.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("miplata.android.library")
                apply("miplata.android.compose")
                apply("miplata.android.hilt")
                // Las rutas de navegacion son objetos @Serializable: renombrar
                // una pantalla pasa a ser un refactor del IDE en vez de un
                // find-and-replace sobre strings que se rompe en silencio.
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            dependencies {
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:common"))

                add("implementation", libs.library("androidx-lifecycle-runtime-ktx"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("hilt-navigation-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("androidTestImplementation", libs.library("androidx-test-junit"))
            }

            enforceFeatureDependencyRules()
        }
}
