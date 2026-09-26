plugins {
    alias(libs.plugins.miplata.android.feature)
}

android {
    namespace = "com.miplata.feature.backup"
}

dependencies {
    // El formato y el cifrado del archivo. Es un modulo de :core, no de datos
    // ni de otro feature, asi que las reglas de docs/04 lo permiten.
    implementation(projects.core.backup)

    // `rememberLauncherForActivityResult`: la forma de abrir el selector de
    // archivos del sistema desde Compose.
    implementation(libs.androidx.activity.compose)

    // El recordatorio periodico de copia (docs/05).
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(testFixtures(projects.core.domain))
}
