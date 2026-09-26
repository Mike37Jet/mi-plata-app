package com.miplata.feature.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val FRASE = "correcto caballo bateria grapa"

/**
 * La pantalla de la copia, sin el selector de archivos.
 *
 * El selector es una Activity del sistema que Robolectric no sabe abrir, asi
 * que aqui se prueba lo que depende de esta pantalla: que no deja guardar hasta
 * que la frase vale, y que avisa de lo que hay que avisar. La escritura del
 * archivo esta probada en el ViewModel, y el recorrido completo, a mano en el
 * emulador.
 */
@RunWith(RobolectricTestRunner::class)
class PantallaCopiaTest {
    @get:Rule
    val compose = createComposeRule()

    private var guardados = 0

    private val frecuenciasElegidas = mutableListOf<FrecuenciaDeRecordatorio>()
    private var activaciones = 0

    private fun abrir(
        estado: CopiaUiState = CopiaUiState(),
        notificacionesActivas: Boolean = true,
    ) {
        compose.setContent {
            var frase by androidx.compose.runtime.remember { mutableStateOf("") }
            var confirmacion by androidx.compose.runtime.remember { mutableStateOf("") }
            var riesgo by androidx.compose.runtime.remember { mutableStateOf(false) }

            MiPlataTheme {
                PantallaCopia(
                    estado = estado,
                    frase = frase,
                    confirmacion = confirmacion,
                    riesgoAsumido = riesgo,
                    alCambiarFrase = { frase = it },
                    alCambiarConfirmacion = { confirmacion = it },
                    alCambiarRiesgo = { riesgo = it },
                    alGuardar = { guardados++ },
                    alDescartarAviso = {},
                    alVolver = {},
                    notificacionesActivas = notificacionesActivas,
                    alCambiarFrecuencia = { frecuenciasElegidas += it },
                    alActivarNotificaciones = { activaciones++ },
                )
            }
        }
    }

    // docs/05 exige avisar "con todas sus letras" de que sin frase no hay copia.
    @Test
    fun `avisa de que perder la frase es perder la copia`() {
        abrir()

        compose.onNodeWithText("Si olvidas la frase", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Entiendo que si pierdo la frase, pierdo la copia.").assertIsDisplayed()
    }

    @Test
    fun `sin frase no se puede guardar`() {
        abrir()

        compose.onNodeWithText("Guardar copia").assertIsNotEnabled()
    }

    @Test
    fun `con la frase repetida y el riesgo asumido se puede guardar`() {
        abrir()

        compose.onNodeWithText("Frase de respaldo").performTextInput(FRASE)
        compose.onNodeWithText("Repite la frase").performTextInput(FRASE)
        compose.onNodeWithText("Entiendo que si pierdo la frase, pierdo la copia.").performClick()

        compose.onNodeWithText("Guardar copia").assertIsEnabled().performClick()
        guardados shouldBe 1
    }

    // La segunda vez que se avisa del riesgo es esta casilla: sin marcarla, la
    // frase puede ser perfecta y aun asi no se deja continuar.
    @Test
    fun `sin marcar la casilla no se puede guardar aunque la frase valga`() {
        abrir()

        compose.onNodeWithText("Frase de respaldo").performTextInput(FRASE)
        compose.onNodeWithText("Repite la frase").performTextInput(FRASE)

        compose.onNodeWithText("Guardar copia").assertIsNotEnabled()
        compose.onNodeWithText("Marca la casilla para confirmar que lo entiendes.").assertIsDisplayed()
    }

    @Test
    fun `si la repeticion no coincide se dice`() {
        abrir()

        compose.onNodeWithText("Frase de respaldo").performTextInput(FRASE)
        compose.onNodeWithText("Repite la frase").performTextInput("otra frase distinta")

        compose.onNodeWithText("Las dos frases no coinciden.").assertIsDisplayed()
        compose.onNodeWithText("Guardar copia").assertIsNotEnabled()
    }

    // Regañar sobre un campo vacio es regañar antes de tiempo.
    @Test
    fun `con los campos vacios no se muestra ningun error`() {
        abrir()

        compose.onNodeWithText("La frase es demasiado corta.").assertDoesNotExist()
    }

    @Test
    fun `sin copias previas lo dice`() {
        abrir()

        compose.onNodeWithText("Todavía no has hecho ninguna copia.").assertIsDisplayed()
    }

    @Test
    fun `mientras cifra no deja guardar otra vez y explica la espera`() {
        abrir(CopiaUiState(exportacion = Exportacion.EnCurso))

        compose.onNodeWithText("Guardar copia").assertIsNotEnabled()
        compose.onNodeWithText("Cifrando la copia", substring = true).assertIsDisplayed()
    }

    @Test
    fun `al terminar dice el nombre del archivo`() {
        abrir(CopiaUiState(exportacion = Exportacion.Terminada("miplata-backup-2026-09-26-1042.mpb")))

        compose
            .onNodeWithText("Copia guardada: miplata-backup-2026-09-26-1042.mpb")
            .assertIsDisplayed()
    }

    @Test
    fun `un fallo se cuenta con su motivo`() {
        abrir(CopiaUiState(exportacion = Exportacion.Fallida("Sin espacio en el destino")))

        compose
            .onNodeWithText("No se pudo guardar la copia: Sin espacio en el destino")
            .assertIsDisplayed()
    }

    @Test
    fun `se puede elegir cada cuanto recordar`() {
        abrir()

        compose.onNodeWithText("Cada semana").performScrollTo().performClick()

        frecuenciasElegidas shouldBe listOf(FrecuenciaDeRecordatorio.SEMANAL)
    }

    // Sin este aviso, el usuario elegiria "cada mes", creeria estar cubierto, y
    // no le llegaria nada nunca.
    @Test
    fun `con las notificaciones apagadas avisa de que no vera el recordatorio`() {
        abrir(notificacionesActivas = false)

        compose
            .onNodeWithText(
                "Las notificaciones están desactivadas",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Activar notificaciones").performScrollTo().performClick()
        activaciones shouldBe 1
    }

    // Quien eligio no recibir recordatorios no necesita notificaciones: el
    // aviso seria ruido.
    @Test
    fun `sin recordatorio no se habla de notificaciones`() {
        abrir(CopiaUiState(frecuencia = FrecuenciaDeRecordatorio.NUNCA), notificacionesActivas = false)

        compose.onNodeWithText("Las notificaciones están desactivadas", substring = true).assertDoesNotExist()
    }
}
