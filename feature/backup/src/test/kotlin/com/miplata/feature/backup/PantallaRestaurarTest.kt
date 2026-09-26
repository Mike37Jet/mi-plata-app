package com.miplata.feature.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.miplata.core.backup.Manifiesto
import com.miplata.core.backup.ParametrosDeCifrado
import com.miplata.core.backup.Recuento
import com.miplata.core.designsystem.theme.MiPlataTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val MANIFIESTO =
    Manifiesto(
        versionDelEsquema = 1,
        versionDeLaApp = "0.1.0",
        creadoEnMillis = 1_785_000_000_000L,
        dispositivo = "Movil viejo",
        checksum = "",
        cifrado = ParametrosDeCifrado(iteraciones = 1, salt = "", iv = ""),
        contenido = Recuento(cuentas = 2, categorias = 19, transacciones = 40, planes = 3),
    )

private val RESUMEN =
    PasoDeRestauracion.Resumen(copia = MANIFIESTO, actual = Recuento(cuentas = 5, transacciones = 900))

/**
 * La pantalla de restaurar, sin el selector de archivos.
 *
 * Lo que mas importa es que el dialogo de confirmacion se interponga de verdad:
 * pulsar "Restaurar" no puede restaurar, solo preguntar.
 */
@RunWith(RobolectricTestRunner::class)
class PantallaRestaurarTest {
    @get:Rule
    val compose = createComposeRule()

    private var restauraciones = 0
    private var deshechas = 0

    private fun abrir(estado: RestaurarUiState) {
        compose.setContent {
            var frase by remember { mutableStateOf("") }
            MiPlataTheme {
                PantallaRestaurar(
                    estado = estado,
                    frase = frase,
                    alCambiarFrase = { frase = it },
                    alElegirArchivo = {},
                    alRestaurar = { restauraciones++ },
                    alDeshacer = { deshechas++ },
                    alVolverAlInicio = {},
                    alVolver = {},
                )
            }
        }
    }

    // Lo que evita restaurar la copia equivocada: los dos recuentos a la vista.
    @Test
    fun `el resumen compara lo que trae la copia con lo que hay ahora`() {
        abrir(RestaurarUiState(paso = RESUMEN))

        compose.onNodeWithText("2 cuentas · 40 movimientos · 3 meses planificados · 19 categorías").assertIsDisplayed()
        compose.onNodeWithText("5 cuentas · 900 movimientos · 0 meses planificados · 0 categorías").assertIsDisplayed()
        compose.onNodeWithText("Movil viejo", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sin frase no se puede restaurar`() {
        abrir(RestaurarUiState(paso = RESUMEN))

        compose.onNodeWithText("Restaurar").assertIsNotEnabled()
    }

    // Pulsar "Restaurar" solo pregunta. Hasta confirmar no se pide nada.
    @Test
    fun `restaurar pide confirmacion antes de hacer nada`() {
        abrir(RestaurarUiState(paso = RESUMEN))

        compose.onNodeWithText("Frase de la copia").performTextInput("correcto caballo bateria grapa")
        compose.onNodeWithText("Restaurar").assertIsEnabled().performClick()

        compose.onNodeWithText("¿Sustituir todos tus datos?").assertIsDisplayed()
        restauraciones shouldBe 0
    }

    // El dialogo es otra ventana: se pulsa por semantica (docs/02, trampas de
    // Robolectric), o el toque se perderia y el test pasaria sin comprobar nada.
    @Test
    fun `solo al confirmar se restaura`() {
        abrir(RestaurarUiState(paso = RESUMEN))
        compose.onNodeWithText("Frase de la copia").performTextInput("correcto caballo bateria grapa")
        compose.onNodeWithText("Restaurar").performClick()

        compose.onNodeWithText("Sustituir").performSemanticsAction(SemanticsActions.OnClick)

        restauraciones shouldBe 1
    }

    @Test
    fun `cancelar la confirmacion no restaura`() {
        abrir(RestaurarUiState(paso = RESUMEN))
        compose.onNodeWithText("Frase de la copia").performTextInput("correcto caballo bateria grapa")
        compose.onNodeWithText("Restaurar").performClick()

        compose.onNodeWithText("Cancelar").performSemanticsAction(SemanticsActions.OnClick)

        restauraciones shouldBe 0
        compose.onNodeWithText("¿Sustituir todos tus datos?").assertDoesNotExist()
    }

    @Test
    fun `una frase incorrecta se explica junto al campo`() {
        abrir(RestaurarUiState(paso = RESUMEN.copy(fraseIncorrecta = true)))

        compose.onNodeWithText("La frase no abre esta copia", substring = true).assertIsDisplayed()
    }

    // Volver atras no desaparece al salir: mientras haya copia previa, se ofrece.
    @Test
    fun `al entrar se ofrece volver a los datos anteriores`() {
        abrir(RestaurarUiState(copiaPreviaEnMillis = 1_790_000_000_000L))

        compose.onNodeWithText("Volver a los datos del", substring = true).performClick()

        deshechas shouldBe 1
    }

    @Test
    fun `sin copia previa no se ofrece volver atras`() {
        abrir(RestaurarUiState())

        compose.onNodeWithText("Volver a los datos del", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tras restaurar se ofrece deshacer en el acto`() {
        abrir(RestaurarUiState(paso = PasoDeRestauracion.Restaurada(), copiaPreviaEnMillis = 1L))

        compose.onNodeWithText("Copia restaurada.").assertIsDisplayed()
        compose.onNodeWithText("Deshacer").performClick()
        deshechas shouldBe 1
    }
}
