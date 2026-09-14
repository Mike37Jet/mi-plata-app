plugins {
    alias(libs.plugins.miplata.android.feature)
}

android {
    namespace = "com.miplata.feature.resumen"
}

dependencies {
    // Los fakes de repositorio los publica :core:domain como test fixtures, asi
    // que este modulo prueba contra el MISMO contrato que la implementacion real
    // de Room, no contra una copia que se desincroniza (docs/04).
    testImplementation(testFixtures(projects.core.domain))
}
