package com.miplata.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Utilidades para fabricar backups rotos a proposito.
//
// Se construyen manipulando el ZIP de verdad, no con cadenas a mano: asi los
// tests de corrupcion comprueban lo que de verdad le llegaria a la app si un
// archivo se estropea en la nube o al copiarlo.

internal fun zipCon(piezas: Map<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
        .also { salida ->
            ZipOutputStream(salida).use { zip ->
                piezas.forEach { (nombre, contenido) ->
                    zip.putNextEntry(ZipEntry(nombre))
                    zip.write(contenido)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

internal fun piezasDe(backup: ByteArray): MutableMap<String, ByteArray> =
    mutableMapOf<String, ByteArray>().also { piezas ->
        ZipInputStream(ByteArrayInputStream(backup)).use { zip ->
            var entrada = zip.nextEntry
            while (entrada != null) {
                piezas[entrada.name] = zip.readBytes()
                zip.closeEntry()
                entrada = zip.nextEntry
            }
        }
    }

/** Cambia un byte de `data.json`, como haria una copia a medias. */
internal fun corromperLosDatos(backup: ByteArray): ByteArray {
    val piezas = piezasDe(backup)
    val datos = piezas.getValue(Piezas.DATOS)
    // Se toca un caracter del contenido, no la estructura: el ZIP sigue siendo
    // valido y solo el checksum puede delatarlo.
    piezas[Piezas.DATOS] = datos.decodeToString().replace("EUR", "USD").toByteArray()
    return zipCon(piezas)
}

/** Reescribe el manifiesto con otra version de formato. */
internal fun conVersionDeFormato(
    backup: ByteArray,
    version: Int,
): ByteArray {
    val piezas = piezasDe(backup)
    val manifiesto = piezas.getValue(Piezas.MANIFIESTO).decodeToString()
    piezas[Piezas.MANIFIESTO] =
        manifiesto
            .replace("\"versionDelFormato\":$VERSION_DEL_FORMATO", "\"versionDelFormato\":$version")
            .toByteArray()
    return zipCon(piezas)
}
