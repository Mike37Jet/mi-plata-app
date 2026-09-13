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
