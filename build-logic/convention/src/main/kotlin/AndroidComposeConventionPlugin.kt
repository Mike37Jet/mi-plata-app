import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import com.miplata.convention.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Añade Compose a un módulo Android, sea aplicación o librería.
 *
 * Se aplica ENCIMA de `miplata.android.library` / `miplata.android.application`,
 * no en su lugar: no todos los módulos tienen UI (`:core:data` no debe arrastrar
 * Compose).
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // AGP expone la configuración de Android en dos extensiones distintas
            // según el tipo de módulo, pero ambas heredan de CommonExtension, que es
            // lo que configureAndroidCompose necesita.
            val extension =
                extensions.findByType(ApplicationExtension::class.java)
                    ?: extensions.findByType(LibraryExtension::class.java)
                    ?: error(
                        "miplata.android.compose requiere que ya esté aplicado " +
                            "miplata.android.application o miplata.android.library",
                    )

            configureAndroidCompose(extension)
        }
}
