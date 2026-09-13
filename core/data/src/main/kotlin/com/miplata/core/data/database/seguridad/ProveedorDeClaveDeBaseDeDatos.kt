package com.miplata.core.data.database.seguridad

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * La base de datos no se puede abrir porque su clave no esta disponible.
 *
 * Es irrecuperable en este dispositivo: la clave del Keystore no sale del
 * hardware y no se puede reconstruir. La salida es restaurar desde un backup
 * (docs/05).
 */
class ClaveDeBaseDeDatosNoDisponible(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/**
 * Custodia la frase que cifra la base de datos.
 *
 * El esquema tiene dos niveles:
 *
 * 1. Una **frase aleatoria de 256 bits** cifra la base con SQLCipher. Se genera
 *    una vez, con [SecureRandom], y nunca se escribe en claro.
 * 2. Esa frase se guarda cifrada por un [CustodioDeClaves], cuya clave vive en
 *    hardware seguro y no se puede exportar.
 *
 * Asi, el archivo `.db` fuera del telefono es ruido, y la frase guardada tambien
 * lo es sin el hardware que la descifra.
 */
class ProveedorDeClaveDeBaseDeDatos(
    private val context: Context,
    private val nombreDeLaBase: String,
    private val custodio: CustodioDeClaves = CustodioDelAndroidKeystore(),
) {
    /**
     * La frase que abre la base, generandola la primera vez.
     *
     * @throws ClaveDeBaseDeDatosNoDisponible si ya existe una base pero su frase
     *   no se puede recuperar.
     */
    fun obtenerFrase(): ByteArray {
        val guardada = preferencias().getString(CLAVE_FRASE, null)

        if (guardada == null) {
            // El caso que puede costarle los datos al usuario: no hay frase
            // guardada pero SI hay base. Generar una nueva la dejaria huerfana
            // para siempre y, peor, Room la abriria como si estuviera vacia: el
            // usuario veria su app en blanco y pensaria que perdio sus datos por
            // un fallo suyo. Mejor fallar y decirle que restaure.
            if (existeLaBase()) {
                throw ClaveDeBaseDeDatosNoDisponible(
                    "Existe una base de datos pero no su clave. Generar una nueva la dejaria " +
                        "inaccesible para siempre. Hay que restaurar desde un backup.",
                )
            }
            return crearYGuardarFrase()
        }

        return try {
            custodio.descifrar(Base64.decode(guardada, Base64.NO_WRAP))
        } catch (e: GeneralSecurityException) {
            // Clave invalidada o datos manipulados. NO se genera una frase nueva:
            // eso destruiria el acceso a la base existente en silencio.
            throw ClaveDeBaseDeDatosNoDisponible(
                "No se pudo descifrar la clave de la base de datos.",
                e,
            )
        }
    }

    private fun existeLaBase(): Boolean = context.getDatabasePath(nombreDeLaBase).exists()

    private fun crearYGuardarFrase(): ByteArray {
        val frase = ByteArray(BYTES_DE_FRASE).also(SecureRandom()::nextBytes)
        val empaquetada = custodio.cifrar(frase)

        preferencias().edit {
            putString(CLAVE_FRASE, Base64.encodeToString(empaquetada, Base64.NO_WRAP))
        }

        return frase
    }

    private fun preferencias() = context.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFERENCIAS = "mi-plata-seguridad"
        const val CLAVE_FRASE = "frase-de-la-base"
        const val BYTES_DE_FRASE = 32
    }
}
