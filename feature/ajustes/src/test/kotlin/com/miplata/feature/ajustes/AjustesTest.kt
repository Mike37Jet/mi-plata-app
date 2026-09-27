package com.miplata.feature.ajustes

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.FakeAjustesRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Elegir el tema y llegar a la copia de seguridad, desde la pantalla de verdad. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AjustesTest {
    @get:Rule
    val compose = createComposeRule()

    private val ajustes = FakeAjustesRepository()
    private var abrioLaCopia = false

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun abrir() {
        val viewModel = AjustesViewModel(ajustes)
        compose.setContent {
            MiPlataTheme {
                PantallaAjustes(alVolver = {}, alAbrirCopia = { abrioLaCopia = true }, viewModel = viewModel)
            }
        }
    }

    private fun elegida(si: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, si)

    @Test
    fun `por defecto sigue al telefono`() {
        abrir()

        compose.onNodeWithText("Según el teléfono").assert(elegida(true))
        compose.onNodeWithText("Oscuro").assert(elegida(false))
    }

    @Test
    fun `elegir un tema lo guarda y lo marca`() {
        abrir()

        compose.onNodeWithText("Oscuro").performClick()

        runBlocking { ajustes.obtener() }.tema shouldBe Tema.OSCURO
        compose.onNodeWithText("Oscuro").assert(elegida(true))
        compose.onNodeWithText("Según el teléfono").assert(elegida(false))
    }

    /** Cambiar el tema no toca nada mas: la moneda y el mes siguen como estaban. */
    @Test
    fun `elegir un tema no cambia el resto de ajustes`() {
        runBlocking { ajustes.guardar(Ajustes(primerDiaDelMesFinanciero = 25)) }
        abrir()

        compose.onNodeWithText("Claro").performClick()

        runBlocking { ajustes.obtener() }.primerDiaDelMesFinanciero shouldBe 25
    }

    @Test
    fun `la copia dice cuando se hizo la ultima y lleva a ella`() {
        val jueves = LocalDateTime(2026, 3, 26, 10, 0).toInstant(TimeZone.currentSystemDefault())
        runBlocking { ajustes.guardar(Ajustes(ultimoBackupEnMillis = jueves.toEpochMilliseconds())) }
        abrir()

        compose.onNodeWithText("Última: Jueves, 26 de marzo").assertIsDisplayed()
        compose.onNodeWithText("Copia de seguridad").performClick()

        abrioLaCopia shouldBe true
    }

    @Test
    fun `sin ninguna copia lo dice`() {
        abrir()

        compose.onNodeWithText("Aún no has hecho ninguna").assertIsDisplayed()
    }
}
