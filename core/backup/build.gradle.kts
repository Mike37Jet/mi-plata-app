plugins {
    alias(libs.plugins.miplata.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Kotlin/JVM puro, igual que `:core:domain`, y por el mismo motivo.
//
// El formato del backup no necesita Android para NADA: serializar, comprimir,
// calcular un SHA-256 y validar son operaciones de la biblioteca estandar. Lo
// unico que Android aporta es el `OutputStream` a donde escribir, y eso lo pasa
// quien llame (docs/05).
//
// La ventaja practica es grande: la parte mas delicada de la app -la que decide
// si los datos del usuario sobreviven a un cambio de movil- se prueba entera en
// milisegundos, sin emulador.

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(testFixtures(projects.core.domain))
}
