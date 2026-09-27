package com.miplata.feature.plan

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.repository.PlanRepository
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
    private val transacciones = FakeTransaccionRepository()
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

    private fun abrirPantalla(repositorio: PlanRepository = planes) {
        val ids = GeneradorDeIdsSecuencial()
        val viewModel =
            PlanViewModel(
                planes = repositorio,
                transacciones = transacciones,
                abrirPlan = AbrirPlanDelMesUseCase(repositorio, MaterializarPlanDelMesUseCase(ids)),
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

        anadir("Ingreso", nombre = "Sueldo", monto = "2000")
        anadir("Gasto fijo", nombre = "Arriendo", monto = "450")

        // 2000 - 450. Es la cifra que responde "¿me alcanza?" antes de haber
        // anotado un solo movimiento, que es el punto entero del modelo.
        compose.onNodeWithText("Disponible").assertIsDisplayed()
        compose.onAllNodesWithText("$1,550.00").onFirst().assertIsDisplayed()
    }

    @Test
    fun `lo que se anota queda guardado`() {
        abrirPantalla()

        anadir("Ingreso", nombre = "Sueldo", monto = "2000")

        compose.waitUntil(ESPERA) { runBlocking { planes.obtenerDe(marzo) } != null }
        val guardado = requireNotNull(runBlocking { planes.obtenerDe(marzo) })
        guardado.lineasActivas.single().nombre shouldBe "Sueldo"
    }

    /**
     * Escribir en la hoja letra a letra, con una base que tarda, no pierde nada.
     *
     * Cuando se editaba en la propia fila, cada tecla se guardaba y volvia por el
     * flujo del plan; la recomposicion que llegaba antes devolvia el campo al
     * texto anterior, y "Transporte" quedo como "ransTe" en el emulador. La hoja
     * guarda el texto en si misma hasta pulsar Guardar.
     */
    @Test
    fun `escribir letra a letra en la hoja no pierde letras aunque la base tarde`() {
        val base = BaseQueRetiene(planes)
        abrirPantalla(base)
        compose.onNodeWithContentDescription("Añadir línea").performClick()

        base.retener = true
        "Sueldo".forEach { letra ->
            campo("Nombre").performTextInput(letra.toString())
            compose.waitForIdle()
        }
        campo("Nombre").assert(hasText("Sueldo"))
        enLaHoja("Guardar")
        base.soltar()

        compose.waitUntil(ESPERA) {
            runBlocking { planes.obtenerDe(marzo) }?.lineas?.singleOrNull()?.nombre == "Sueldo"
        }
    }

    @Test
    fun `eliminar desde la hoja se puede deshacer`() {
        abrirPantalla()
        anadir("Gasto fijo", nombre = "Arriendo", monto = "450")

        compose.onNodeWithText("Arriendo").performClick()
        enLaHoja("Eliminar")

        compose.onNodeWithText("«Arriendo» eliminada").assertIsDisplayed()
        compose.onNodeWithText("Arriendo").assertDoesNotExist()
        compose.onNodeWithText("Deshacer").performClick()

        compose.waitUntil(ESPERA) { compose.onAllNodesWithText("Arriendo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Arriendo").assertIsDisplayed()
    }

    /**
     * Al deslizar una linea para borrarla, la de debajo tiene que seguir viendose.
     *
     * Sin clave en las filas, Compose reutilizaba el estado del deslizamiento
     * por posicion: la linea siguiente heredaba el "deslizada" de la borrada y se
     * quedaba como una franja roja vacia.
     */
    @Test
    fun `deslizar una linea la borra y la siguiente sigue viendose`() {
        abrirPantalla()
        anadir("Gasto fijo", nombre = "Arriendo", monto = "450")
        anadir("Gasto fijo", nombre = "Luz", monto = "40")

        compose.onNodeWithText("Arriendo").performTouchInput { swipeLeft() }

        compose.waitUntil(ESPERA) { compose.onAllNodesWithText("Arriendo").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Luz").assertIsDisplayed()
    }

    /** Pulsa el "+", elige el tipo y rellena la hoja. */
    private fun anadir(
        tipo: String,
        nombre: String,
        monto: String,
    ) {
        compose.onNodeWithContentDescription("Añadir línea").performClick()
        enLaHoja(tipo)
        campo("Nombre").performTextInput(nombre)
        campo("Importe").performTextInput(monto)
        enLaHoja("Guardar")
        // Hasta que la hoja se cierra y la linea aparece en el plan: el nombre
        // escrito en el campo ya "existia" antes de guardar.
        compose.waitUntil(ESPERA) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(ESPERA) { compose.onAllNodesWithText(nombre).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun campo(etiqueta: String) = compose.onNode(hasSetTextAction() and hasText(etiqueta))

    /**
     * Pulsa algo de la hoja.
     *
     * Con la accion de pulsar de la semantica -la de un lector de pantalla- y no
     * con un toque simulado: en Robolectric el toque no llega a la hoja modal,
     * que vive en su propia ventana, y el test pulsaba Guardar sin que pasara
     * nada. En el telefono se comprobo a mano que el toque funciona.
     */
    private fun enLaHoja(texto: String) {
        compose.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
    }

    private companion object {
        const val ESPERA = 5_000L
    }
}

/**
 * Una base que, mientras [retener] este activo, guarda las escrituras sin
 * aplicarlas: el plan no vuelve a la pantalla hasta [soltar].
 */
private class BaseQueRetiene(
    private val real: FakePlanRepository,
) : PlanRepository by real {
    var retener = false
    private val retenidas = mutableListOf<PlanMensual>()

    override suspend fun guardar(plan: PlanMensual) {
        if (retener) retenidas += plan else real.guardar(plan)
    }

    fun soltar() {
        retener = false
        runBlocking { retenidas.forEach { real.guardar(it) } }
        retenidas.clear()
    }
}
