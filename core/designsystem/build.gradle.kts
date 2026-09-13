plugins {
    alias(libs.plugins.miplata.android.library)
    alias(libs.plugins.miplata.android.compose)
}

android {
    namespace = "com.miplata.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.compose.material.icons.extended)
}
