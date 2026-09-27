package com.miplata.feature.backup

import app.cash.turbine.test
import com.miplata.core.backup.AlmacenDeLaCopiaPrevia
import com.miplata.core.backup.ArchivoDeBackup
import com.miplata.core.backup.CifradorDeBackup
import com.miplata.core.backup.ContenidoDelBackup
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.backup.RecolectorDeDatos
import com.miplata.core.backup.RestauradorDeCopias
import com.miplata.core.backup.escribirABytes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCierreRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeRepositorioDeRestauracion
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.OutputStream

private const val FRASE = "correcto caballo bateria grapa"
private const val URI = "content://prueba/copia.mpb"
private const val CREADA_EN = 1_785_000_000_000L

private class CopiaPreviaEnMemoria : AlmacenDeLaCopiaPrevia {
    var contenido: ByteArray? = null

    override fun frase() = FraseDeRespaldo.de("frase interna de la copia previa")

    override fun guardar(escribir: (OutputStream) -> Unit) {
        contenido = ByteArrayOutputStream().also(escribir).toByteArray()
    }

    override fun abrir() = contenido?.let(::ByteArrayInputStream)

    override fun borrar() {
        contenido = null
    }

    private var sustituta: ByteArray? = null

    override fun prepararSustituta(escribir: (OutputStream) -> Unit) {
        sustituta = ByteArrayOutputStream().also(escribir).toByteArray()
    }

    override fun confirmarSustituta() {
        contenido = sustituta
        sustituta = null
    }

    override fun descartarSustituta() {
        sustituta = null
    }
}

