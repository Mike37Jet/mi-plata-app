package com.miplata.feature.plan

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El plan con la letra del sistema al 200%, el maximo de Android 14.
 *
 * Con el importe en un ancho fijo, al 200% de "2000.00" solo asomaba el primer
 * digito: la pantalla que responde "¿me alcanza?" dejaba de decir cuanto.
 *
 * Corre con los graficos nativos de Robolectric porque lo que se comprueba es
 * cuanto mide el texto de verdad. Con los graficos de mentira, cada letra mide
 * lo mismo y el test no distinguiria un importe que cabe de uno que no.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp", fontScale = 2.0f)
class LetraGrandeEnElPlanTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `el importe de una linea se ve entero`() {
        compose.setContent {
            MiPlataTheme(colorDinamico = false) {
                PantallaPlan(estado = planConUnSueldoDe("2000"), alEvento = {})
            }
        }

        val campo = compose.onAllNodesWithText("2000.00").filterToOne(hasSetTextAction()).fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        campo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)

        // Lo que mide el texto contra el sitio que tiene. Un campo de una linea
        // no parte el texto: si no cabe, lo desplaza y esconde lo que sobra.
        medidas.single().size.width shouldBeLessThanOrEqual campo.size.width - relleno() * 2
    }

    @Test
    fun `el nombre de la linea tambien se ve`() {
        compose.setContent {
            MiPlataTheme(colorDinamico = false) {
                PantallaPlan(estado = planConUnSueldoDe("2000"), alEvento = {})
            }
        }

        val campo = compose.onAllNodesWithText("Sueldo").filterToOne(hasSetTextAction()).fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        campo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)

        medidas.single().size.width shouldBeLessThanOrEqual campo.size.width - relleno() * 2
    }

    private fun planConUnSueldoDe(monto: String): PlanUiState {
        val sueldo =
            LineaDePlan(
                id = LineaId("sueldo"),
                nombre = "Sueldo",
                tipo = TipoDeLinea.INGRESO,
                montoPlanificado = Money.deUnidades(monto.toLong()),
            )
        return PlanUiState(
            mes = Mes.de(2026, 3),
            secciones = listOf(SeccionDelPlan(TipoDeLinea.INGRESO, listOf(sueldo), sueldo.montoPlanificado)),
            ingresos = sueldo.montoPlanificado,
            cargando = false,
        )
    }

    /** El relleno horizontal de un TextField de Material, en pixeles. */
    private fun relleno() = with(compose.density) { 16.dp.roundToPx() }
}
