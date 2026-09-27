plugins {
    alias(libs.plugins.miplata.android.feature)
}

android {
    namespace = "com.miplata.feature.ajustes"
}

dependencies {
    testImplementation(testFixtures(projects.core.domain))
}
