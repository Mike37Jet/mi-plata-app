package com.miplata.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Utilidades para fabricar backups rotos a proposito.
//
// Se construyen manipulando el ZIP de verdad, no con cadenas a mano: asi los
// tests de corrupcion comprueban lo que de verdad le llegaria a la app si un
// archivo se estropea en la nube, al copiarlo, o si alguien lo toca.

internal val FRASE = FraseDeRespaldo.de("correcto caballo bateria grapa")
internal val OTRA_FRASE = FraseDeRespaldo.de("incorrecto caballo bateria grapa")

/**
 * Un archivo con pocas iteraciones, para que los tests no tarden.
 *
 * Con las 600.000 de produccion cada test pasaria medio segundo solo derivando
 * la clave. Hay UN test que usa el valor real, para que la configuracion de
 * produccion tambien este probada; el resto prueba el formato, no la CPU.
 */
internal fun archivoRapido() = ArchivoDeBackup(CifradorDeBackup(iteraciones = ITERACIONES_DE_TEST))

internal const val ITERACIONES_DE_TEST = 1_000

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

/** Cambia un bit del contenido cifrado, como haria una copia a medias. */
internal fun corromperLosDatos(backup: ByteArray): ByteArray {
    val piezas = piezasDe(backup)
    piezas[Piezas.DATOS] = conUnBitCambiado(piezas.getValue(Piezas.DATOS))
    return zipCon(piezas)
}

/**
 * Cambia un bit del contenido cifrado **y** recalcula el checksum.
 *
 * Es lo que haria alguien que manipula el archivo a proposito sabiendo como
 * esta hecho: el checksum ya no le delata. Lo que tiene que pararlo entonces es
 * la autenticacion de GCM.
 */
internal fun manipularConChecksumNuevo(backup: ByteArray): ByteArray {
    val piezas = piezasDe(backup)
    val manipulado = conUnBitCambiado(piezas.getValue(Piezas.DATOS))
    piezas[Piezas.DATOS] = manipulado
    val manifiesto = piezas.getValue(Piezas.MANIFIESTO).decodeToString()
    val checksumViejo = Regex("\"checksum\":\"([0-9a-f]+)\"").find(manifiesto)!!.groupValues[1]
    piezas[Piezas.MANIFIESTO] = manifiesto.replace(checksumViejo, sha256(manipulado)).toByteArray()
    return zipCon(piezas)
}

/** Corta el contenido cifrado por la mitad, como una descarga interrumpida. */
internal fun truncarLosDatos(backup: ByteArray): ByteArray {
    val piezas = piezasDe(backup)
    val datos = piezas.getValue(Piezas.DATOS)
    piezas[Piezas.DATOS] = datos.copyOf(datos.size / 2)
    return zipCon(piezas)
}

/** Reescribe un campo de texto del manifiesto. */
internal fun conCampoDelManifiesto(
    backup: ByteArray,
    campo: String,
    valorNuevo: String,
): ByteArray {
    val piezas = piezasDe(backup)
    val manifiesto = piezas.getValue(Piezas.MANIFIESTO).decodeToString()
    val patron = Regex("\"$campo\":(\"[^\"]*\"|[0-9]+)")
    require(patron.containsMatchIn(manifiesto)) { "El manifiesto no tiene el campo $campo" }
    piezas[Piezas.MANIFIESTO] = manifiesto.replace(patron, "\"$campo\":$valorNuevo").toByteArray()
    return zipCon(piezas)
}

/** Reescribe el manifiesto con otra version de formato. */
internal fun conVersionDeFormato(
    backup: ByteArray,
    version: Int,
): ByteArray = conCampoDelManifiesto(backup, "versionDelFormato", version.toString())

private fun conUnBitCambiado(bytes: ByteArray): ByteArray =
    bytes.copyOf().also { copia ->
        val mitad = copia.size / 2
        copia[mitad] = (copia[mitad].toInt() xor 1).toByte()
    }

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Un almacen de copia previa en memoria, con escritura atomica como el real:
 * lo que se escribe solo sustituye a lo anterior si la escritura termina bien.
 */
internal class AlmacenEnMemoria : AlmacenDeLaCopiaPrevia {
    var contenido: ByteArray? = null
        private set
    var fallarAlGuardar: Boolean = false

    override fun frase(): FraseDeRespaldo = FraseDeRespaldo.de("frase interna de la copia previa")

    override fun guardar(escribir: (java.io.OutputStream) -> Unit) {
        if (fallarAlGuardar) throw java.io.IOException("Sin espacio para la copia previa")
        val buffer = ByteArrayOutputStream()
        escribir(buffer)
        contenido = buffer.toByteArray()
    }

    override fun abrir() = contenido?.let(::ByteArrayInputStream)

    override fun borrar() {
        contenido = null
    }

    private var sustituta: ByteArray? = null
    var fallarAlPrepararSustituta: Boolean = false

    override fun prepararSustituta(escribir: (java.io.OutputStream) -> Unit) {
        if (fallarAlPrepararSustituta) throw java.io.IOException("Sin espacio para la sustituta")
        sustituta = ByteArrayOutputStream().also(escribir).toByteArray()
    }

    override fun confirmarSustituta() {
        contenido = checkNotNull(sustituta) { "No hay sustituta que confirmar" }
        sustituta = null
    }

    override fun descartarSustituta() {
        sustituta = null
    }
}
