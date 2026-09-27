package com.miplata.feature.plan

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.text.TextLayoutResult
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El plan con la letra del sistema al 200%, el maximo de Android 14.
 *
 * Cuando el importe iba en un campo de ancho fijo dentro de la fila, al 200% de
 * "2000.00" solo asomaba el primer digito. Ahora la fila es una fila de lista,
 * que con letra grande baja el importe a su propia linea: tiene que verse
 * entero, sin cortarse.
 *
 * Graficos nativos de Robolectric: lo que se comprueba es cuanto mide el texto
 * de verdad. Con los de mentira, cada letra mide lo mismo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp", fontScale = 2.0f)
class LetraGrandeEnElPlanTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Si [texto] se ve entero: en una sola linea y sin pasarse del sitio que
     * tiene su nodo. `hasVisualOverflow` no sirve aqui: con la letra escalada,
     * los graficos nativos de Robolectric lo marcan incluso en "Sueldo", que mide
     * 95px con 276 disponibles.
     */
    private fun seVeEntero(texto: String) {
        val nodo = compose.onAllNodesWithText(texto).onLast().fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        val medida = medidas.single()

        medida.lineCount shouldBe 1
        medida.size.width shouldBeLessThanOrEqual nodo.size.width
    }

    @Test
    fun `el importe de una linea se ve entero`() {
        compose.setContent {
            MiPlataTheme(colorDinamico = false) {
                PantallaPlan(estado = planConUnSueldoDe("123456"), alEvento = {})
            }
        }

        seVeEntero("$123,456.00")
    }

    @Test
    fun `el nombre de la linea tambien se ve`() {
        compose.setContent {
            MiPlataTheme(colorDinamico = false) {
                PantallaPlan(estado = planConUnSueldoDe("2000"), alEvento = {})
            }
        }

        seVeEntero("Sueldo")
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
}
