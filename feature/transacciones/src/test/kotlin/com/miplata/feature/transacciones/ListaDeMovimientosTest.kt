package com.miplata.feature.transacciones

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val HOY = LocalDate(2026, 3, 26)

private fun transferencia(nota: String? = null) =
    MovimientoEnLista(
        transaccion =
            Transaccion(
                id = TransaccionId("t"),
                fecha = HOY,
                monto = Money.deUnidades(100),
                tipo = TipoDeTransaccion.TRANSFERENCIA,
                cuentaOrigenId = CuentaId("nomina"),
                cuentaDestinoId = CuentaId("visa"),
                nota = nota,
            ),
        cuenta = "Cuenta nómina",
        cuentaDestino = "Visa",
        categoria = null,
        lineaDePlan = null,
    )

/** Como se ve cada movimiento en la lista del mes. */
@RunWith(RobolectricTestRunner::class)
class ListaDeMovimientosTest {
    @get:Rule
    val compose = createComposeRule()

    private fun lista(
        vararg movimientos: MovimientoEnLista,
        neto: Money = Money.ZERO,
    ) {
        compose.setContent {
            MiPlataTheme {
                PantallaTransacciones(
                    estado =
                        TransaccionesUiState(
                            mes = Mes.de(2026, 3),
                            cargando = false,
                            hoy = HOY,
                            dias = listOf(DiaEnLista(HOY, movimientos.toList(), neto = neto)),
                        ),
                    alEvento = {},
                )
            }
        }
    }

    /**
     * Una transferencia no tiene categoria, y salia como "Sin categoria -€100":
     * un gasto que no era. Lo que la identifica es de donde a donde fue el dinero.
     */
    @Test
    fun `una transferencia dice de donde a donde, sin signo`() {
        lista(transferencia())

        compose.onNodeWithText("Cuenta nómina → Visa").assertIsDisplayed()
        compose.onNodeWithText("Transferencia").assertIsDisplayed()
        compose.onNodeWithText("$100.00").assertIsDisplayed()
        compose.onNodeWithText("Sin categoría").assertDoesNotExist()
        compose.onNodeWithText("-$100.00").assertDoesNotExist()
    }

    /** Si tiene nota, la nota manda, y las cuentas bajan al detalle. */
    @Test
    fun `una transferencia con nota enseña la nota y las cuentas debajo`() {
        lista(transferencia(nota = "Pago de la tarjeta"))

        compose.onNodeWithText("Pago de la tarjeta").assertIsDisplayed()
        compose.onNodeWithText("Cuenta nómina → Visa").assertIsDisplayed()
    }

    /** Un dia de solo transferencias no gano ni perdio: sin "€0.00" en verde. */
    @Test
    fun `un dia de solo transferencias no enseña total`() {
        // Un neto con valor a proposito: lo que decide si se enseña es que el dia
        // solo tenga transferencias, no que el neto sea cero. Asi tampoco se
        // confunde con los "$0.00" de Entro y Salio, arriba.
        lista(transferencia(), neto = Money.deUnidades(7))

        compose.onNodeWithText("Hoy").assertIsDisplayed()
        compose.onNodeWithText("+$7.00").assertDoesNotExist()
    }
}
