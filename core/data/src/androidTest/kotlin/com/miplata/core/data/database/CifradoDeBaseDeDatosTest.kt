package com.miplata.core.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.miplata.core.data.database.entity.CuentaEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba en un dispositivo real que la base queda cifrada.
 *
 * **Esto no corre en CI, y es una decision consciente.** SQLCipher usa librerias
 * nativas que la JVM no carga, asi que la alternativa era levantar un emulador
 * en cada PR -varios minutos y la parte mas fragil de cualquier CI de Android- o
 * dejar sin verificar la promesa central de esta capa. Se elige ejecutarlo a
 * mano contra un emulador o un movil:
 *
 * ```
 * ./gradlew :core:data:connectedDebugAndroidTest
 * ```
 *
 * Lo que si corre en CI son los 29 tests de JVM: el SQL de los DAO y la logica
 * de custodia de la clave, que es donde estan los errores que se cometen al
 * editar codigo.
 */
@RunWith(AndroidJUnit4::class)
class CifradoDeBaseDeDatosTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun partirDeCero() {
        context.deleteDatabase(MiPlataDatabase.NOMBRE)
        context
            .getSharedPreferences("mi-plata-seguridad", 0)
            .edit()
            .clear()
            .commit()
    }

    // La promesa que se le hace al usuario: si alguien saca el archivo del
    // telefono, no puede leer sus finanzas.
    @Test
    fun elArchivoNoEsUnaBaseSqliteLegible() {
        crearBaseConUnaCuenta()

        val bytes = context.getDatabasePath(MiPlataDatabase.NOMBRE).readBytes()
        val cabecera = bytes.copyOfRange(0, CABECERA_SQLITE.size).toString(Charsets.US_ASCII)

        cabecera shouldNotBe CABECERA_SQLITE.toString(Charsets.US_ASCII)
    }

    // Una cabecera distinta no basta: hay que comprobar que el contenido
    // tampoco esta ahi en claro.
    @Test
    fun elNombreDeLaCuentaNoApareceEnClaroEnElArchivo() {
        crearBaseConUnaCuenta()

        val contenido =
            context.getDatabasePath(MiPlataDatabase.NOMBRE).readBytes().toString(Charsets.ISO_8859_1)

        contenido.contains(NOMBRE_RECONOCIBLE) shouldBe false
    }

    // El ida y vuelta completo contra el Android Keystore real, que es lo que
    // Robolectric no puede emular.
    @Test
    fun vuelveAAbrirseConLaMismaClaveYLosDatosSiguenAhi() {
        crearBaseConUnaCuenta()

        val db = FabricaDeBaseDeDatos.crear(context)
        val recuperada = runBlocking { db.cuentaDao().obtener("c1") }
        db.close()

        recuperada?.nombre shouldBe NOMBRE_RECONOCIBLE
    }

    private fun crearBaseConUnaCuenta() {
        val db = FabricaDeBaseDeDatos.crear(context)
        runBlocking {
            db.cuentaDao().guardar(
                CuentaEntity(
                    id = "c1",
                    nombre = NOMBRE_RECONOCIBLE,
                    tipo = "BANCARIA",
                    saldoInicialCentavos = 123_456,
                    moneda = "USD",
                    incluirEnTotal = true,
                    archivada = false,
                    creadaEn = 1,
                    actualizadaEn = 1,
                ),
            )
        }
        db.close()
    }

    private companion object {
        val CABECERA_SQLITE = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        const val NOMBRE_RECONOCIBLE = "CuentaDePruebaMuyReconocible"
    }
}
