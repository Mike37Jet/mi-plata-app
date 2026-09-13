package com.miplata.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import java.io.File

/** Detekt con la misma configuración en todos los módulos. */
internal fun Project.configureDetekt() {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // Igual que con Lint: la baseline solo cuenta si ya existe. Se genera
        // a propósito con `./gradlew detektBaseline` cuando se decide aceptar
        // deuda conocida, nunca por accidente.
        file("detekt-baseline.xml").takeIf(File::exists)?.let { baseline = it }
        parallel = true
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = libs.version("javaToolchain")
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}
