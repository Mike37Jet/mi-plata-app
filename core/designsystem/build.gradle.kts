plugins {
    alias(libs.plugins.miplata.android.library)
    alias(libs.plugins.miplata.android.compose)
}

android {
    namespace = "com.miplata.core.designsystem"
}

dependencies {
    // El design system conoce el dominio para poder dar formato a un Money.
    // La flecha apunta hacia dentro, como debe (docs/01).
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.compose.material.icons.extended)
}
