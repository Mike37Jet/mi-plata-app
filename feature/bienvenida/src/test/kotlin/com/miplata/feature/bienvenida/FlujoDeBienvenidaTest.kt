package com.miplata.feature.bienvenida

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.usecase.CompletarPrimerosPasosUseCase
import io.kotest.matchers.nulls.shouldNotBeNull
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
 * La primera vez que se abre la app, de la primera pantalla al plan.
 *
 * Es el primer contacto con la app: si se atasca aqui, no hay segunda vez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlujoDeBienvenidaTest {
    @get:Rule
    val compose = createComposeRule()

    private val ajustes = FakeAjustesRepository()
    private val cuentas = FakeCuentaRepository()
    private val planes = FakePlanRepository()

    private var pidioRestaurar = false
    private var cuentasAlTerminar: Int? = null

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun abrir() {
        val viewModel =
            BienvenidaViewModel(
                CompletarPrimerosPasosUseCase(
                    ajustes,
                    cuentas,
                    planes,
                    GeneradorDeIdsSecuencial(),
                    object : Calendario {
                        override fun hoy() = LocalDate(2026, 3, 10)
                    },
                ),
            )
        compose.setContent {
            MiPlataTheme(colorDinamico = false) {
                PantallaBienvenida(
                    alTenerCopia = { pidioRestaurar = true },
                    alTerminar = { cuentasAlTerminar = runBlocking { cuentas.observarTodas().first().size } },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun pulsar(texto: String) = compose.onNodeWithText(texto).performScrollTo().performClick()

    @Test
    fun `de la bienvenida al plan`() {
        abrir()

        pulsar("Empezar")
        compose.onNodeWithText("Paso 1 de 3").assertIsDisplayed()
        pulsar("Siguiente")

        compose.onNodeWithText("Paso 2 de 3").assertIsDisplayed()
        // Sin nombre, la cuenta no se puede crear: se ve antes de pulsar.
        compose.onNodeWithText("Siguiente").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextInput("Cartera")
        compose.onNodeWithText("Siguiente").assertIsEnabled()
        pulsar("Siguiente")

        compose.onNodeWithText("Paso 3 de 3").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Ingreso mensual")).performTextInput("1200")
        pulsar("Empezar a planear")

        compose.waitUntil(ESPERA) { runBlocking { cuentas.observarTodas().first() }.isNotEmpty() }
        runBlocking { cuentas.observarTodas().first() }.single().nombre shouldBe "Cartera"
        compose.waitUntil(ESPERA) { runBlocking { planes.obtenerDe(Mes.de(2026, 3)) } != null }
        val ingreso = runBlocking { planes.obtenerDe(Mes.de(2026, 3)) }.shouldNotBeNull().lineas.single()
        ingreso.nombre shouldBe "Sueldo"
        ingreso.montoPlanificado shouldBe Money.deUnidades(1200)
    }

    /**
     * `:app` usa el aviso para saber que tiene que abrir el plan. Tiene que
     * llegar antes de que exista la cuenta: en cuanto existe, la bienvenida se
     * cierra sola y ya no habria a quien avisar.
     */
    @Test
    fun `el aviso de terminar llega antes de guardar`() {
        abrir()
        pulsar("Empezar")
        pulsar("Siguiente")
        compose.onNode(hasSetTextAction() and hasText("Nombre")).performTextInput("Cartera")
        pulsar("Siguiente")

        pulsar("Lo haré después")

        cuentasAlTerminar shouldBe 0
    }

    @Test
    fun `quien ya tiene una copia puede ir a restaurarla desde el principio`() {
        abrir()

        pulsar("Ya tengo una copia de seguridad")

        pidioRestaurar shouldBe true
    }

    private companion object {
        const val ESPERA = 5_000L
    }
}
