package com.miplata.feature.transacciones

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Mes
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La nota no puede depender de que el estado vuelva a tiempo.
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
class EditorDeMovimientoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `la nota no pierde letras aunque el estado tarde en volver`() {
        val eventos = mutableListOf<EventoDeMovimientos>()
        val editor = EditorDeMovimiento(fecha = LocalDate(2026, 3, 10))
        compose.setContent {
            MiPlataTheme {
                EditorDeMovimientoUi(
                    editor = editor,
                    estado = TransaccionesUiState(mes = Mes.de(2026, 3), editor = editor),
                    alEvento = { eventos += it },
                )
            }
        }

        val campo = compose.onNode(hasSetTextAction() and hasText("Nota"))
        "Cena".forEach { letra ->
            campo.performTextInput(letra.toString())
            compose.waitForIdle()
        }

        campo.assert(hasText("Cena"))
        eventos.filterIsInstance<EventoDeMovimientos.CambioDeCampo.Nota>().last().nota shouldBe "Cena"
    }
}
