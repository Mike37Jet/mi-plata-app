package com.miplata.feature.transacciones

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.AgruparMovimientosPorDiaUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Flujo critico 2: anotar un movimiento.
 *
 * Es lo que el usuario hace todos los dias, y de lo que depende todo lo demas:
 * sin movimientos anotados el Resumen no tiene realidad con la que comparar el
 * plan, y los saldos se quedan en el saldo inicial.
 *
 * Se prueba la pantalla de verdad -Compose real, ViewModel real- con los
 * repositorios en memoria, y corre en la JVM con Robolectric para que se
 * ejecute en cada PR (docs/02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlujoDeAnotarTest {
    @get:Rule
    val compose = createComposeRule()

    private val transacciones = FakeTransaccionRepository()
    private val cuentas = FakeCuentaRepository()
    private val categorias = FakeCategoriaRepository()
    private val planes = FakePlanRepository()
    private val ajustes = FakeAjustesRepository()
    private val marzo = Mes.de(2026, 3)

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun abrirPantalla() {
        val viewModel =
            TransaccionesViewModel(
                transacciones = transacciones,
                ids = GeneradorDeIdsSecuencial(),
                calendario =
                    object : Calendario {
                        override fun hoy() = LocalDate(marzo.anio, marzo.numeroDeMes, DIA)
                    },
                cuentas = cuentas,
                categorias = categorias,
                planes = planes,
                ajustes = ajustes,
                agruparPorDia = AgruparMovimientosPorDiaUseCase(),
            )

        compose.setContent {
            MiPlataTheme {
                PantallaTransacciones(viewModel = viewModel)
            }
        }
    }

    private fun darDeAltaCuenta(nombre: String = "Banco") =
        runBlocking {
            cuentas.guardar(
                Cuenta(
                    id = CuentaId(nombre),
                    nombre = nombre,
                    tipo = TipoDeCuenta.BANCARIA,
                    saldoInicial = Money.ZERO,
                    moneda = Moneda("USD"),
                ),
            )
        }

    @Test
    fun `anotar un gasto lo deja en la lista del dia`() {
        darDeAltaCuenta()
        abrirPantalla()

        anotar(importe = "42")

        // Agrupado bajo su dia, y con signo: en una lista de movimientos el
        // signo es la informacion.
        compose.onNodeWithText("2026-03-10").assertIsDisplayed()
        compose.onAllNodesWithText("-$42.00")[0].assertIsDisplayed()
    }

    @Test
    fun `lo anotado queda guardado como gasto de la cuenta elegida`() {
        darDeAltaCuenta("Cartera")
        abrirPantalla()

        anotar(importe = "42")

        val movimiento = movimientosGuardados().single()
        movimiento.monto shouldBe Money.deUnidades(42)
        movimiento.tipo shouldBe TipoDeTransaccion.GASTO
        movimiento.cuentaOrigenId shouldBe CuentaId("Cartera")
        // La fecha de hoy viene puesta: es la mitad de lo que hace que esto sea
        // un alta rapida.
        movimiento.fecha shouldBe LocalDate(2026, 3, DIA)
    }

    @Test
    fun `la categoria elegida se guarda`() {
        darDeAltaCuenta()
        runBlocking { categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida")) }
        abrirPantalla()

        anotar(importe = "30", categoria = "Comida")

        movimientosGuardados().single().categoriaId shouldBe CategoriaId("comida")
    }

    // El modelo exige monto positivo. El editor lo comprueba antes de construir
    // la transaccion, asi que el boton se ve apagado en vez de fallar al pulsar.
    @Test
    fun `sin importe no se puede guardar`() {
        darDeAltaCuenta()
        abrirPantalla()

        compose.onNodeWithContentDescription("Anotar movimiento").performClick()

        compose.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    // Toda transaccion sale de una cuenta: sin ninguna, ofrecer el boton seria
    // abrir un formulario que no se puede guardar.
    @Test
    fun `sin cuentas no se ofrece anotar`() {
        abrirPantalla()

        compose
            .onNodeWithText("Primero crea una cuenta: todo movimiento sale de alguna. Ve a Cuentas.")
            .assertIsDisplayed()
        compose.onAllNodesWithText("Anotar movimiento").fetchSemanticsNodes().size shouldBe 0
    }

    /** Abre el alta, rellena lo indicado y guarda. */
    private fun anotar(
        importe: String,
        categoria: String? = null,
    ) {
        compose.onNodeWithContentDescription("Anotar movimiento").performClick()

        // El campo se localiza por su etiqueta, no por el marcador de posicion:
        // el "0" solo se dibuja con el campo vacio Y enfocado, asi que buscarlo
        // ataria el test a donde este el cursor. La etiqueta esta siempre.
        compose.onNodeWithText("Cuánto").performTextInput(importe)

        categoria?.let { pulsarEnLaHoja(it) }
        pulsarEnLaHoja("Guardar")
    }

    /**
     * Pulsa un control que vive dentro de la hoja modal.
     *
     * Dos cosas que no son obvias:
     *
     * 1. Se invoca la accion de clic por **semantica** y no se tocan unas
     *    coordenadas. La hoja se dibuja en una ventana aparte y, bajo
     *    Robolectric, un toque por coordenadas no se enruta a esa ventana: el
     *    gesto se pierde en silencio y el test pasa sin haber pulsado nada.
     *    Queda probado el cableado -pantalla, ViewModel, dominio- pero no el
     *    gesto fisico, que se comprueba a mano en el emulador.
     * 2. Se espera a que el control este **habilitado**. Guardar solo se
     *    enciende cuando el importe ya llego al ViewModel; pulsarlo antes no
     *    hace nada y el test seguiria adelante creyendo que guardo.
     */
    private fun pulsarEnLaHoja(texto: String) {
        compose.waitUntil(ESPERA) {
            compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(isEnabled() and hasText(texto)).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun movimientosGuardados() = runBlocking { transacciones.observarTodas().first() }

    private companion object {
        const val DIA = 10
        const val ESPERA = 5_000L
    }
}
