pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Descarga el JDK del toolchain si la máquina no lo tiene. Sin esto, el build
// depende de qué JDK haya instalado cada quien: funciona en tu portátil y falla
// en CI (o al revés). Con esto, `javaToolchain` del catálogo es la única fuente
// de verdad sobre con qué Java se compila.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    // Ningún módulo puede declarar sus propios repositorios: se falla el build si
    // lo intenta. Evita que una dependencia entre por una fuente no auditada.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mi-plata-app"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")

// Los módulos :feature:* se añaden en la Etapa 3 (ver docs/07-roadmap.md).
