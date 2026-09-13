package com.miplata.core.data.database.seguridad

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val NOMBRE_DE_LA_BASE = "prueba.db"
private const val BYTES_DE_IV = 12
private const val BITS_DE_ETIQUETA = 128

/**
 * Custodio en memoria que hace AES-GCM **de verdad**.
 *
 * No es un mock que devuelva lo que le pidan: cifra y descifra igual que el
 * real, asi que el ida y vuelta de la frase se prueba de verdad. Lo unico que
 * cambia es de donde sale la clave, porque Robolectric no emula el Android
 * Keystore -comprobado: lanza NoSuchAlgorithmException-.
 */
private class CustodioEnMemoria : CustodioDeClaves {
    private var clave: SecretKey? = null

    /** Simula que Android invalido la clave, por ejemplo al cambiar el bloqueo. */
    fun invalidarClave() {
        clave = null
    }

    override fun hayClave(): Boolean = clave != null

    override fun cifrar(datos: ByteArray): ByteArray {
        val activa =
            clave ?: KeyGenerator
                .getInstance("AES")
                .apply { init(256) }
                .generateKey()
                .also { clave = it }
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(Cipher.ENCRYPT_MODE, activa)
        return cifrador.iv + cifrador.doFinal(datos)
    }

    override fun descifrar(empaquetado: ByteArray): ByteArray {
        val activa = clave ?: throw GeneralSecurityException("clave invalidada")
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(
            Cipher.DECRYPT_MODE,
            activa,
            GCMParameterSpec(BITS_DE_ETIQUETA, empaquetado.copyOfRange(0, BYTES_DE_IV)),
        )
        return cifrador.doFinal(empaquetado.copyOfRange(BYTES_DE_IV, empaquetado.size))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProveedorDeClaveDeBaseDeDatosTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val custodio = CustodioEnMemoria()

    private fun proveedor(nombre: String = NOMBRE_DE_LA_BASE) = ProveedorDeClaveDeBaseDeDatos(context, nombre, custodio)

    @Before
    fun limpiar() {
        context
            .getSharedPreferences("mi-plata-seguridad", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getDatabasePath(NOMBRE_DE_LA_BASE).delete()
    }

    @Test
    fun `genera una frase de 256 bits la primera vez`() {
        proveedor().obtenerFrase().size shouldBe 32
    }

    @Test
    fun `devuelve la misma frase en llamadas sucesivas`() {
        val primera = proveedor().obtenerFrase()

        proveedor().obtenerFrase() shouldBe primera
    }

    // Si dos instalaciones generaran la misma frase, la clave no seria secreta.
    @Test
    fun `dos instalaciones generan frases distintas`() {
        val unaFrase = proveedor().obtenerFrase()
        limpiar()

        ProveedorDeClaveDeBaseDeDatos(context, NOMBRE_DE_LA_BASE, CustodioEnMemoria())
            .obtenerFrase() shouldNotBe unaFrase
    }

    @Test
    fun `la frase guardada no queda en claro`() {
        val frase = proveedor().obtenerFrase()

        val guardada =
            context
                .getSharedPreferences("mi-plata-seguridad", Context.MODE_PRIVATE)
                .getString("frase-de-la-base", null)

        guardada shouldNotBe null
        guardada!!.contains(android.util.Base64.encodeToString(frase, android.util.Base64.NO_WRAP)) shouldBe false
    }

    // El caso que puede costarle los datos al usuario. Generar una frase nueva
    // dejaria la base inaccesible para siempre y Room la abriria como si
    // estuviera vacia: el usuario veria su app en blanco y pensaria que perdio
    // sus datos por un fallo suyo.
    @Test
    fun `si hay base pero no hay frase, falla en vez de generar una nueva`() {
        context.getDatabasePath(NOMBRE_DE_LA_BASE).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        shouldThrow<ClaveDeBaseDeDatosNoDisponible> { proveedor().obtenerFrase() }
    }

    @Test
    fun `si no hay base, generar una frase nueva es correcto`() {
        proveedor().obtenerFrase().size shouldBe 32
    }

    // Lo que pasaria si atasemos la clave al bloqueo de pantalla y el usuario lo
    // cambiara. Tampoco entonces se genera una frase nueva en silencio.
    @Test
    fun `si la clave se invalida, avisa en vez de destruir el acceso`() {
        proveedor().obtenerFrase()
        custodio.invalidarClave()

        shouldThrow<ClaveDeBaseDeDatosNoDisponible> { proveedor().obtenerFrase() }
    }
}
