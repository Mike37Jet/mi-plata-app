plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
}

group = "com.miplata.buildlogic"

// El toolchain de los convention plugins debe coincidir con el que usa Gradle
// para compilar scripts. 17 es el mínimo que exige AGP 8.x.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // `compileOnly` a propósito: estos artefactos ya están en el classpath del
    // build cuando los plugins se aplican. Ponerlos como `implementation`
    // duplicaría AGP en el classpath y rompe el build con errores crípticos.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeCompilerGradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

// build-logic es un build incluido: `./gradlew detekt` desde la raiz NO lo
// alcanza. Sin esto, el codigo mas intrincado del repositorio (los convention
// plugins) seria el unico sin analisis estatico. CI lo invoca con
// `./gradlew -p build-logic detekt`.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("../../config/detekt/detekt.yml"))
    parallel = true
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "miplata.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "miplata.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "miplata.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "miplata.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "miplata.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidFeature") {
            id = "miplata.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "miplata.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
