package com.miplata.core.domain.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

@DisplayName("Money")
class MoneyTest {
    @Nested
    @DisplayName("construccion")
    inner class Construccion {
        @Test
        fun `deCentavos guarda el valor tal cual`() {
            Money.deCentavos(1234).centavos shouldBe 1234L
        }

        @Test
        fun `deUnidades multiplica por cien`() {
            Money.deUnidades(5).centavos shouldBe 500L
        }

        @Test
        fun `ZERO es cero`() {
            Money.ZERO.centavos shouldBe 0L
            Money.ZERO.esCero shouldBe true
        }

        @Test
        fun `acepta cantidades negativas`() {
            Money.deCentavos(-1).esNegativo shouldBe true
        }

        @Test
        fun `clasifica el signo`() {
            Money.deCentavos(1).let {
                it.esPositivo shouldBe true
                it.esNegativo shouldBe false
                it.esCero shouldBe false
            }
            Money.deCentavos(-1).let {
                it.esPositivo shouldBe false
                it.esNegativo shouldBe true
                it.esCero shouldBe false
            }
            Money.ZERO.let {
                it.esPositivo shouldBe false
                it.esNegativo shouldBe false
                it.esCero shouldBe true
            }
        }
    }

    @Nested
    @DisplayName("conversion desde decimal")
    inner class DesdeDecimal {
        @Test
        fun `convierte dos decimales exactos`() {
            Money.deDecimal(BigDecimal("12.34")).centavos shouldBe 1234L
        }

        @Test
        fun `convierte un entero`() {
            Money.deDecimal(BigDecimal("7")).centavos shouldBe 700L
        }

        @Test
        fun `convierte negativos`() {
            Money.deDecimal(BigDecimal("-0.05")).centavos shouldBe -5L
        }

        // HALF_EVEN reparte los empates: .5 redondea hacia el par mas cercano.
        // Sin esto, redondear muchas cantidades sesgaria el total hacia arriba.
        @Test
        fun `en un empate redondea hacia el par - hacia abajo`() {
            Money.deDecimal(BigDecimal("0.125")).centavos shouldBe 12L
        }

        @Test
        fun `en un empate redondea hacia el par - hacia arriba`() {
            Money.deDecimal(BigDecimal("0.135")).centavos shouldBe 14L
        }

        @Test
        fun `fuera de un empate redondea normalmente`() {
            Money.deDecimal(BigDecimal("0.126")).centavos shouldBe 13L
            Money.deDecimal(BigDecimal("0.124")).centavos shouldBe 12L
        }

        @Test
        fun `no pierde precision con muchos decimales`() {
            Money.deDecimal(BigDecimal("1999.999")).centavos shouldBe 200000L
        }
    }

    @Nested
    @DisplayName("aritmetica")
    inner class Aritmetica {
        @Test
        fun `suma`() {
            (Money.deCentavos(1000) + Money.deCentavos(250)) shouldBe Money.deCentavos(1250)
        }

        @Test
        fun `resta`() {
            (Money.deCentavos(1000) - Money.deCentavos(250)) shouldBe Money.deCentavos(750)
        }

        @Test
        fun `restar de mas da negativo, no cero`() {
            (Money.deCentavos(100) - Money.deCentavos(250)) shouldBe Money.deCentavos(-150)
        }

        @Test
        fun `multiplica por un entero`() {
            (Money.deCentavos(333) * 3) shouldBe Money.deCentavos(999)
        }

        @Test
        fun `niega`() {
            (-Money.deCentavos(500)) shouldBe Money.deCentavos(-500)
        }

        @Test
        fun `valor absoluto`() {
            Money.deCentavos(-500).valorAbsoluto() shouldBe Money.deCentavos(500)
            Money.deCentavos(500).valorAbsoluto() shouldBe Money.deCentavos(500)
            Money.ZERO.valorAbsoluto() shouldBe Money.ZERO
        }

        // El motivo de existir de este tipo. Con Double, 0.1 + 0.2 == 0.30000000000000004
        // y un saldo deja de cuadrar.
        @Test
        fun `no arrastra error de punto flotante`() {
            val diezCentavos = Money.deDecimal(BigDecimal("0.1"))
            val veinteCentavos = Money.deDecimal(BigDecimal("0.2"))

            (diezCentavos + veinteCentavos) shouldBe Money.deDecimal(BigDecimal("0.3"))
        }

        @Test
        fun `sumar muchas veces no acumula desviacion`() {
            val unCentimo = Money.deDecimal(BigDecimal("0.01"))

            val total = List(size = 100) { unCentimo }.sumar()

            total shouldBe Money.deUnidades(1)
        }
    }

