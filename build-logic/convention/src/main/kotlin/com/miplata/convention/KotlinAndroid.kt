package com.miplata.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

/**
 * Configuración de Android + Kotlin compartida por TODOS los módulos Android
 * (aplicación y librerías).
 *
 * Esta función es la razón de ser de build-logic: subir el `compileSdk`, cambiar
 * el toolchain de Java o añadir un flag del compilador se hace aquí, una vez, y
 * aplica a los 5 módulos (y a los 15 que vengan).
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    val javaVersionString = libs.version("javaToolchain")
    val javaVersion = JavaVersion.toVersion(javaVersionString)
    val isCi = providers.environmentVariable("CI").isPresent

    commonExtension.apply {
        compileSdk = libs.version("compileSdk").toInt()

        defaultConfig {
            minSdk = libs.version("minSdk").toInt()
        }

        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
            // Habilita java.time y otras APIs modernas en minSdk 26.
            isCoreLibraryDesugaringEnabled = true
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                excludes += "/META-INF/LICENSE*"
            }
        }

        lint {
            // En CI cualquier warning de Lint rompe el build; en local solo avisa.
            warningsAsErrors = isCi
            abortOnError = true
            checkDependencies = true
            xmlReport = true
            htmlReport = true
            // Solo se usa la baseline si ya existe. Si se declarara siempre,
            // AGP la generaría y fallaría el primer build con "baseline created",
            // que es un arranque en falso muy confuso.
            file("lint-baseline.xml").takeIf(File::exists)?.let { baseline = it }
        }
    }

    configureKotlinJvmTarget(javaVersionString)

    dependencies {
        add("coreLibraryDesugaring", libs.library("desugar-jdk-libs"))
    }
}

/** Configuración para módulos Kotlin/JVM puros, como `:core:domain`. */
internal fun Project.configureKotlinJvm() {
    val javaVersionString = libs.version("javaToolchain")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersionString))
    }

    configureKotlinJvmTarget(javaVersionString)
}

private fun Project.configureKotlinJvmTarget(javaVersionString: String) {
    val target = JvmTarget.fromTarget(javaVersionString)
    // Los warnings son errores en CI, pero no en local: un warning de
    // deprecación no debe impedirte iterar mientras escribes código. La red de
    // seguridad está en el PR, que es donde importa (docs/06).
    val strict = providers.environmentVariable("CI").isPresent

    // El bloque `compilerOptions` vive en una extensión distinta según el plugin
    // de Kotlin que esté aplicado, así que se configura la que exista.
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.apply {
        compilerOptions { applyCommonCompilerOptions(target, strict) }
    }
    extensions.findByType(KotlinJvmProjectExtension::class.java)?.apply {
        compilerOptions { applyCommonCompilerOptions(target, strict) }
    }
}

private fun org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.applyCommonCompilerOptions(
    target: JvmTarget,
    allWarningsAreErrors: Boolean,
) {
    if (this is org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions) {
        jvmTarget.set(target)
    }
    allWarningsAsErrors.set(allWarningsAreErrors)
}
