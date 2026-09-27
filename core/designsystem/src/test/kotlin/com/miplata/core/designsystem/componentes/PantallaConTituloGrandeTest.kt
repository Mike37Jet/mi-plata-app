package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.theme.MiPlataTheme
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El contenido pasa por detras de la barra inferior de cristal, pero nada que
 * haga falta puede quedarse escondido ahi: ni la ultima fila de la lista ni el
 * boton flotante.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class PantallaConTituloGrandeTest {
    @get:Rule
    val compose = createComposeRule()

    // Lo que mide de verdad: 80dp la barra, mas la navegacion del sistema. Mas
    // que el margen extra del final de la lista (89dp): con una barra mas baja,
    // ese margen solo ya la cubria y el test no veia si faltaba su hueco.
    private val barra = 120.dp
    private var altoDeLaPantalla: Dp = 0.dp

    private fun pantalla() {
        compose.setContent {
            MiPlataTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    altoDeLaPantalla = maxHeight
                    CompositionLocalProvider(LocalEspacioDeLaBarraInferior provides barra) {
                        PantallaConTituloGrande(
                            titulo = "Movimientos",
                            botonFlotante = {
                                FloatingActionButton(onClick = {}, modifier = Modifier.testTag("mas")) { Text("+") }
                            },
                        ) { relleno ->
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = relleno) {
                                items(FILAS) { i -> Box { Text("Fila $i") } }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `la ultima fila puede subir por encima de la barra`() {
        pantalla()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Fila ${FILAS - 1}"))

        compose.onNodeWithText("Fila ${FILAS - 1}").getUnclippedBoundsInRoot().bottom shouldBeLessThanOrEqualTo
            altoDeLaPantalla - barra
    }

    @Test
    fun `el boton flotante queda por encima de la barra`() {
        pantalla()

        compose.onNodeWithTag("mas").getUnclippedBoundsInRoot().bottom shouldBeLessThanOrEqualTo
            altoDeLaPantalla - barra
    }

    private companion object {
        const val FILAS = 40
    }
}
