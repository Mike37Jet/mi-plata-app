package com.miplata.feature.cuentas

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El campo del nombre no puede depender de que el estado vuelva a tiempo.
 *
 * Lo que se escribe sube al ViewModel y vuelve por un `StateFlow`, y nada
 * garantiza que esa vuelta llegue antes de la siguiente recomposicion. Si el
 * campo tomara su texto de ahi, esa recomposicion lo devolveria al texto
 * anterior y la siguiente tecla se aplicaria encima: se pierden letras.
 *
 * Aqui el estado no vuelve nunca, que es el caso extremo. El campo tiene que
 * seguir mostrando lo tecleado y avisar de cada cambio completo.
 */
@RunWith(RobolectricTestRunner::class)
class EditorDeCuentaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `el nombre no pierde letras aunque el estado tarde en volver`() {
        val eventos = mutableListOf<EventoDeCuentas>()
        compose.setContent {
            MiPlataTheme {
                Editor(editor = EditorDeCuenta(), alEvento = { eventos += it })
            }
        }

        val campo = compose.onNode(hasSetTextAction() and hasText("Nombre"))
        "Banco".forEach { letra ->
            campo.performTextInput(letra.toString())
            compose.waitForIdle()
        }

        campo.assert(hasText("Banco"))
        eventos.filterIsInstance<EventoDeCuentas.CambiarNombre>().last().nombre shouldBe "Banco"
    }
}
