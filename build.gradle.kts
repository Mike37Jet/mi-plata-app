// Build script raíz.
//
// Deliberadamente casi vacío: toda la configuración compartida vive en los
// convention plugins de `build-logic/` (ver docs/02, sección "Build: convention
// plugins"). Aquí solo se declaran los plugins con `apply false` para fijar sus
// versiones en el classpath, más la configuración de Spotless que sí aplica a
// todo el repositorio por igual.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.md", "**/.gitignore", "**/*.yml", "**/*.yaml")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

/**
 * Ejecuta los tests de **todos** los modulos Kotlin/JVM puros.
 *
 * Existe porque `testDebugUnitTest` solo alcanza a los modulos Android: un
 * modulo JVM como `:core:domain` o `:core:backup` tiene la tarea `test` a
 * secas, y si el CI los enumera a mano, el dia que se anada el siguiente nadie
 * se acordara. Sus tests quedarian sin ejecutar y el build seguiria en verde,
 * que es la peor forma de perder cobertura: en silencio.
 *
 * Se descubren por el plugin que aplican, asi que un modulo JVM nuevo entra
 * aqui solo.
 */
tasks.register("testsDeJvm") {
    group = "verification"
    description = "Tests de los modulos Kotlin/JVM puros (los que no cubre testDebugUnitTest)."

    dependsOn(
        subprojects
            .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.jvm") }
            .filterNot { it.plugins.hasPlugin("com.android.library") }
            .map { "${it.path}:test" },
    )
}
