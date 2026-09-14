package com.miplata.feature.resumen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
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
                calendario =
                    object : Calendario {
                        override fun hoy() = LocalDate(marzo.anio, marzo.numeroDeMes, hoy)
                    },
                calcular = CalcularResumenMensualUseCase(),
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

        compose.onNodeWithText("Plan y realidad").assertIsDisplayed()
        compose.onAllNodesWithText("$2,000.00")[0].assertIsDisplayed()
        compose.onAllNodesWithText("$450.00")[0].assertIsDisplayed()
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
        compose.onAllNodesWithText("$120.00")[0].assertIsDisplayed()
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

        compose.onNodeWithText("Ritmo del mes").assertIsDisplayed()
        compose
            .onNodeWithText("Estás gastando más deprisa de lo que pasa el mes.")
            .assertIsDisplayed()
    }

    @Test
    fun `las desviaciones desfavorables se listan`() {
        planDeMarzo(linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 400))
        anotarGasto(520, lineaId = "f")
        abrirPantalla()

        compose.onNodeWithText("Donde te has desviado").assertIsDisplayed()
        // `assertExists` y no `assertIsDisplayed`: la ventana de Robolectric es
        // pequena y la fila cae por debajo del borde. Lo que se comprueba aqui
        // es que la desviacion se calcula y llega a la lista -planificaste 400,
        // llevas 520, sobran 120-, no en que pixel acaba dibujada.
        compose.onNodeWithText("Comida").assertExists()
        compose.onNodeWithText("+$120.00").assertExists()
    }

    @Test
    fun `navegar de mes cambia lo que se ensena`() {
        planDeMarzo(linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000))
        abrirPantalla()

        compose.onNodeWithText("2026-03").assertIsDisplayed()

        compose.onNodeWithContentDescription("Mes anterior").performClick()

        compose.onNodeWithText("2026-02").assertIsDisplayed()
        // Febrero no tiene plan, asi que vuelve el aviso de siempre.
        compose
            .onNodeWithText("Aún no has planificado este mes. Ve a Plan y anota tus ingresos y gastos.")
            .assertIsDisplayed()
    }

    private companion object {
        const val DIA = 15
    }
}