    @Nested
    @DisplayName("desbordamiento")
    inner class Desbordamiento {
        // Un saldo que da la vuelta en silencio es peor que un crash: el crash
        // se ve, el saldo corrupto se propaga a los informes del usuario.
        @Test
        fun `sumar mas alla del maximo falla en vez de dar la vuelta`() {
            val maximo = Money.deCentavos(Long.MAX_VALUE)

            assertThrows<ArithmeticException> { maximo + Money.deCentavos(1) }
        }

        @Test
        fun `restar mas alla del minimo falla`() {
            val minimo = Money.deCentavos(Long.MIN_VALUE)

            assertThrows<ArithmeticException> { minimo - Money.deCentavos(1) }
        }

        @Test
        fun `multiplicar fuera de rango falla`() {
            assertThrows<ArithmeticException> { Money.deCentavos(Long.MAX_VALUE) * 2 }
        }

        @Test
        fun `un decimal demasiado grande falla en vez de truncar`() {
            assertThrows<ArithmeticException> {
                Money.deDecimal(BigDecimal("99999999999999999999.99"))
            }
        }
    }

    @Nested
    @DisplayName("comparacion")
    inner class Comparacion {
        @Test
        fun `ordena por valor`() {
            (Money.deCentavos(100) > Money.deCentavos(99)) shouldBe true
            (Money.deCentavos(-100) < Money.ZERO) shouldBe true
        }

        @Test
        fun `dos cantidades iguales son iguales`() {
            Money.deCentavos(500) shouldBe Money.deCentavos(500)
        }

        @Test
        fun `ordena una lista`() {
            val desordenadas =
                listOf(Money.deCentavos(300), Money.deCentavos(-100), Money.ZERO)

            desordenadas.sorted() shouldBe
                listOf(Money.deCentavos(-100), Money.ZERO, Money.deCentavos(300))
        }
    }

    @Nested
    @DisplayName("porcentaje")
    inner class Porcentaje {
        @Test
        fun `calcula la proporcion`() {
            Money.deUnidades(25).porcentajeDe(Money.deUnidades(100)) shouldBe 25.0
        }

        @Test
        fun `puede pasar del cien por cien`() {
            Money.deUnidades(150).porcentajeDe(Money.deUnidades(100)) shouldBe 150.0
        }

        // "He gastado 50 de un presupuesto de 0" no tiene respuesta numerica.
        // Devolver 0, 100 o infinito seria inventarsela.
        @Test
        fun `sobre un total de cero no hay respuesta`() {
            Money.deUnidades(50).porcentajeDe(Money.ZERO).shouldBeNull()
        }

        @Test
        fun `cero sobre cero tampoco`() {
            Money.ZERO.porcentajeDe(Money.ZERO).shouldBeNull()
        }
    }

    @Nested
    @DisplayName("suma de colecciones")
    inner class SumaDeColecciones {
        @Test
        fun `una coleccion vacia suma cero`() {
            emptyList<Money>().sumar() shouldBe Money.ZERO
        }

        @Test
        fun `suma ingresos y gastos con signo`() {
            val movimientos =
                listOf(Money.deUnidades(1000), Money.deUnidades(-350), Money.deUnidades(-120))

            movimientos.sumar() shouldBe Money.deUnidades(530)
        }
    }

    @Nested
    @DisplayName("representacion de texto")
    inner class Representacion {
        // Es para depuracion y mensajes de test, no para el usuario: el formateo
        // con locale y simbolo de moneda vive en presentacion.
        @Test
        fun `muestra siempre dos decimales`() {
            Money.deCentavos(1234).toString() shouldBe "12.34"
            Money.deCentavos(1200).toString() shouldBe "12.00"
            Money.deCentavos(5).toString() shouldBe "0.05"
        }

        @Test
        fun `muestra el signo de los negativos`() {
            Money.deCentavos(-1234).toString() shouldBe "-12.34"
            Money.deCentavos(-5).toString() shouldBe "-0.05"
        }

        @Test
        fun `el cero no lleva signo`() {
            Money.ZERO.toString() shouldBe "0.00"
        }
    }
}
