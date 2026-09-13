package com.miplata.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Acceso al catálogo de versiones desde código de plugin.
 *
 * Dentro de un convention plugin no existe el accesor `libs` que genera Gradle
 * para los build scripts, así que hay que resolverlo a mano. Centralizarlo aquí
 * evita repetir esta línea en cada plugin.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

/** Lee un flag booleano de `gradle.properties`, con `false` por defecto. */
internal fun Project.booleanProperty(name: String): Boolean =
    providers.gradleProperty(name).orNull?.toBoolean() ?: false
