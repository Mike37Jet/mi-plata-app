/**
 * Genera el Baseline Profile de la app y mide su arranque (roadmap 7.3).
 *
 * Es un modulo de test contra :app, no parte de la app: sus pruebas corren en un
 * dispositivo, instalan la variante de release y la recorren con UiAutomator.
 * No entra en el APK.
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.miplata.baselineprofile"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        // BaselineProfileRule necesita Android 9 (API 28).
        minSdk = 28
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Los numeros de un emulador no son los de un telefono, y Macrobenchmark
        // se niega a medir en uno salvo que se le diga. Se le dice, y se anota
        // al leer los resultados (docs/07).
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(
        libs.versions.javaToolchain
            .get()
            .toInt(),
    )
}

baselineProfile {
    // El dispositivo o emulador conectado; sin dispositivos gestionados por
    // Gradle, que descargarian una imagen de sistema entera.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
