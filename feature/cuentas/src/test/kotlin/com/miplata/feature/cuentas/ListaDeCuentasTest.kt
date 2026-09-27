package com.miplata.feature.cuentas

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.usecase.CuentaConSaldo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun conSaldo(
    nombre: String,
    saldo: Long,
    archivada: Boolean = false,
) = CuentaConSaldo(
    cuenta =
        Cuenta(
            id = CuentaId(nombre),
            nombre = nombre,
            tipo = TipoDeCuenta.BANCARIA,
            saldoInicial = Money.deUnidades(saldo),
            moneda = Moneda("USD"),
            archivada = archivada,
        ),
    saldo = Money.deUnidades(saldo),
)

/** Las activas a la vista, las archivadas plegadas hasta que se piden. */
@RunWith(RobolectricTestRunner::class)
class ListaDeCuentasTest {
    @get:Rule
    val compose = createComposeRule()

    private fun lista(vararg cuentas: CuentaConSaldo) {
        compose.setContent {
            MiPlataTheme {
                PantallaCuentas(
                    estado =
                        CuentasUiState(
                            cuentas = cuentas.toList(),
                            total = Money.deUnidades(100),
                            cargando = false,
                        ),
                    alEvento = {},
                )
            }
        }
    }

    @Test
    fun `las archivadas empiezan plegadas y se despliegan al tocar`() {
        lista(conSaldo("Banco", 100), conSaldo("Visa", -330, archivada = true))

        compose.onNodeWithText("Banco").assertIsDisplayed()
        compose.onNodeWithText("Archivadas").assertIsDisplayed()
        compose.onNodeWithText("Visa").assertDoesNotExist()

        compose.onNodeWithText("Archivadas").performClick()

        compose.onNodeWithText("Visa").assertIsDisplayed()
    }

    /** Un lector de pantalla tiene que saber si la fila esta abierta o cerrada. */
    @Test
    fun `la fila de archivadas dice si esta desplegada`() {
        lista(conSaldo("Banco", 100), conSaldo("Visa", -330, archivada = true))
        val estado = { valor: String -> SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, valor) }

        compose.onNode(estado("Plegadas"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Archivadas").performClick()
        compose.onNode(estado("Desplegadas"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `sin archivadas no hay fila de archivadas`() {
        lista(conSaldo("Banco", 100))

        compose.onNodeWithText("Archivadas").assertDoesNotExist()
    }
}
