package com.miplata.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
 * El archivo es un ZIP con `manifest.json` en claro y `data.enc` cifrado con
 * la frase del usuario (docs/05). Un
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
    private val cifrador: CifradorDeBackup = CifradorDeBackup(),
    private val json: Json = JSON,
    private val migrador: MigradorDeFormato = MigradorDeFormato(),
) {
    /**
     * Escribe el backup en [destino], **sin cerrarlo**.
     *
     * El manifiesto va **primero** dentro del ZIP a proposito: al leer se puede
     * comprobar la version del formato sin descomprimir los datos, que es lo que
     * permite rechazar un archivo demasiado nuevo antes de tocar nada.
     */
    fun escribir(
        contenido: ContenidoDelBackup,
        frase: FraseDeRespaldo,
        destino: OutputStream,
    ) {
        val claro = json.encodeToString(DatosDelBackup.serializer(), contenido.datos).toByteArray()
        val cifrado = cifrador.cifrar(claro, frase, contextoDe(VERSION_DEL_FORMATO))
        // El claro no sale de esta funcion: se sobrescribe en cuanto esta cifrado.
        claro.fill(0)

        val manifiesto =
            Manifiesto(
                versionDelEsquema = contenido.versionDelEsquema,
                versionDeLaApp = contenido.versionDeLaApp,
                creadoEnMillis = contenido.creadoEnMillis,
                dispositivo = contenido.dispositivo,
                checksum = sha256De(cifrado.bytes),
                cifrado = cifrado.parametros,
                contenido = recuentoDe(contenido.datos),
            )

        // `finish` y no `close`: se cierra el ZIP -se escribe su indice final-
        // pero NO el stream de quien llama. Quien abre un stream es quien lo
        // cierra. Si esto lo cerrara, quien llama ya no podria hacer `fsync`
        // para asegurarse de que la copia llego al disco antes de darla por
        // buena, y ese paso es justo el que hace fiable la copia previa.
        val zip = ZipOutputStream(destino)
        zip.escribir(Piezas.MANIFIESTO, json.encodeToString(Manifiesto.serializer(), manifiesto).toByteArray())
        zip.escribir(Piezas.DATOS, cifrado.bytes)
        zip.finish()
        destino.flush()
    }

    /**
     * Lee y descifra el backup de [origen].
     *
     * El orden de las comprobaciones es lo que hace utiles los mensajes de error:
     *
     * 1. Que el archivo tenga manifiesto: si no, "esto no es un backup".
     * 2. Que la version del formato sea legible: si no, "actualiza la app".
     * 3. Que el contenido cifrado coincida con su checksum: si no, "archivo
     *    dañado". Esto se sabe **sin la frase**.
     * 4. Solo entonces se descifra. Si falla ahora, el archivo esta entero y lo
     *    que no cuadra es la frase: [FraseIncorrecta], y la interfaz puede
     *    volver a pedirla en vez de dar el backup por perdido.
     *
     * @throws FraseIncorrecta si la frase no abre el backup.
     * @throws BackupInvalido en cualquier otro caso.
     */
    fun leer(
        origen: InputStream,
        frase: FraseDeRespaldo,
    ): BackupLeido {
        val piezas = descomprimir(origen)
        val manifiesto = manifiestoDe(piezas)

        exigirFormatoLegible(manifiesto)
        val cifrado = datosDe(piezas)
        exigirChecksumCorrecto(cifrado, manifiesto)

        val claro =
            cifrador.descifrar(
                cifrado = cifrado,
                parametros = manifiesto.cifrado,
                frase = frase,
                // La version del ARCHIVO, no la de la app: un backup v1 tiene
                // que seguir abriendose cuando la app vaya por la v3.
                contexto = contextoDe(manifiesto.versionDelFormato),
            )

        return try {
            BackupLeido(manifiesto, decodificar(claro, manifiesto.versionDelFormato))
        } finally {
            claro.fill(0)
        }
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
                "El backup esta dañado: su contenido no coincide con el que se guardo. " +
                    "No se ha modificado nada.",
            )
        }
    }

    /**
     * Lee **solo** el manifiesto.
     *
     * No necesita la frase. Sirve para enseñar el resumen previo -"4 cuentas,
     * 312 movimientos, del 15 de marzo"- y para rechazar un archivo de una
     * version futura antes de pedirle nada al usuario (docs/05).
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

    /**
     * Del JSON descifrado a los datos, pasando antes por las migraciones.
     *
     * Una copia del formato actual pasa por el migrador sin cambios; una de un
     * formato anterior sale convertida al de hoy antes de tocar los DTOs.
     */
    private fun decodificar(
        datos: ByteArray,
        versionDelArchivo: Int,
    ): DatosDelBackup =
        try {
            val crudo = json.parseToJsonElement(datos.decodeToString()).jsonObject
            json.decodeFromJsonElement(DatosDelBackup.serializer(), migrador.migrar(crudo, versionDelArchivo))
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

/**
 * Lo que se autentica junto al contenido cifrado, ademas de los parametros.
 *
 * Incluye la version del formato: si alguien la cambia en el manifiesto para
 * forzar otra ruta de lectura, el descifrado falla.
 */
private fun contextoDe(versionDelFormato: Int) = "formato=$versionDelFormato"

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
fun ArchivoDeBackup.escribirABytes(
    contenido: ContenidoDelBackup,
    frase: FraseDeRespaldo,
): ByteArray = ByteArrayOutputStream().also { escribir(contenido, frase, it) }.toByteArray()
