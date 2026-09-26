package com.miplata.core.backup

import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Lo necesario para descifrar un backup, salvo la frase.
 *
 * Viaja **en claro** en el manifiesto, y no pasa nada: el salt y el vector de
 * inicializacion no son secretos, solo tienen que ser unicos. Lo que protege el
 * archivo es la frase, que nunca se escribe.
 *
 * Las iteraciones se guardan en el archivo en vez de darse por sabidas: asi se
 * pueden subir en el futuro -los ordenadores se abaratan- sin dejar de poder
 * abrir los backups antiguos, que se descifran con las suyas.
 */
@Serializable
data class ParametrosDeCifrado(
    val algoritmo: String = ALGORITMO,
    val derivacion: String = DERIVACION,
    val iteraciones: Int,
    /** Base64. */
    val salt: String,
    /** Base64. */
    val iv: String,
) {
    internal companion object {
        const val ALGORITMO = "AES-256-GCM"
        const val DERIVACION = "PBKDF2-HMAC-SHA256"
    }
}

/** Un contenido ya cifrado, con lo que hace falta para volver a abrirlo. */
class Cifrado(
    val bytes: ByteArray,
    val parametros: ParametrosDeCifrado,
)

/**
 * Cifra y descifra el contenido del backup con una frase.
 *
 * **Por que estas piezas** (docs/05):
 *
 * - **AES-256-GCM**, que es cifrado *autenticado*: ademas de ocultar el
 *   contenido, detecta cualquier modificacion. Un CBC sin MAC descifraria un
 *   archivo manipulado y devolveria basura plausible, que en una app de dinero
 *   es lo peor que puede pasar.
 * - **PBKDF2-HMAC-SHA256** para convertir la frase en clave, y no Argon2id. Los
 *   dos estan permitidos en docs/05; PBKDF2 viene en la biblioteca estandar de
 *   Java, y Argon2 obligaria a meter una dependencia nativa en un modulo que
 *   hoy es Kotlin puro y se prueba en la JVM sin nada mas. El coste se compensa
 *   con iteraciones: [ITERACIONES_POR_DEFECTO] es la cifra que recomienda OWASP
 *   para este algoritmo.
 * - **Salt e IV aleatorios en cada backup.** Dos backups con la misma frase
 *   producen claves distintas, asi que nunca se reutiliza un IV con la misma
 *   clave, que es la unica forma de romper GCM sin romper AES.
 *
 * Todo ocurre en memoria. El contenido en claro nunca se escribe en disco.
 */
