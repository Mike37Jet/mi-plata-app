package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

private val USD = Moneda("USD")
private val EUR = Moneda("EUR")
private val UN_DIA = LocalDate(2026, 3, 10)

private fun cuenta(
    id: String,
    saldoInicial: Long = 0,
    moneda: Moneda = USD,
    incluirEnTotal: Boolean = true,
    archivada: Boolean = false,
) = Cuenta(
    id = CuentaId(id),
    nombre = id,
    // El tipo no cambia ningun calculo de saldo: quien necesite otro lo pone
    // con `copy`, en vez de arrastrar un parametro mas por todos los tests.
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.deUnidades(saldoInicial),
    moneda = moneda,
    incluirEnTotal = incluirEnTotal,
    archivada = archivada,
)

private fun movimiento(
    id: String,
    monto: Long,
    tipo: TipoDeTransaccion,
    origen: String,
    destino: String? = null,
) = Transaccion(
    id = TransaccionId(id),
    fecha = UN_DIA,
    monto = Money.deUnidades(monto),
    tipo = tipo,
    cuentaOrigenId = CuentaId(origen),
    cuentaDestinoId = destino?.let(::CuentaId),
)

class CalcularSaldosDeCuentasUseCaseTest {
    private val calcular = CalcularSaldosDeCuentasUseCase()

    private fun saldoDe(
        resultado: SaldosDeCuentas,
        id: String,
    ) = resultado.cuentas.single { it.cuenta.id == CuentaId(id) }.saldo

    @Test
    fun `sin movimientos el saldo es el inicial`() {
        val resultado = calcular(listOf(cuenta("banco", saldoInicial = 500)), emptyList(), USD)

        saldoDe(resultado, "banco") shouldBe Money.deUnidades(500)
    }

    @Test
    fun `sin cuentas el total es cero`() {
        val resultado = calcular(emptyList(), emptyList(), USD)

        resultado.total shouldBe Money.ZERO
        resultado.cuentas shouldBe emptyList()
    }

    @Test
    fun `un ingreso suma y un gasto resta`() {
        val resultado =
            calcular(
                listOf(cuenta("banco", saldoInicial = 100)),
                listOf(
                    movimiento("i", 50, TipoDeTransaccion.INGRESO, "banco"),
                    movimiento("g", 30, TipoDeTransaccion.GASTO, "banco"),
                ),
                USD,
            )

        saldoDe(resultado, "banco") shouldBe Money.deUnidades(120)
    }

    // La diferencia con el resumen mensual, donde las transferencias se ignoran:
    // alli no son ingreso ni gasto, pero aqui cambian donde esta el dinero.
    @Test
    fun `una transferencia sale de una cuenta y entra en la otra`() {
        val resultado =
            calcular(
                listOf(cuenta("banco", saldoInicial = 500), cuenta("cartera", saldoInicial = 20)),
                listOf(movimiento("t", 100, TipoDeTransaccion.TRANSFERENCIA, "banco", "cartera")),
                USD,
            )

        saldoDe(resultado, "banco") shouldBe Money.deUnidades(400)
        saldoDe(resultado, "cartera") shouldBe Money.deUnidades(120)
    }

    // Consecuencia de lo anterior: mover dinero de sitio no cambia cuanto hay.
    @Test
    fun `una transferencia no cambia el total`() {
        val cuentas = listOf(cuenta("banco", saldoInicial = 500), cuenta("cartera", saldoInicial = 20))

        val antes = calcular(cuentas, emptyList(), USD).total
        val despues =
            calcular(
                cuentas,
                listOf(movimiento("t", 100, TipoDeTransaccion.TRANSFERENCIA, "banco", "cartera")),
                USD,
            ).total

        despues shouldBe antes
    }

    @Test
    fun `una cuenta puede quedarse en negativo`() {
        val resultado =
            calcular(
                listOf(cuenta("visa").copy(tipo = TipoDeCuenta.TARJETA_CREDITO)),
                listOf(movimiento("g", 250, TipoDeTransaccion.GASTO, "visa")),
                USD,
            )

        saldoDe(resultado, "visa") shouldBe Money.deUnidades(-250)
        resultado.cuentas.single().enNegativo shouldBe true
    }

