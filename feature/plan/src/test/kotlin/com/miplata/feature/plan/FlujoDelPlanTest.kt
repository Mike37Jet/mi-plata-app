package com.miplata.feature.plan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.MaterializarPlanDelMesUseCase
import io.kotest.matchers.shouldBe
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
 * Flujo critico 1: armar el plan del mes.
 *
 * Es lo primero que hace alguien que abre la app, y lo unico imprescindible
 * para que el resto sirva: sin plan no hay con que comparar la realidad
 * (docs/00). Si esto se rompe, la app no vale para nada, aunque todo lo demas
 * este verde.
 *
 * Se prueba la pantalla **de verdad** -Compose real, ViewModel real, casos de
 * uso reales-, con los repositorios en memoria en lugar de Room. Corre con
 * Robolectric en la JVM, asi que se ejecuta en cada PR: un test de interfaz que
 * necesite un emulador acaba sin ejecutarse nunca (docs/02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlujoDelPlanTest {
    @get:Rule
    val compose = createComposeRule()

    private val planes = FakePlanRepository()
    private val ajustes = FakeAjustesRepository()
    private val marzo = Mes.de(2026, 3)

    @Before
    fun fijarDispatcher() {
        // Unconfined y no Standard: aqui interesa que el estado llegue a la
        // pantalla sin tener que adelantar el reloj a mano desde el test.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun abrirPantalla() {
        val ids = GeneradorDeIdsSecuencial()
        val viewModel =
            PlanViewModel(
                planes = planes,
                abrirPlan = AbrirPlanDelMesUseCase(planes, MaterializarPlanDelMesUseCase(ids)),
                ids = ids,
                ajustes = ajustes,
                calendario =
                    object : Calendario {
                        override fun hoy() = LocalDate(marzo.anio, marzo.numeroDeMes, 1)
                    },
            )

        compose.setContent {
            MiPlataTheme {
                PantallaPlan(viewModel = viewModel)
            }
        }
    }

    @Test
    fun `anotar un ingreso y un gasto deja ver el disponible`() {
        abrirPantalla()

        // El plan vacio invita a empezar por los ingresos, que es el orden en el
        // que la gente piensa su mes.
        compose.onNodeWithText("Aún no hay nada. Empieza por tus ingresos.").assertIsDisplayed()

        anadirEn("Ingresos", nombre = "Sueldo", monto = "2000")
        anadirEn("Gastos fijos", nombre = "Arriendo", monto = "450")

        // 2000 - 450. Es la cifra que responde "¿me alcanza?" antes de haber
        // anotado un solo movimiento, que es el punto entero del modelo.
        compose.onNodeWithText("Disponible").assertIsDisplayed()
        compose.onAllNodesWithText("$1,550.00").onFirst().assertIsDisplayed()
    }

    @Test
    fun `lo que se anota queda guardado`() {
        abrirPantalla()

        anadirEn("Ingresos", nombre = "Sueldo", monto = "2000")

        compose.waitUntil(ESPERA) { runBlocking { planes.obtenerDe(marzo) } != null }
        val guardado = requireNotNull(runBlocking { planes.obtenerDe(marzo) })
        guardado.lineasActivas.single().nombre shouldBe "Sueldo"
    }

    /**
     * Anade una linea a una seccion y la rellena.
     *
     * La fila nueva es la ultima de su tipo, asi que se localizan los campos por
     * su marcador de posicion: es lo mismo que ve el usuario cuando la fila
     * aparece vacia.
     */
    private fun anadirEn(
        seccion: String,
        nombre: String,
        monto: String,
    ) {
        compose.onNodeWithText(seccion).assertIsDisplayed()
        botonAnadirDe(seccion).performClick()

        compose.onNodeWithText("Sin nombre").performTextInput(nombre)
        compose
            .onAllNodesWithText("0")
            .filterToOne(hasSetTextAction())
            .performTextInput(monto)
    }

    private fun botonAnadirDe(seccion: String) = compose.onAllNodesWithText("Añadir")[indiceDeSeccion(seccion)]

    private fun indiceDeSeccion(seccion: String) =
        when (seccion) {
            "Ingresos" -> 0
            "Gastos fijos" -> 1
            "Gastos variables" -> 2
            else -> 3
        }

    private companion object {
        const val ESPERA = 5_000L
    }
}
