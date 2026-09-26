package com.miplata.core.designsystem.accesibilidad

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Una etiqueta de la barra de navegacion con la letra al 200%.
 *
 * Cada pestaña tiene un cuarto del ancho del movil. Con un Text normal,
 * "Movimientos" se partia a mitad de palabra en dos lineas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp", fontScale = 2.0f)
class TextoQueCabeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun medir(contenido: @Composable () -> Unit): TextLayoutResult {
        compose.setContent { MaterialTheme { Box(Modifier.width(ANCHO_DE_UNA_PESTANA)) { contenido() } } }
        val medidas = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(PALABRA_LARGA)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(medidas)
        return medidas.single()
    }

    @Test
    fun `una palabra larga cabe entera en una linea`() {
        val medida = medir { TextoQueCabe(PALABRA_LARGA) }

        medida.lineCount shouldBe 1
        medida.hasVisualOverflow.shouldBeFalse()
    }

    /** El caso que arregla: sin esto, el test anterior podria pasar por casualidad. */
    @Test
    fun `un texto normal en el mismo sitio se parte`() {
        val medida = medir { Text(PALABRA_LARGA) }

        (medida.lineCount > 1) shouldBe true
    }

    /** Solo se encoge lo que no cabe: la letra grande sigue siendo grande. */
    @Test
    fun `una palabra que ya cabe no se encoge`() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(ANCHO_DE_UNA_PESTANA)) {
                    Text(PALABRA_CORTA, Modifier.testTag("normal"))
                    TextoQueCabe(PALABRA_CORTA, Modifier.testTag("que cabe"))
                }
            }
        }

        compose.onNodeWithTag("que cabe").fetchSemanticsNode().size shouldBe
            compose.onNodeWithTag("normal").fetchSemanticsNode().size
    }

    private companion object {
        const val PALABRA_CORTA = "Plan"

        const val PALABRA_LARGA = "Movimientos"

        // 360dp entre cuatro pestañas, menos el relleno de cada una.
        val ANCHO_DE_UNA_PESTANA = 82.dp
    }
}
