plugins {
    alias(libs.plugins.miplata.android.library)
    alias(libs.plugins.miplata.android.hilt)
}

android {
    namespace = "com.miplata.core.common"
}

dependencies {
    implementation(projects.core.domain)
}