private fun cuenta(
    id: String,
    nombre: String,
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.deUnidades(100),
    moneda = Moneda("EUR"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RestaurarViewModelTest {
    private val despachador = StandardTestDispatcher()
    private val archivo = ArchivoDeBackup(CifradorDeBackup(iteraciones = 1_000))

    private val cuentas = FakeCuentaRepository()
    private val categorias = FakeCategoriaRepository()
    private val transacciones = FakeTransaccionRepository()
    private val planes = FakePlanRepository()
    private val cierres = FakeCierreRepository()
    private val ajustes = FakeAjustesRepository()
    private val repositorio = FakeRepositorioDeRestauracion(cuentas, categorias, transacciones, planes, cierres)
    private val recolector = RecolectorDeDatos(cuentas, categorias, transacciones, planes, ajustes, cierres)
    private val copiaPrevia = CopiaPreviaEnMemoria()

    /** Lo que devuelve el selector: una copia de verdad, cifrada con [FRASE]. */
    private var archivoElegido: ByteArray = ByteArray(0)

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(despachador)

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    /** Genera una copia con una cuenta, como si viniera de otro movil. */
    private suspend fun unaCopiaConUnaCuenta(): ByteArray {
        val otroMovil = FakeCuentaRepository().apply { guardar(cuenta("de-la-copia", "Cuenta de la copia")) }
        val datos =
            RecolectorDeDatos(
                otroMovil,
                FakeCategoriaRepository(),
                FakeTransaccionRepository(),
                FakePlanRepository(),
                FakeAjustesRepository(),
                FakeCierreRepository(),
            ).recolectar()
        return archivo.escribirABytes(
            ContenidoDelBackup(datos, 1, "0.1.0", creadoEnMillis = CREADA_EN, dispositivo = "Movil viejo"),
            FraseDeRespaldo.de(FRASE),
        )
    }

    private fun viewModel() =
        RestaurarViewModel(
            archivo = archivo,
            restaurador = RestauradorDeCopias(recolector, archivo, repositorio, ajustes, copiaPrevia),
            recolector = recolector,
            abridor = { ByteArrayInputStream(archivoElegido) },
            informacion =
                object : InformacionDeLaApp {
                    override val dispositivo = "Pixel nuevo"
                    override val versionDeLaApp = "0.1.0"
                    override val versionDelEsquema = 1
                },
            reloj =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(1_790_000_000_000L)
                },
            trabajo = despachador,
        )

    private suspend fun enElResumen(): RestaurarViewModel {
        cuentas.guardar(cuenta("actual-1", "Una de ahora"))
        cuentas.guardar(cuenta("actual-2", "Otra de ahora"))
        archivoElegido = unaCopiaConUnaCuenta()
        return viewModel().also { vm ->
            vm.elegirArchivo(URI)
            despachador.scheduler.advanceUntilIdle()
        }
    }

    // Lo que evita restaurar la copia equivocada: antes de pedir la frase se ve
    // que trae y que hay ahora.
    @Test
    fun `al elegir un archivo se ve lo que trae y lo que hay ahora, sin pedir la frase`() =
        runTest(despachador) {
            val vm = enElResumen()

            val resumen =
                vm.uiState.value.paso
                    .shouldBeInstanceOf<PasoDeRestauracion.Resumen>()
            resumen.copia.contenido.cuentas shouldBe 1
            resumen.copia.dispositivo shouldBe "Movil viejo"
            resumen.actual.cuentas shouldBe 2
            resumen.fraseIncorrecta shouldBe false
        }

    @Test
    fun `un archivo que no es una copia se rechaza`() =
        runTest(despachador) {
            archivoElegido = "una foto".toByteArray()
            val vm = viewModel()

            vm.elegirArchivo(URI)
            advanceUntilIdle()

            vm.uiState.value.paso
                .shouldBeInstanceOf<PasoDeRestauracion.Fallida>()
                .mensaje shouldContain "no parece un backup"
        }

    @Test
    fun `con la frase correcta se restaura y se puede deshacer`() =
        runTest(despachador) {
            val vm = enElResumen()

            vm.restaurar(FRASE.toCharArray())
            advanceUntilIdle()

            vm.uiState.value.paso shouldBe PasoDeRestauracion.Restaurada()
            cuentas.observarTodas().first().map { it.nombre } shouldBe listOf("Cuenta de la copia")
            vm.uiState.value.copiaPreviaEnMillis
                .shouldNotBeNull()
        }

    // Una frase mal escrita no hace perder el archivo elegido: se vuelve a pedir.
    @Test
    fun `con otra frase se vuelve a pedir, sin tocar nada`() =
        runTest(despachador) {
            val vm = enElResumen()

            vm.restaurar("otra frase distinta del todo".toCharArray())
            advanceUntilIdle()

            vm.uiState.value.paso
                .shouldBeInstanceOf<PasoDeRestauracion.Resumen>()
                .fraseIncorrecta shouldBe true
            repositorio.vecesReemplazado shouldBe 0
            cuentas.observarTodas().first().size shouldBe 2
        }

    @Test
    fun `una frase por debajo del minimo cuenta como incorrecta`() =
        runTest(despachador) {
            val vm = enElResumen()

            vm.restaurar("corta".toCharArray())

            vm.uiState.value.paso
                .shouldBeInstanceOf<PasoDeRestauracion.Resumen>()
                .fraseIncorrecta shouldBe true
        }

    @Test
    fun `tras una frase incorrecta la correcta funciona`() =
        runTest(despachador) {
            val vm = enElResumen()
            vm.restaurar("otra frase distinta del todo".toCharArray())
            advanceUntilIdle()

            vm.restaurar(FRASE.toCharArray())
            advanceUntilIdle()

            vm.uiState.value.paso shouldBe PasoDeRestauracion.Restaurada()
        }

    @Test
    fun `la frase que se le pasa queda borrada`() =
        runTest(despachador) {
            val vm = enElResumen()
            val tecleada = FRASE.toCharArray()

            vm.restaurar(tecleada)

            tecleada.all { it == '\u0000' } shouldBe true
        }

    @Test
    fun `deshacer devuelve lo que habia`() =
        runTest(despachador) {
            val vm = enElResumen()
            vm.restaurar(FRASE.toCharArray())
            advanceUntilIdle()

            vm.deshacer()
            advanceUntilIdle()

            vm.uiState.value.paso shouldBe PasoDeRestauracion.Deshecha
            cuentas
                .observarTodas()
                .first()
                .map { it.nombre }
                .sorted() shouldBe listOf("Otra de ahora", "Una de ahora")
            // Lo de justo antes de deshacer queda guardado: se puede volver a ello.
            vm.uiState.value.copiaPreviaEnMillis
                .shouldNotBeNull()
        }

    // Sin haber pasado por el resumen no se puede restaurar: el orden de los
    // pasos es la proteccion.
    @Test
    fun `sin haber visto el resumen no se restaura nada`() =
        runTest(despachador) {
            archivoElegido = unaCopiaConUnaCuenta()
            val vm = viewModel()

            vm.restaurar(FRASE.toCharArray())
            advanceUntilIdle()

            repositorio.vecesReemplazado shouldBe 0
            vm.uiState.value.paso shouldBe PasoDeRestauracion.Inicio
        }

    // El caso que de verdad protege exigir el resumen: el archivo ya esta
    // elegido pero aun se esta leyendo. Si se dejara restaurar aqui, se
    // sustituirian los datos sin que el usuario hubiera visto que trae la copia.
    @Test
    fun `mientras se lee el archivo no se puede restaurar`() =
        runTest(despachador) {
            archivoElegido = unaCopiaConUnaCuenta()
            val vm = viewModel()

            vm.elegirArchivo(URI)
            vm.restaurar(FRASE.toCharArray())
            advanceUntilIdle()

            repositorio.vecesReemplazado shouldBe 0
            vm.uiState.value.paso
                .shouldBeInstanceOf<PasoDeRestauracion.Resumen>()
        }

    // Una restauracion de hace dias sigue pudiendo deshacerse al volver a entrar.
    @Test
    fun `al abrir la pantalla se sabe si hay algo que deshacer`() =
        runTest(despachador) {
            enElResumen().apply {
                restaurar(FRASE.toCharArray())
                advanceUntilIdle()
            }

            val otraVez = viewModel()
            otraVez.uiState.test {
                var estado = awaitItem()
                while (estado.copiaPreviaEnMillis == null) estado = awaitItem()
                estado.copiaPreviaEnMillis shouldBe 1_790_000_000_000L
                cancelAndIgnoreRemainingEvents()
            }
        }
}
