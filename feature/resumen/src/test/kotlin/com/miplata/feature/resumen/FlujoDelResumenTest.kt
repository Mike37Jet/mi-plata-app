package com.miplata.feature.resumen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCierreRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Flujo critico 3: ver si me alcanza.
 *
 * Es la pregunta que justifica la app entera (docs/00), y el unico sitio donde
 * las dos mitades del modelo se cruzan: el plan que escribiste y los
 * movimientos que ocurrieron. Si esto miente, todo lo demas da igual.
 *
 * Los datos entran por los mismos repositorios que usa la pantalla de
 * Movimientos, asi que lo que se comprueba aqui es que un gasto anotado **llega
 * de verdad** al resumen y mueve las cifras.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlujoDelResumenTest {
    @get:Rule
    val compose = createComposeRule()

    private val planes = FakePlanRepository()
    private val transacciones = FakeTransaccionRepository()
    private val ajustes = FakeAjustesRepository()
    private val cuentas = FakeCuentaRepository()
    private val cierres = FakeCierreRepository()
    private val marzo = Mes.de(2026, 3)

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun abrirPantalla(hoy: Int = DIA) {
        val viewModel =
            ResumenViewModel(
                planes = planes,
                transacciones = transacciones,
                ajustes = ajustes,
                cuentas = cuentas,
                cierres = cierres,
                calendario =
                    object : Calendario {
                        override fun hoy() = LocalDate(marzo.anio, marzo.numeroDeMes, hoy)
                    },
                calcular = CalcularResumenMensualUseCase(),
                calcularPorCuentas = CalcularPlanPorCuentasUseCase(),
            )

        compose.setContent {
            MiPlataTheme {
                PantallaResumen(viewModel = viewModel)
            }
        }
    }

    private fun planDeMarzo(vararg lineas: LineaDePlan) =
        runBlocking {
            planes.guardar(PlanMensual(id = PlanId("p"), mes = marzo, lineas = lineas.toList()))
        }

    private fun linea(
        id: String,
        nombre: String,
        tipo: TipoDeLinea,
        monto: Long,
    ) = LineaDePlan(
        id = LineaId(id),
        nombre = nombre,
        tipo = tipo,
        montoPlanificado = Money.deUnidades(monto),
    )

    private fun anotarGasto(
        monto: Long,
        lineaId: String? = null,
    ) = runBlocking {
        transacciones.guardar(
            Transaccion(
                id = TransaccionId("g$monto"),
                fecha = LocalDate(2026, 3, DIA),
                monto = Money.deUnidades(monto),
                tipo = TipoDeTransaccion.GASTO,
                cuentaOrigenId = CuentaId("c1"),
                lineaDePlanId = lineaId?.let(::LineaId),
            ),
        )
    }

    private fun anotarIngreso(monto: Long) =
        runBlocking {
            transacciones.guardar(
                Transaccion(
                    id = TransaccionId("i$monto"),
                    fecha = LocalDate(2026, 3, DIA),
                    monto = Money.deUnidades(monto),
                    tipo = TipoDeTransaccion.INGRESO,
                    cuentaOrigenId = CuentaId("c1"),
                ),
            )
        }

    @Test
    fun `sin plan invita a hacer uno`() {
        abrirPantalla()

        compose
            .onNodeWithText("Aún no has planificado este mes. Ve a Plan y anota tus ingresos y gastos.")
            .assertIsDisplayed()
    }

    // Lo que hace util a la app antes de anotar un solo movimiento: el plan solo
    // ya dice si cuadra sobre el papel.
    @Test
    fun `un plan sin movimientos ya ensena lo planificado`() {
        planDeMarzo(
            linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000),
            linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
        )
        abrirPantalla()

        // Lo planeado se lee junto a cada concepto, antes de anotar nada.
        verDesplazando("Plan y realidad")
        verDesplazando("de $2,000.00 planeados")
        verDesplazando("de $450.00 planeados")
        // Y la cifra grande dice contra que se compara: 2000 - 450 planeados.
        verDesplazando("de $1,550.00 planeados")
    }

    // El circuito completo: un gasto anotado mueve las cifras del resumen.
    @Test
    fun `un gasto anotado aparece en el resumen`() {
        planDeMarzo(
            linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000),
            linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 400),
        )
        anotarIngreso(2000)
        anotarGasto(120, lineaId = "f")
        abrirPantalla()

        // Ingresos 2000, gastos 120: quedan 1880.
        compose.onNodeWithText("Te queda").assertIsDisplayed()
        compose.onAllNodesWithText("$1,880.00")[0].assertIsDisplayed()
        verDesplazando("$120.00")
    }

    // La distincion que justifica la pantalla: el mes puede estar en rojo porque
    // gastaste de mas o porque el ingreso no llego, y no se arreglan igual.
    @Test
    fun `un ingreso que no llega se explica como tal`() {
        planDeMarzo(
            linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000),
            linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
        )
        anotarGasto(450, lineaId = "f")
        abrirPantalla()

        compose.onNodeWithText("Te faltan").assertIsDisplayed()
        compose
            .onNodeWithText(
                "El mes está en rojo porque falta ingreso, no porque hayas gastado de más. " +
                    "Recortar no lo arregla: revisa qué cobro no ha llegado.",
            ).assertIsDisplayed()
    }

    // La senal util del resumen: a dia 2 con el 80% del presupuesto gastado el
    // total todavia cuadra, pero el ritmo no.
    @Test
    fun `avisa cuando el gasto va mas deprisa que el mes`() {
        planDeMarzo(linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 400))
        anotarGasto(320, lineaId = "f")
        abrirPantalla(hoy = 2)

        verDesplazando("Ritmo del mes")
        verDesplazando("Estás gastando más deprisa de lo que pasa el mes.")
    }

    @Test
    fun `las desviaciones desfavorables se listan`() {
        planDeMarzo(linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 400))
        anotarGasto(520, lineaId = "f")
        abrirPantalla()

        verDesplazando("Dónde te has desviado")
        // La ventana de Robolectric es pequena y la fila cae por debajo del
        // borde; como la lista es perezosa, ni siquiera se compone hasta que se
        // llega a ella. Se desplaza hasta alli. Lo que se comprueba es que la
        // desviacion se calcula y llega a la lista -planificaste 400, llevas
        // 520, sobran 120-, no en que pixel acaba dibujada.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("+$120.00"))
        compose.onNodeWithText("Comida").assertExists()
        compose.onNodeWithText("+$120.00").assertExists()
    }

    @Test
    fun `navegar de mes cambia lo que se ensena`() {
        planDeMarzo(linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000))
        abrirPantalla()

        compose.onNodeWithText("Marzo 2026").assertIsDisplayed()

        compose.onNodeWithContentDescription("Mes anterior").performClick()

        compose.onNodeWithText("Febrero 2026").assertIsDisplayed()
        // Febrero no tiene plan, asi que vuelve el aviso de siempre.
        compose
            .onNodeWithText("Aún no has planificado este mes. Ve a Plan y anota tus ingresos y gastos.")
            .assertIsDisplayed()
    }

    /**
     * Desplaza la lista hasta [texto] y comprueba que se ve.
     *
     * El titulo grande y la escala aurea dejan esas filas por debajo de la
     * ventana de Robolectric, que es pequeña; en un movil tambien pueden quedar
     * por debajo, y se llega a ellas igual: desplazando.
     */
    private fun verDesplazando(texto: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(texto))
        compose.onNodeWithText(texto).assertIsDisplayed()
    }

    private companion object {
        const val DIA = 15
    }
}
