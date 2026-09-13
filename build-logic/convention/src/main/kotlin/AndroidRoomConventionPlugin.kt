import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.miplata.convention.library
import com.miplata.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room con exportación de esquemas activada.
 *
 * `schemaDirectory` es lo importante: hace que cada versión del esquema se
 * escriba a JSON y se commitee al repo, que es lo que permite escribir tests de
 * migración reales. En una app de finanzas, perder datos del usuario no es un
 * bug aceptable (ver docs/02).
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            extensions.configure<KspExtension> {
                arg("room.generateKotlin", "true")
            }

            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("implementation", libs.library("androidx-room-ktx"))
                add("ksp", libs.library("androidx-room-compiler"))
                add("testImplementation", libs.library("androidx-room-testing"))
            }
        }
}
