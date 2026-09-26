package com.miplata.app.copia

import android.content.Context
import androidx.core.util.AtomicFile
import com.miplata.core.backup.AlmacenDeLaCopiaPrevia
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.data.database.MiPlataDatabase
import com.miplata.core.data.database.seguridad.ProveedorDeClaveDeBaseDeDatos
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Guarda la copia previa a una restauracion en el almacenamiento privado de la
 * app.
 *
 * **Donde:** en `filesDir`, que ninguna otra app puede leer y que no sale del
 * telefono (`allowBackup="false"`, docs/05). Es para deshacer aqui, no para
 * llevarsela a otro movil.
 *
 * **Con que frase:** una **derivada** de la clave que cifra la base, con
 * HMAC-SHA256 y una etiqueta propia. Asi:
 *
 * - la copia previa queda exactamente igual de protegida que la base -quien
 *   pudiera sacar esa clave del Keystore ya tendria todos los datos igualmente-;
 * - no hay un secreto nuevo que generar, guardar y perder;
 * - y no se reutiliza la clave tal cual para dos cosas: la etiqueta separa los
 *   usos, que es lo que hace HKDF, y si una se viera comprometida no delata la
 *   otra.
 *
 * **Como:** con `AtomicFile`. La copia nueva se escribe aparte y solo sustituye a
 * la anterior al terminar bien; si la app muere a mitad, queda la de antes.
 */
class CopiaPreviaEnArchivo(
    private val context: Context,
) : AlmacenDeLaCopiaPrevia {
    private val archivo: AtomicFile get() = AtomicFile(File(context.filesDir, NOMBRE))

    override fun frase(): FraseDeRespaldo {
        val claveDeLaBase = ProveedorDeClaveDeBaseDeDatos(context, MiPlataDatabase.NOMBRE).obtenerFrase()
        try {
            val mac = Mac.getInstance(HMAC)
            mac.init(SecretKeySpec(claveDeLaBase, HMAC))
            val derivada = mac.doFinal(ETIQUETA.toByteArray())
            val hex = CharArray(derivada.size * 2)
            derivada.forEachIndexed { i, byte ->
                hex[i * 2] = DIGITOS[(byte.toInt() shr BITS_POR_CIFRA) and MASCARA_DE_CIFRA]
                hex[i * 2 + 1] = DIGITOS[byte.toInt() and MASCARA_DE_CIFRA]
            }
            derivada.fill(0)
            return FraseDeRespaldo(hex).also { hex.fill('\u0000') }
        } finally {
            claveDeLaBase.fill(0)
        }
    }

    override fun guardar(escribir: (OutputStream) -> Unit) {
        val atomico = archivo
        val salida = atomico.startWrite()
        var terminada = false
        try {
            escribir(salida)
            atomico.finishWrite(salida)
            terminada = true
        } finally {
            // Cualquier fallo a mitad -o una cancelacion- deja la copia anterior
            // intacta.
            if (!terminada) atomico.failWrite(salida)
        }
    }

    override fun abrir(): InputStream? = archivo.takeIf { it.baseFile.exists() }?.openRead()

    override fun borrar() = archivo.delete()

    // La sustituta vive en su propio archivo hasta confirmarse, asi la copia
    // previa sigue intacta mientras tanto. Confirmar es un `renameTo` dentro del
    // mismo directorio, que el sistema de archivos hace de forma atomica.
    override fun prepararSustituta(escribir: (OutputStream) -> Unit) {
        val destino = sustituta
        var escrita = false
        try {
            destino.outputStream().use { salida ->
                escribir(salida)
                // Asegura que llego al disco antes de darla por buena.
                salida.fd.sync()
            }
            escrita = true
        } finally {
            if (!escrita) destino.delete()
        }
    }

    override fun confirmarSustituta() {
        val actual = File(context.filesDir, NOMBRE)
        if (!sustituta.renameTo(actual)) {
            throw java.io.IOException("No se pudo guardar la copia de lo que habia antes de volver atras")
        }
    }

    override fun descartarSustituta() {
        sustituta.delete()
    }

    private val sustituta: File get() = File(context.filesDir, "$NOMBRE.sustituta")

    private companion object {
        const val NOMBRE = "copia-previa-a-restaurar.mpb"
        const val HMAC = "HmacSHA256"

        /** Separa este uso de la clave de cualquier otro. No cambiarla: dejaria sin abrir la copia previa. */
        const val ETIQUETA = "miplata/copia-previa-a-restaurar/v1"
        val DIGITOS = "0123456789abcdef".toCharArray()
        const val BITS_POR_CIFRA = 4
        const val MASCARA_DE_CIFRA = 0xF
    }
}
