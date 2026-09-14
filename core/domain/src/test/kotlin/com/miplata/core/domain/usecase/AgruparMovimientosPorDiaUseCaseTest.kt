package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

private fun movimiento(
    id: String,
    dia: Int,
    monto: Long,
    tipo: TipoDeTransaccion = TipoDeTransaccion.GASTO,
    destino: String? = null,
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, 3, dia),
    monto = Money.deUnidades(monto),
    tipo = tipo,
    cuentaOrigenId = CuentaId("c1"),
    cuentaDestinoId = destino?.let(::CuentaId),
)

class AgruparMovimientosPorDiaUseCaseTest {
    private val agrupar = AgruparMovimientosPorDiaUseCase()

    @Test
    fun `sin movimientos no hay dias`() {
        agrupar(emptyList()).shouldBeEmpty()
    }

    @Test
    fun `junta los del mismo dia y separa los de dias distintos`() {
        val dias =
            agrupar(
                listOf(
                    movimiento("a", dia = 10, monto = 20),
                    movimiento("b", dia = 10, monto = 30),
                    movimiento("c", dia = 11, monto = 40),
                ),
            )

        dias.size shouldBe 2
        dias.first { it.fecha == LocalDate(2026, 3, 10) }.movimientos.size shouldBe 2
    }

    // Lo ultimo anotado es lo que se quiere ver, y ademas asi el movimiento
    // recien creado aparece donde el usuario esta mirando.
    @Test
    fun `los dias salen del mas reciente al mas antiguo`() {
        val dias =
            agrupar(
                listOf(
                    movimiento("a", dia = 5, monto = 10),
                    movimiento("b", dia = 20, monto = 10),
                    movimiento("c", dia = 12, monto = 10),
                ),
            )

        dias.map { it.fecha.dayOfMonth } shouldBe listOf(20, 12, 5)
    }

    @Test
    fun `el neto del dia resta los gastos y suma los ingresos`() {
        val dias =
            agrupar(
                listOf(
                    movimiento("a", dia = 10, monto = 100, tipo = TipoDeTransaccion.INGRESO),
                    movimiento("b", dia = 10, monto = 30),
                ),
            )

        dias.single().neto shouldBe Money.deUnidades(70)
    }

    @Test
    fun `un dia de solo gastos queda en negativo`() {
        agrupar(listOf(movimiento("a", dia = 10, monto = 45)))
            .single()
            .neto shouldBe Money.deUnidades(-45)
    }

    // Mover dinero de una cuenta a otra no hace que el dia vaya mejor ni peor.
    @Test
    fun `una transferencia aparece en la lista pero no cambia el neto del dia`() {
        val dias =
            agrupar(
                listOf(
                    movimiento("a", dia = 10, monto = 30),
                    movimiento(
                        "b",
                        dia = 10,
                        monto = 500,
                        tipo = TipoDeTransaccion.TRANSFERENCIA,
                        destino = "c2",
                    ),
                ),
            )

        dias.single().movimientos.size shouldBe 2
        dias.single().neto shouldBe Money.deUnidades(-30)
    }

    @Test
    fun `un dia de solo transferencias tiene neto cero`() {
        agrupar(
            listOf(
                movimiento("a", dia = 10, monto = 500, tipo = TipoDeTransaccion.TRANSFERENCIA, destino = "c2"),
            ),
        ).single()
            .neto shouldBe Money.ZERO
    }

    // Sin desempate, dos movimientos de la misma fecha podrian cambiar de orden
    // entre recomposiciones y la lista bailaria bajo el dedo.
    @Test
    fun `dentro de un dia el orden es estable`() {
        val entrada =
            listOf(
                movimiento("a", dia = 10, monto = 10),
                movimiento("c", dia = 10, monto = 10),
                movimiento("b", dia = 10, monto = 10),
            )

        agrupar(entrada).single().movimientos.map { it.id.valor } shouldBe listOf("c", "b", "a")
        agrupar(entrada.reversed()).single().movimientos.map { it.id.valor } shouldBe
            listOf("c", "b", "a")
    }
}
