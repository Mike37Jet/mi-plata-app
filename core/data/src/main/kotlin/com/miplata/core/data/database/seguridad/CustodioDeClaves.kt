package com.miplata.core.data.database.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra y descifra con una clave que nunca sale de su custodia.
 *
 * Existe como interfaz por una razon concreta y comprobada: **Robolectric no
 * emula el Android Keystore**, asi que cualquier logica que lo toque de forma
 * directa no se puede probar en la JVM. Con esta frontera, la parte que de
 * verdad puede costarle los datos al usuario -decidir si generar una frase
 * nueva o negarse- se prueba en CI con un custodio falso, y la implementacion
 * real se cubre con un test instrumentado.
 *
 * Lo cifrado incluye el vector de inicializacion por delante: no es secreto,
 * pero sin el no hay forma de descifrar.
 */
interface CustodioDeClaves {
    /** Si la clave sigue existiendo y es utilizable. */
    fun hayClave(): Boolean

    fun cifrar(datos: ByteArray): ByteArray

    fun descifrar(empaquetado: ByteArray): ByteArray
}

/**
 * Custodio respaldado por el Android Keystore.
 *
 * La clave AES-256 vive en hardware seguro y **no se puede exportar**: ni esta
 * app puede leerla, solo pedirle que cifre o descifre.
 *
 * **No se ata al bloqueo de pantalla a proposito.** Android invalida las claves
 * con `setUserAuthenticationRequired` cuando el usuario quita o cambia el
 * bloqueo, y en algunos moviles al anadir una huella nueva. Una clave invalidada
 * deja la base ilegible para siempre, y el usuario perderia todo lo posterior a
 * su ultimo backup por haber cambiado el PIN.
 *
 * La amenaza realista es que alguien extraiga el archivo de la base del
 * telefono, y contra eso esto ya protege. Contra alguien que tenga el telefono
 * desbloqueado en la mano protege el bloqueo biometrico de la app, que es otra
 * capa distinta y llega en la Etapa 5.
 */
class CustodioDelAndroidKeystore(
    private val alias: String = ALIAS_POR_DEFECTO,
) : CustodioDeClaves {
    override fun hayClave(): Boolean = claveExistente() != null

    override fun cifrar(datos: ByteArray): ByteArray {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, claveExistente() ?: generarClave())
        return cifrador.iv + cifrador.doFinal(datos)
    }

    override fun descifrar(empaquetado: ByteArray): ByteArray {
        val clave =
            claveExistente() ?: throw ClaveDeBaseDeDatosNoDisponible(
                "La clave del Keystore ya no existe. Hay que restaurar desde un backup.",
            )
        val iv = empaquetado.copyOfRange(0, BYTES_DE_IV)
        val cifrada = empaquetado.copyOfRange(BYTES_DE_IV, empaquetado.size)

        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(BITS_DE_ETIQUETA, iv))
        return cifrador.doFinal(cifrada)
    }

    private fun claveExistente(): SecretKey? {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return keystore.getKey(alias, null) as? SecretKey
    }

    private fun generarClave(): SecretKey {
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(BITS_DE_CLAVE)
                // Sin setUserAuthenticationRequired: ver el KDoc de la clase.
                .build(),
        )
        return generador.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS_POR_DEFECTO = "mi-plata-clave-de-base-de-datos"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BYTES_DE_IV = 12
        const val BITS_DE_ETIQUETA = 128
        const val BITS_DE_CLAVE = 256
    }
}