class CifradorDeBackup(
    private val iteraciones: Int = ITERACIONES_POR_DEFECTO,
    private val aleatorio: SecureRandom = SecureRandom(),
) {
    init {
        require(iteraciones > 0) { "Las iteraciones tienen que ser positivas" }
    }

    fun cifrar(
        claro: ByteArray,
        frase: FraseDeRespaldo,
        contexto: String,
    ): Cifrado {
        val salt = ByteArray(BYTES_DE_SALT).also(aleatorio::nextBytes)
        val iv = ByteArray(BYTES_DE_IV).also(aleatorio::nextBytes)
        val parametros =
            ParametrosDeCifrado(
                iteraciones = iteraciones,
                salt = BASE64.encodeToString(salt),
                iv = BASE64.encodeToString(iv),
            )

        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, claveDe(frase, salt, iteraciones), GCMParameterSpec(BITS_DE_ETIQUETA, iv))
        cifrador.updateAAD(datosAsociados(parametros, contexto))

        return Cifrado(cifrador.doFinal(claro), parametros)
    }

    /**
     * Descifra, o lanza [FraseIncorrecta] si la frase no abre el contenido.
     *
     * @param contexto lo mismo que se paso al cifrar. Se autentica junto con los
     *   parametros como *datos asociados*: si alguien cambia la version del
     *   formato o el salt en el manifiesto, el descifrado falla en vez de
     *   seguir adelante con un archivo alterado.
     */
    fun descifrar(
        cifrado: ByteArray,
        parametros: ParametrosDeCifrado,
        frase: FraseDeRespaldo,
        contexto: String,
    ): ByteArray {
        exigirParametrosConocidos(parametros)

        val salt = base64De(parametros.salt, "salt")
        val iv = base64De(parametros.iv, "vector de inicializacion")

        return try {
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(
                Cipher.DECRYPT_MODE,
                claveDe(frase, salt, parametros.iteraciones),
                GCMParameterSpec(BITS_DE_ETIQUETA, iv),
            )
            cifrador.updateAAD(datosAsociados(parametros, contexto))
            cifrador.doFinal(cifrado)
        } catch (e: AEADBadTagException) {
            throw FraseIncorrecta(e)
        } catch (e: GeneralSecurityException) {
            throw BackupInvalido("El backup esta cifrado de una forma que esta app no sabe abrir", e)
        }
    }

    /**
     * Rechaza un archivo con parametros raros **antes** de derivar la clave.
     *
     * El tope de iteraciones no es un capricho: derivar la clave con mil
     * millones de iteraciones colgaria la app durante minutos. Un archivo
     * malicioso -o simplemente roto- no deberia poder hacer eso.
     */
    private fun exigirParametrosConocidos(parametros: ParametrosDeCifrado) {
        if (parametros.algoritmo != ParametrosDeCifrado.ALGORITMO ||
            parametros.derivacion != ParametrosDeCifrado.DERIVACION
        ) {
            throw BackupInvalido(
                "El backup usa un cifrado desconocido (${parametros.algoritmo}, " +
                    "${parametros.derivacion}). Actualiza la app para restaurarlo.",
            )
        }
        if (parametros.iteraciones !in 1..ITERACIONES_MAXIMAS) {
            throw BackupInvalido("Los parametros de cifrado del backup no son validos")
        }
    }

    private fun claveDe(
        frase: FraseDeRespaldo,
        salt: ByteArray,
        iteraciones: Int,
    ): SecretKeySpec {
        val especificacion = PBEKeySpec(frase.caracteres, salt, iteraciones, BITS_DE_CLAVE)
        return try {
            val bytes = SecretKeyFactory.getInstance(DERIVADOR).generateSecret(especificacion).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            // PBEKeySpec guarda su propia copia de la frase: se borra en cuanto
            // la clave esta derivada.
            especificacion.clearPassword()
        }
    }

    private fun base64De(
        texto: String,
        que: String,
    ): ByteArray =
        try {
            BASE64_DEC.decode(texto)
        } catch (e: IllegalArgumentException) {
            throw BackupInvalido("El $que del backup no es valido", e)
        }

    companion object {
        /** Recomendacion de OWASP (2023) para PBKDF2-HMAC-SHA256. */
        const val ITERACIONES_POR_DEFECTO: Int = 600_000

        private const val ITERACIONES_MAXIMAS = 10_000_000
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val DERIVADOR = "PBKDF2WithHmacSHA256"
        private const val BITS_DE_CLAVE = 256
        private const val BITS_DE_ETIQUETA = 128
        private const val BYTES_DE_SALT = 16

        /** 12 bytes: el tamaño para el que GCM esta disenado. */
        private const val BYTES_DE_IV = 12
        private val BASE64: Base64.Encoder get() = Base64.getEncoder()
        private val BASE64_DEC: Base64.Decoder get() = Base64.getDecoder()

        /**
         * Lo que se autentica sin cifrar: los parametros y un contexto.
         *
         * Una cadena explicita y no el JSON del manifiesto, porque tiene que salir
         * byte a byte igual al cifrar y al descifrar, y el orden de los campos de
         * un JSON no es algo en lo que se pueda confiar entre versiones.
         */
        private fun datosAsociados(
            parametros: ParametrosDeCifrado,
            contexto: String,
        ): ByteArray =
            listOf(
                "miplata-backup",
                contexto,
                parametros.algoritmo,
                parametros.derivacion,
                parametros.iteraciones.toString(),
                parametros.salt,
                parametros.iv,
            ).joinToString("|").toByteArray()
    }
}