    @Test
    fun `los movimientos de una cuenta no tocan a las demas`() {
        val resultado =
            calcular(
                listOf(cuenta("banco", saldoInicial = 100), cuenta("cartera", saldoInicial = 40)),
                listOf(movimiento("g", 30, TipoDeTransaccion.GASTO, "banco")),
                USD,
            )

        saldoDe(resultado, "cartera") shouldBe Money.deUnidades(40)
    }

    @Test
    fun `una cuenta marcada para no sumar sale en la lista pero no en el total`() {
        val resultado =
            calcular(
                listOf(
                    cuenta("banco", saldoInicial = 100),
                    cuenta("fondo", saldoInicial = 5000, incluirEnTotal = false),
                ),
                emptyList(),
                USD,
            )

        resultado.cuentas.size shouldBe 2
        resultado.total shouldBe Money.deUnidades(100)
    }

    @Test
    fun `una cuenta archivada no entra en el total`() {
        val resultado =
            calcular(
                listOf(
                    cuenta("banco", saldoInicial = 100),
                    cuenta("vieja", saldoInicial = 70, archivada = true),
                ),
                emptyList(),
                USD,
            )

        resultado.total shouldBe Money.deUnidades(100)
    }

    // Ninguna de las dos es un aviso: son decisiones del usuario.
    @Test
    fun `archivar o no sumar no cuentan como cuentas en otra moneda`() {
        val resultado =
            calcular(
                listOf(
                    cuenta("fondo", incluirEnTotal = false),
                    cuenta("vieja", archivada = true),
                ),
                emptyList(),
                USD,
            )

        resultado.cuentasEnOtraMoneda shouldBe 0
    }

    // Sumar euros con dolares da un numero que no significa nada. Se dejan fuera
    // y se dice cuantas son, en vez de inventarse un tipo de cambio.
    @Test
    fun `las cuentas en otra moneda no se suman, y se avisa de cuantas son`() {
        val resultado =
            calcular(
                listOf(
                    cuenta("banco", saldoInicial = 100),
                    cuenta("europa", saldoInicial = 900, moneda = EUR),
                ),
                emptyList(),
                USD,
            )

        resultado.total shouldBe Money.deUnidades(100)
        resultado.cuentasEnOtraMoneda shouldBe 1
        resultado.cuentas.size shouldBe 2
    }

    @Test
    fun `las archivadas se ordenan al final`() {
        val resultado =
            calcular(
                listOf(
                    cuenta("vieja", archivada = true),
                    cuenta("banco"),
                    cuenta("antigua", archivada = true),
                    cuenta("cartera"),
                ),
                emptyList(),
                USD,
            )

        resultado.cuentas.map { it.cuenta.archivada } shouldBe listOf(false, false, true, true)
    }

    // Sin esto, el orden que trae el repositorio -alfabetico- se perderia dentro
    // de cada grupo y la lista bailaria entre recomposiciones.
    @Test
    fun `ordenar por archivada respeta el orden de llegada dentro de cada grupo`() {
        val resultado =
            calcular(
                listOf(cuenta("ahorro"), cuenta("banco"), cuenta("cartera")),
                emptyList(),
                USD,
            )

        resultado.cuentas.map { it.cuenta.id.valor } shouldBe listOf("ahorro", "banco", "cartera")
    }

    @Test
    fun `un movimiento de una cuenta que ya no existe no rompe el calculo`() {
        val resultado =
            calcular(
                listOf(cuenta("banco", saldoInicial = 100)),
                listOf(movimiento("g", 30, TipoDeTransaccion.GASTO, "borrada")),
                USD,
            )

        saldoDe(resultado, "banco") shouldBe Money.deUnidades(100)
        resultado.total shouldBe Money.deUnidades(100)
    }
}
