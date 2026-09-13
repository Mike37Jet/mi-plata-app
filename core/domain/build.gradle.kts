plugins {
    alias(libs.plugins.miplata.jvm.library)
}

// Sin `android { }`, sin namespace, sin nada de AndroidX.
//
// Si algún día este archivo necesita el plugin de Android, algo se rompió en la
// arquitectura: revisar docs/01 antes de añadirlo.
