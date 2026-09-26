package com.miplata.core.designsystem.componentes

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.MiPlataTheme
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La fila de las listas agrupadas: su tamaño de toque, su pista de que se
 * puede pulsar y que el importe no aplaste el titulo con letra grande.
 *
 * Graficos nativos porque lo que se comprueba es donde cae cada texto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class FilaDeListaTest {
    @get:Rule
    val compose = createComposeRule()

    private val esBoton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun fila(alPulsar: (() -> Unit)? = null) {
        compose.setContent {
            MiPlataTheme {
                GrupoDeLista {
                    FilaDeLista(
                        titulo = "Cuenta nómina",
                        detalle = "Banco",
                        final = { Text("€4,400.00") },
                        alPulsar = alPulsar,
                    )
                }
            }
        }
    }

    @Test
    fun `una fila que se puede pulsar se anuncia como boton y mide un paso de la escala`() {
        fila(alPulsar = {})

        compose.onNode(esBoton).assertHeightIsAtLeast(Espacio.xl)
    }

    @Test
    fun `una fila que no se puede pulsar no se anuncia como boton`() {
        fila()

        compose.onAllNodes(esBoton).fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `a tamaño normal, el importe va en la misma linea que el titulo`() {
        fila()

        val titulo = compose.onNodeWithText("Cuenta nómina").getUnclippedBoundsInRoot()
        val importe = compose.onNodeWithText("€4,400.00").getUnclippedBoundsInRoot()
        importe.top shouldBeLessThan titulo.bottom
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `con letra grande, el importe baja a su propia linea`() {
        fila()

        val detalle = compose.onNodeWithText("Banco").getUnclippedBoundsInRoot()
        val importe = compose.onNodeWithText("€4,400.00").getUnclippedBoundsInRoot()
        importe.top shouldBeGreaterThanOrEqualTo detalle.bottom
    }
}
