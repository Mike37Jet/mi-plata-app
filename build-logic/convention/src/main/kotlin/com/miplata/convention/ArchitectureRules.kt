package com.miplata.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

// Reglas de arquitectura que se verifican solas en cada build.
//
// Son las reglas de docs/01 y docs/04. La decision de ponerlas aqui, y no en un
// test o en una tarea aparte, es deliberada: una regla que hay que acordarse de
// ejecutar se rompe en tres semanas. Estas fallan durante la configuracion del
// build, asi que no hay forma de saltarselas sin verlo.

/**
 * `:core:domain` debe ser Kotlin/JVM puro.
 *
 * El compilador ya impide importar `android.*` alli (no esta en el classpath),
 * pero eso solo se nota cuando alguien lo intenta. Esto ataca la causa: si
 * alguien aplica un plugin de Android al modulo para "arreglarlo", el build
 * falla en el acto y con un mensaje que explica por que.
 */
internal fun Project.forbidAndroidPlugins() {
    listOf("com.android.library", "com.android.application").forEach { pluginId ->
        pluginManager.withPlugin(pluginId) {
            error(
                """
                |
                |  Regla de arquitectura violada en $path
                |
                |  Se aplico el plugin '$pluginId' a un modulo que debe ser
                |  Kotlin/JVM puro. Eso mete Android en su classpath y rompe la
                |  regla de dependencia de docs/01-arquitectura.md.
                |
                |  Si necesitas Android aqui, casi seguro el codigo va en otra
                |  capa: la logica de negocio no depende del framework.
                |
                """.trimMargin(),
            )
        }
    }
}

/**
 * Un `:feature:*` depende de `:core:domain` y `:core:designsystem`, nunca de
 * `:core:data` ni de otro feature (docs/04-modularizacion.md).
 *
 * Depender de `:core:data` saltaria la capa de dominio y ataria la UI a Room.
 * Depender de otro feature crea acoplamiento entre pantallas: lo compartido
 * baja a `:core`.
 */
internal fun Project.enforceFeatureDependencyRules() {
    afterEvaluate {
        val violations =
            configurations
                // Solo las configuraciones DECLARABLES (implementation, api...).
                // Las resolubles (debugUnitTestCompileClasspath y compania)
                // heredan las mismas dependencias, y recorrerlas reportaria la
                // misma violacion cuatro veces.
                .filter { it.isCanBeDeclared }
                .flatMap { configuration -> configuration.dependencies }
                .mapNotNull { dependency ->
                    val dependencyPath = (dependency as? ProjectDependency)?.path ?: return@mapNotNull null
                    val reason =
                        when {
                            dependencyPath == ":core:data" ->
                                "saltaria la capa de dominio y ataria la UI a Room"

                            dependencyPath.startsWith(":feature:") && dependencyPath != path ->
                                "acopla dos pantallas; lo que compartan debe bajar a :core"

                            else -> return@mapNotNull null
                        }
                    "  $path -> $dependencyPath: $reason"
                }.distinct()

        if (violations.isNotEmpty()) {
            error(
                """
                |
                |  Regla de arquitectura violada (docs/04-modularizacion.md):
                |
                |${violations.joinToString("\n")}
                |
                """.trimMargin(),
            )
        }
    }
}
