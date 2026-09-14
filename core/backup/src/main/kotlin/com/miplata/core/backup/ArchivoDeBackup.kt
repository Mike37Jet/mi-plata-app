package com.miplata.core.backup

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Lee y escribe el archivo de backup.
 *
 * El archivo es un ZIP con `manifest.json` y `data.json` dentro (docs/05). Un
 * ZIP y no un JSON suelto porque deja sitio para lo que vendra -adjuntos, fotos
 * de recibos- sin cambiar el formato, y porque comprime bien un JSON que es casi
 * todo texto repetido.
 *
 * Trabaja con `InputStream`/`OutputStream` y no con archivos ni con `Uri`: quien
 * le da el stream es el que sabe de donde sale. Asi esta clase no necesita
 * Android y se prueba entera en la JVM, que para la pieza que decide si los
 * datos del usuario sobreviven a un cambio de movil no es un detalle menor.
 */
class ArchivoDeBackup(
    private val json: Json = JSON,
) {
    /**
     * Escribe el backup en [destino].
     *
     * El manifiesto va **primero** dentro del ZIP a proposito: al leer se puede
     * comprobar la version del formato sin descomprimir los datos, que es lo que
     * permite rechazar un archivo demasiado nuevo antes de tocar nada.
     */
    fun escribir(
        contenido: ContenidoDelBackup,
        destino: OutputStream,
    ) {
        val datos = json.encodeToString(DatosDelBackup.serializer(), contenido.datos).toByteArray()

        val manifiesto =
            Manifiesto(
                versionDelEsquema = contenido.versionDelEsquema,
                versionDeLaApp = contenido.versionDeLaApp,
                creadoEnMillis = contenido.creadoEnMillis,
                dispositivo = contenido.dispositivo,
                checksum = sha256De(datos),
                contenido = recuentoDe(contenido.datos),
            )

        ZipOutputStream(destino).use { zip ->
            zip.escribir(Piezas.MANIFIESTO, json.encodeToString(Manifiesto.serializer(), manifiesto).toByteArray())
            zip.escribir(Piezas.DATOS, datos)
        }
    }

    /**
     * Lee el backup de [origen], comprobando que este entero.
     *
     * Se valida en este orden y no en otro: primero que el archivo tenga las dos
     * piezas, luego que la version del formato sea legible, y solo despues el
     * checksum. Asi un archivo de una version futura da el mensaje util
     * -"actualiza la app"- en vez de un "checksum incorrecto" que no le dice
     * nada a nadie.
     */
    fun leer(origen: InputStream): BackupLeido {
        val piezas = descomprimir(origen)
        val manifiesto = manifiestoDe(piezas)

        exigirFormatoLegible(manifiesto)
        val datos = datosDe(piezas)
        exigirChecksumCorrecto(datos, manifiesto)

        return BackupLeido(manifiesto, decodificar(datos))
    }

    private fun exigirFormatoLegible(manifiesto: Manifiesto) {
        if (manifiesto.versionDelFormato > VERSION_DEL_FORMATO) {
            throw BackupInvalido(
                "El backup es de un formato mas nuevo (v${manifiesto.versionDelFormato}) " +
                    "que esta app (v$VERSION_DEL_FORMATO). Actualiza la app para restaurarlo.",
            )
        }
    }

    private fun datosDe(piezas: Map<String, ByteArray>): ByteArray =
        piezas[Piezas.DATOS] ?: throw BackupInvalido("El backup no contiene ${Piezas.DATOS}")

    private fun exigirChecksumCorrecto(
        datos: ByteArray,
        manifiesto: Manifiesto,
    ) {
        if (sha256De(datos) != manifiesto.checksum) {
            throw BackupInvalido(
                "El backup esta corrupto: el contenido no coincide con su checksum. " +
                    "No se ha modificado nada.",
            )
        }
    }

    /**
     * Lee **solo** el manifiesto.
     *
     * Sirve para enseñar el resumen previo -"4 cuentas, 312 movimientos, del 15
     * de marzo"- y, mas adelante, para saber si hay que pedir la frase de
     * descifrado antes de pedirla (docs/05).
     */
    fun leerManifiesto(origen: InputStream): Manifiesto = manifiestoDe(descomprimir(origen))

    private fun manifiestoDe(piezas: Map<String, ByteArray>): Manifiesto {
        val crudo =
            piezas[Piezas.MANIFIESTO]
                ?: throw BackupInvalido(
                    "Esto no parece un backup de mi-plata: no contiene ${Piezas.MANIFIESTO}",
                )

        return try {
            json.decodeFromString(Manifiesto.serializer(), crudo.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw BackupInvalido("El manifiesto del backup no se puede leer", e)
        }
    }

    private fun decodificar(datos: ByteArray): DatosDelBackup =
        try {
            json.decodeFromString(DatosDelBackup.serializer(), datos.decodeToString())
        } catch (e: IllegalArgumentException) {
            throw BackupInvalido("Los datos del backup no se pueden leer", e)
        }

    private fun descomprimir(origen: InputStream): Map<String, ByteArray> =
        try {
            buildMap {
                ZipInputStream(origen).use { zip ->
                    var entrada = zip.nextEntry
                    while (entrada != null) {
                        if (!entrada.isDirectory) put(entrada.name, zip.readBytes())
                        zip.closeEntry()
                        entrada = zip.nextEntry
                    }
                }
            }
        } catch (e: java.util.zip.ZipException) {
            // Solo salta con un ZIP roto por dentro. Un archivo que no es un ZIP
            // -una foto, un PDF- no da error aqui: simplemente no tiene entradas,
            // y se rechaza mas arriba al no encontrar el manifiesto.
            throw BackupInvalido("El archivo esta danado y no se puede abrir", e)
        }

    private fun recuentoDe(datos: DatosDelBackup) =
        Recuento(
            cuentas = datos.cuentas.size,
            categorias = datos.categorias.size,
            transacciones = datos.transacciones.size,
            planes = datos.planes.size,
        )

    private companion object {
        val JSON =
            Json {
                // Para que un backup viejo siga leyendose cuando el formato gane
                // campos nuevos: lo que no se conoce se ignora en vez de fallar.
                ignoreUnknownKeys = true
                // Los valores por defecto SI se escriben. Ocupan mas, pero el
                // archivo se puede leer a ojo cuando algo va mal, y un backup es
                // justo el sitio donde eso hace falta.
                encodeDefaults = true
                prettyPrint = false
            }
    }
}

/** Lo que hay que darle al escritor para generar un backup. */
data class ContenidoDelBackup(
    val datos: DatosDelBackup,
    val versionDelEsquema: Int,
    val versionDeLaApp: String,
    val creadoEnMillis: Long,
    val dispositivo: String,
)

/** Un backup ya validado. */
data class BackupLeido(
    val manifiesto: Manifiesto,
    val datos: DatosDelBackup,
)

private fun ZipOutputStream.escribir(
    nombre: String,
    contenido: ByteArray,
) {
    putNextEntry(ZipEntry(nombre))
    write(contenido)
    closeEntry()
}

private fun sha256De(datos: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(datos)
        .joinToString("") { "%02x".format(it) }

/** Escribe el backup y devuelve los bytes, para quien no tenga un stream a mano. */
fun ArchivoDeBackup.escribirABytes(contenido: ContenidoDelBackup): ByteArray =
    ByteArrayOutputStream().also { escribir(contenido, it) }.toByteArray()
