plugins {
    alias(libs.plugins.miplata.android.library)
    alias(libs.plugins.miplata.android.hilt)
    alias(libs.plugins.miplata.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.miplata.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
