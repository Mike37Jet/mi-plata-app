package com.miplata.core.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private val USD = Moneda("USD")
private val UNA_FECHA = LocalDate(2026, 3, 15)

@DisplayName("Identificadores")
class IdentificadoresTest {
    @Test
    fun `rechazan valores en blanco`() {
        assertThrows<IllegalArgumentException> { CuentaId("") }
        assertThrows<IllegalArgumentException> { CategoriaId("   ") }
        assertThrows<IllegalArgumentException> { TransaccionId("") }
        assertThrows<IllegalArgumentException> { PlanId("") }
        assertThrows<IllegalArgumentException> { LineaId("") }
    }

    @Test
    fun `se muestran por su valor`() {
        CuentaId("cuenta-1").toString() shouldBe "cuenta-1"
        CategoriaId("cat-1").toString() shouldBe "cat-1"
        TransaccionId("tx-1").toString() shouldBe "tx-1"
        PlanId("plan-1").toString() shouldBe "plan-1"
        LineaId("linea-1").toString() shouldBe "linea-1"
    }
}

@DisplayName("Moneda")
class MonedaTest {
    @Test
    fun `acepta un codigo ISO valido`() {
        Moneda("USD").codigo shouldBe "USD"
        Moneda("COP").toString() shouldBe "COP"
    }

    @Test
    fun `rechaza codigos que no son ISO 4217`() {
        assertThrows<IllegalArgumentException> { Moneda("US") }
        assertThrows<IllegalArgumentException> { Moneda("USDD") }
        assertThrows<IllegalArgumentException> { Moneda("usd") }
        assertThrows<IllegalArgumentException> { Moneda("US1") }
        assertThrows<IllegalArgumentException> { Moneda("") }
    }
}

@DisplayName("Cuenta")
class CuentaTest {
    private fun cuenta(
        nombre: String = "Banco",
        saldoInicial: Money = Money.ZERO,
    ) = Cuenta(
        id = CuentaId("c1"),
        nombre = nombre,
        tipo = TipoDeCuenta.BANCARIA,
        saldoInicial = saldoInicial,
        moneda = USD,
    )

    @Test
    fun `exige un nombre`() {
        assertThrows<IllegalArgumentException> { cuenta(nombre = "  ") }
    }

    @Test
    fun `por defecto cuenta para el total y no esta archivada`() {
        cuenta().incluirEnTotal shouldBe true
        cuenta().archivada shouldBe false
    }

    // Una tarjeta de credito arranca en negativo con toda normalidad.
    @Test
    fun `admite saldo inicial negativo`() {
        cuenta(saldoInicial = Money.deUnidades(-250)).saldoInicial shouldBe Money.deUnidades(-250)
    }
}

@DisplayName("Categoria")
class CategoriaTest {
    @Test
    fun `una categoria sin madre es raiz`() {
        Categoria(id = CategoriaId("c1"), nombre = "Comida").esRaiz shouldBe true
    }

    @Test
    fun `una subcategoria no es raiz`() {
        val sub =
            Categoria(
                id = CategoriaId("c2"),
                nombre = "Restaurantes",
                padreId = CategoriaId("c1"),
            )

        sub.esRaiz shouldBe false
    }

    @Test
    fun `exige un nombre`() {
        assertThrows<IllegalArgumentException> { Categoria(id = CategoriaId("c1"), nombre = "") }
    }

    @Test
    fun `no puede ser su propia madre`() {
        assertThrows<IllegalArgumentException> {
            Categoria(id = CategoriaId("c1"), nombre = "Comida", padreId = CategoriaId("c1"))
        }
    }
}

@DisplayName("Transaccion")
class TransaccionTest {
    private fun transaccion(
        monto: Money = Money.deUnidades(100),
        tipo: TipoDeTransaccion = TipoDeTransaccion.GASTO,
        destino: CuentaId? = null,
    ) = Transaccion(
        id = TransaccionId("t1"),
        fecha = UNA_FECHA,
        monto = monto,
        tipo = tipo,
        cuentaOrigenId = CuentaId("origen"),
        cuentaDestinoId = destino,
    )

    @Nested
    @DisplayName("el monto siempre es positivo")
    inner class MontoPositivo {
        @Test
        fun `rechaza montos negativos`() {
            assertThrows<IllegalArgumentException> { transaccion(monto = Money.deUnidades(-5)) }
        }

        // Un movimiento de cero no significa nada y ensucia los informes.
        @Test
        fun `rechaza el cero`() {
            assertThrows<IllegalArgumentException> { transaccion(monto = Money.ZERO) }
        }
    }

    @Nested
    @DisplayName("cuentas implicadas")
    inner class Cuentas {
        @Test
        fun `una transferencia exige cuenta de destino`() {
            assertThrows<IllegalArgumentException> {
                transaccion(tipo = TipoDeTransaccion.TRANSFERENCIA, destino = null)
            }
        }

        @Test
        fun `una transferencia exige dos cuentas distintas`() {
            assertThrows<IllegalArgumentException> {
                transaccion(tipo = TipoDeTransaccion.TRANSFERENCIA, destino = CuentaId("origen"))
            }
        }

        @Test
        fun `una transferencia valida se construye`() {
            val t =
                transaccion(tipo = TipoDeTransaccion.TRANSFERENCIA, destino = CuentaId("destino"))

            t.esTransferencia shouldBe true
        }

        @Test
        fun `un gasto no puede llevar cuenta de destino`() {
            assertThrows<IllegalArgumentException> {
                transaccion(tipo = TipoDeTransaccion.GASTO, destino = CuentaId("destino"))
            }
        }

        @Test
        fun `un ingreso tampoco`() {
            assertThrows<IllegalArgumentException> {
                transaccion(tipo = TipoDeTransaccion.INGRESO, destino = CuentaId("destino"))
            }
        }

        @Test
        fun `un gasto normal no es transferencia`() {
            transaccion().esTransferencia shouldBe false
        }
    }
}
