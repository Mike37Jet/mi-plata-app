package com.miplata.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Configuración de Compose compartida.
 *
 * Desde Kotlin 2.0 el compilador de Compose se versiona con Kotlin y se aplica
 * como plugin (`org.jetbrains.kotlin.plugin.compose`); ya no hay que casar a
 * mano `kotlinCompilerExtensionVersion` con la versión de Kotlin.
 */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        buildFeatures {
            compose = true
        }
    }

    dependencies {
        val bom = libs.library("androidx-compose-bom")
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))

        add("implementation", libs.library("androidx-compose-ui"))
        add("implementation", libs.library("androidx-compose-ui-graphics"))
        add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        add("implementation", libs.library("androidx-compose-material3"))
        add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
        add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))

        add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))

        add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Informes de estabilidad del compilador: detectan composables que
        // recomponen de más. Se activan a demanda desde gradle.properties para
        // no ralentizar los builds del día a día.
        if (booleanProperty("miplata.enableComposeCompilerMetrics")) {
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
        if (booleanProperty("miplata.enableComposeCompilerReports")) {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        }
    }
}
