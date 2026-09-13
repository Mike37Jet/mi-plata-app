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

// --- Hooks de git ---
//
// Se instalan solos en el primer build de cada clon. Sin esto, un clon nuevo se
// queda sin hooks en silencio hasta que alguien lea la documentacion, y una
// puerta de calidad que hay que acordarse de activar no es una puerta.
//
// Se usa `providers.exec` (compatible con el cache de configuracion) y se
// comprueba primero el valor actual, asi que en la practica esto no se ejecuta
// mas que una vez por clon.
installGitHooks()

fun installGitHooks() {
    val hooksPath = ".githooks"

    // En CI los hooks no pintan nada: la verificacion la hace el workflow.
    if (providers.environmentVariable("CI").isPresent) return
    if (!File(settingsDir, ".git").exists()) return

    val configured =
        providers
            .exec {
                workingDir = settingsDir
                commandLine("git", "config", "--get", "core.hooksPath")
                isIgnoreExitValue = true
            }.standardOutput
            .asText
            .get()
            .trim()

    if (configured == hooksPath) return

    providers
        .exec {
            workingDir = settingsDir
            commandLine("git", "config", "core.hooksPath", hooksPath)
        }.result
        .get()

    logger.lifecycle("Hooks de git instalados en $hooksPath/ (pre-commit, commit-msg, pre-push).")
}

rootProject.name = "mi-plata-app"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")

// Los módulos :feature:* se añaden en la Etapa 3 (ver docs/07-roadmap.md).
