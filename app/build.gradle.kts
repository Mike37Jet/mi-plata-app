plugins {
    alias(libs.plugins.miplata.android.application)
    alias(libs.plugins.miplata.android.compose)
    alias(libs.plugins.miplata.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.miplata.app"

    defaultConfig {
        applicationId = "com.miplata.app"
    }

    // Las variantes con las que se genera el Baseline Profile y se mide el
    // arranque se firman con la clave de depuracion. El release se firma con un
    // keystore local que puede no estar (docs/06), y un APK sin firmar no se
    // puede instalar para medirlo.
    buildTypes.configureEach {
        if (name == "benchmarkRelease" || name == "nonMinifiedRelease") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

baselineProfile {
    // El perfil generado se commitea (src/release/generated/baselineProfiles):
    // generarlo necesita un dispositivo, y CI no tiene. No se regenera en cada
    // build de release.
    automaticGenerationDuringBuild = false
}

// :app es el único módulo que conoce :core:data — es donde se arma el grafo de
// Hilt. Los features dependen solo de las interfaces de :core:domain (docs/04).
dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.backup)

    implementation(projects.feature.resumen)
    implementation(projects.feature.plan)
    implementation(projects.feature.transacciones)
    implementation(projects.feature.cuentas)
    implementation(projects.feature.backup)
    implementation(projects.feature.bienvenida)
    implementation(projects.feature.ajustes)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Instala el Baseline Profile al instalar el APK, sin esperar a que Play
    // lo haga: esta app no pasa por Play (docs/07).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
}
