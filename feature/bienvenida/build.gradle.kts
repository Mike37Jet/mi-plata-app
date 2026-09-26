plugins {
    alias(libs.plugins.miplata.android.feature)
}

android {
    namespace = "com.miplata.feature.bienvenida"
}

dependencies {
    testImplementation(testFixtures(projects.core.domain))
}
