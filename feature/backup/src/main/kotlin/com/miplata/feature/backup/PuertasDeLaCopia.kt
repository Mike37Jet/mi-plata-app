package com.miplata.feature.backup

import java.io.OutputStream

// Lo que este feature necesita del resto de la app, expresado como interfaces.
//
// Las dos cosas dependen de Android -un ContentResolver, la version instalada-
// y el ViewModel no deberia cargar con un Context. Aqui se declara QUE hace
// falta; `:app`, que es quien conoce Android y la base de datos, dice COMO.
// Asi el ViewModel se prueba en la JVM con dobles triviales.

/** Abre para escribir el documento que el usuario eligio en el selector. */
fun interface AbridorDeDestino {
    /**
     * @param uri la que devolvio el selector del sistema, como texto.
     * @return un stream que hay que cerrar. Si el documento ya existia, tiene
     *   que venir **vaciado**: ver la implementacion en `:app`.
     */
    fun abrir(uri: String): OutputStream
}

/** Datos de la instalacion que se anotan en el manifiesto del backup. */
interface InformacionDeLaApp {
    /** Para que el usuario reconozca de que movil salio la copia. */
    val dispositivo: String
    val versionDeLaApp: String

    /** La version del esquema de la base, para migrar al restaurar. */
    val versionDelEsquema: Int
}
