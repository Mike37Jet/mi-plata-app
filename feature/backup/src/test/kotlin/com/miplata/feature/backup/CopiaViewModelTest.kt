package com.miplata.feature.backup

import app.cash.turbine.test
import com.miplata.core.backup.ArchivoDeBackup
import com.miplata.core.backup.CifradorDeBackup
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.backup.FraseIncorrecta
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

private const val FRASE = "correcto caballo bateria grapa"
private const val AHORA = 1_790_000_000_000L
private const val URI = "content://prueba/copia.mpb"

/** Un destino en memoria que recuerda lo que se escribio y a donde. */
private class DestinoEnMemoria : AbridorDeDestino {
    val escrito = ByteArrayOutputStream()
    var uriAbierta: String? = null

    override fun abrir(uri: String): OutputStream {
        uriAbierta = uri
        return escrito
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CopiaViewModelTest {
    private val despachador = StandardTestDispatcher()

    private val cuentas = FakeCuentaRepository()
    private val ajustes = FakeAjustesRepository()
    private val destino = DestinoEnMemoria()

    // Pocas iteraciones: aqui se prueba el ViewModel, no la CPU. El cifrado con
    // la configuracion real tiene su propio test en :core:backup.
    private val archivo = ArchivoDeBackup(CifradorDeBackup(iteraciones = 1_000))

    private fun viewModel(abridor: AbridorDeDestino = destino) =
        CopiaViewModel(
            recolector =
                RecolectorDeDatos(
                    cuentas,
                    FakeCategoriaRepository(),
                    FakeTransaccionRepository(),
                    FakePlanRepository(),
                    ajustes,
                ),
            archivo = archivo,
            abridor = abridor,
            informacion =
                object : InformacionDeLaApp {
                    override val dispositivo = "Pixel de prueba"
                    override val versionDeLaApp = "0.1.0"
                    override val versionDelEsquema = 1
                },
            ajustes = ajustes,
            reloj =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(AHORA)
                },
            trabajo = despachador,
        )

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(despachador)

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private suspend fun conUnaCuenta() =
        cuentas.guardar(
            Cuenta(
                id = CuentaId("banco"),
                nombre = "Cuenta del banco",
                tipo = TipoDeCuenta.BANCARIA,
                saldoInicial = Money.deUnidades(2500),
                moneda = Moneda("USD"),
            ),
        )

    // El test que importa: lo que el ViewModel escribe es una copia de verdad,
    // que se abre con la misma frase y trae los datos.
    @Test
    fun `exportar escribe una copia que se abre con la misma frase`() =
        runTest(despachador) {
            conUnaCuenta()
            val vm = viewModel()

            vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")
            advanceUntilIdle()

            destino.uriAbierta shouldBe URI
            val leido = archivo.leer(ByteArrayInputStream(destino.escrito.toByteArray()), FraseDeRespaldo.de(FRASE))
            leido.datos.cuentas
                .single()
                .nombre shouldBe "Cuenta del banco"
            leido.manifiesto.dispositivo shouldBe "Pixel de prueba"
            leido.manifiesto.creadoEnMillis shouldBe AHORA
        }

    @Test
    fun `la copia no se abre con otra frase`() =
        runTest(despachador) {
            conUnaCuenta()
            viewModel().exportar(URI, FRASE.toCharArray(), "copia.mpb")
            advanceUntilIdle()

            shouldThrow<FraseIncorrecta> {
                archivo.leer(
                    ByteArrayInputStream(destino.escrito.toByteArray()),
                    FraseDeRespaldo.de("otra frase distinta del todo"),
                )
            }
        }

    @Test
    fun `al terminar se anota la fecha de la copia y se avisa`() =
        runTest(despachador) {
            val vm = viewModel()

            vm.uiState.test {
                awaitItem().ultimaCopiaEnMillis.shouldBeNull()

                vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")

                val final = esperarHasta { it.exportacion is Exportacion.Terminada }
                final.ultimaCopiaEnMillis shouldBe AHORA
                (final.exportacion as Exportacion.Terminada).nombre shouldBe "copia.mpb"
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Apuntar la copia antes de escribirla haria que un fallo dejara al usuario
    // creyendo que tiene una copia reciente que no existe.
    @Test
    fun `si no se puede escribir no se anota ninguna copia`() =
        runTest(despachador) {
            val vm = viewModel(abridor = { throw IOException("Sin espacio en el destino") })

            vm.uiState.test {
                awaitItem()
                vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")

                val final = esperarHasta { it.exportacion is Exportacion.Fallida }
                final.ultimaCopiaEnMillis.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
            ajustes.obtener().ultimoBackupEnMillis.shouldBeNull()
        }

    @Test
    fun `un fallo de escritura se cuenta con el mensaje del proveedor`() =
        runTest(despachador) {
            val vm = viewModel(abridor = { throw IOException("Sin espacio en el destino") })

            vm.uiState.test {
                awaitItem()
                vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")

                val fallida = esperarHasta { it.exportacion is Exportacion.Fallida }.exportacion
                (fallida as Exportacion.Fallida).mensaje shouldBe "Sin espacio en el destino"
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La pantalla no tiene que acordarse de limpiar la frase: el ViewModel la
    // borra en cuanto la ha copiado.
    @Test
    fun `la frase que se le pasa queda borrada`() =
        runTest(despachador) {
            val tecleada = FRASE.toCharArray()

            viewModel().exportar(URI, tecleada, "copia.mpb")

            tecleada.all { it == '\u0000' } shouldBe true
        }

    @Test
    fun `tambien se borra si la frase no vale`() =
        runTest(despachador) {
            val tecleada = "corta".toCharArray()

            viewModel().exportar(URI, tecleada, "copia.mpb")

            tecleada.all { it == '\u0000' } shouldBe true
        }

    // Defensa por si algo salta la validacion de la pantalla: una frase corta
    // no llega a escribirse.
    @Test
    fun `una frase demasiado corta no escribe nada`() =
        runTest(despachador) {
            val vm = viewModel()

            vm.uiState.test {
                awaitItem()
                vm.exportar(URI, "corta".toCharArray(), "copia.mpb")

                esperarHasta { it.exportacion is Exportacion.Fallida }
                cancelAndIgnoreRemainingEvents()
            }
            advanceUntilIdle()
            destino.uriAbierta.shouldBeNull()
        }

    // Pulsar dos veces mientras cifra no puede lanzar dos exportaciones a la vez
    // sobre el mismo documento.
    @Test
    fun `mientras exporta no se puede empezar otra`() =
        runTest(despachador) {
            var aperturas = 0
            val vm =
                viewModel(abridor = {
                    aperturas++
                    ByteArrayOutputStream()
                })

            vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")
            vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")
            advanceUntilIdle()

            aperturas shouldBe 1
        }

    @Test
    fun `descartar el aviso vuelve al reposo`() =
        runTest(despachador) {
            val vm = viewModel()
            vm.exportar(URI, FRASE.toCharArray(), "copia.mpb")
            advanceUntilIdle()

            vm.descartarAviso()

            vm.uiState.test {
                esperarHasta { it.exportacion == Exportacion.Inactiva }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `el nombre sugerido sale del reloj`() {
        viewModel().nombreSugerido().startsWith("miplata-backup-") shouldBe true
        viewModel().nombreSugerido().endsWith(".mpb") shouldBe true
    }
}

private suspend fun app.cash.turbine.TurbineTestContext<CopiaUiState>.esperarHasta(
    condicion: (CopiaUiState) -> Boolean,
): CopiaUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
