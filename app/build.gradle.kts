plugins {
    alias(libs.plugins.miplata.android.application)
    alias(libs.plugins.miplata.android.compose)
    alias(libs.plugins.miplata.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.miplata.app"

    defaultConfig {
        applicationId = "com.miplata.app"
    }
}

// :app es el único módulo que conoce :core:data — es donde se arma el grafo de
// Hilt. Los features dependen solo de las interfaces de :core:domain (docs/04).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)

    implementation(projects.feature.resumen)
    implementation(projects.feature.plan)
    implementation(projects.feature.transacciones)
    implementation(projects.feature.cuentas)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
}
