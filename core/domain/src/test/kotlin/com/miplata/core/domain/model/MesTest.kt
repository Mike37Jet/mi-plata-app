package com.miplata.core.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Mes")
class MesTest {
    @Nested
    @DisplayName("construccion")
    inner class Construccion {
        @Test
        fun `guarda anio y mes`() {
            val marzo = Mes.de(2026, 3)

            marzo.anio shouldBe 2026
            marzo.numeroDeMes shouldBe 3
        }

        @Test
        fun `se construye desde una fecha`() {
            Mes.de(LocalDate(2026, 3, 15)) shouldBe Mes.de(2026, 3)
        }

        @Test
        fun `rechaza meses fuera de rango`() {
            assertThrows<IllegalArgumentException> { Mes.de(2026, 0) }
            assertThrows<IllegalArgumentException> { Mes.de(2026, 13) }
        }

        @Test
        fun `acepta los extremos validos`() {
            Mes.de(2026, 1).numeroDeMes shouldBe 1
            Mes.de(2026, 12).numeroDeMes shouldBe 12
        }
    }

    @Nested
    @DisplayName("navegacion")
    inner class Navegacion {
        @Test
        fun `el siguiente de marzo es abril`() {
            Mes.de(2026, 3).siguiente() shouldBe Mes.de(2026, 4)
        }

        // El caso que rompe cualquier implementacion ingenua con dos campos.
        @Test
        fun `el siguiente de diciembre es enero del anio que viene`() {
            Mes.de(2026, 12).siguiente() shouldBe Mes.de(2027, 1)
        }

        @Test
        fun `el anterior de enero es diciembre del anio pasado`() {
            Mes.de(2026, 1).anterior() shouldBe Mes.de(2025, 12)
        }

        @Test
        fun `suma y resta de varios meses cruzando anios`() {
            (Mes.de(2026, 11) + 3) shouldBe Mes.de(2027, 2)
            (Mes.de(2026, 2) - 3) shouldBe Mes.de(2025, 11)
        }

        @Test
        fun `ir y volver deja el mismo mes`() {
            val mes = Mes.de(2026, 12)

            mes.siguiente().anterior() shouldBe mes
        }

        @Test
        fun `cuenta los meses entre dos`() {
            Mes.de(2026, 1).mesesHasta(Mes.de(2026, 12)) shouldBe 11
            Mes.de(2026, 12).mesesHasta(Mes.de(2026, 1)) shouldBe -11
            Mes.de(2026, 5).mesesHasta(Mes.de(2026, 5)) shouldBe 0
        }
    }

    @Nested
    @DisplayName("pertenencia de fechas")
    inner class Pertenencia {
        @Test
        fun `contiene las fechas de su mes`() {
            val marzo = Mes.de(2026, 3)

            marzo.contiene(LocalDate(2026, 3, 1)) shouldBe true
            marzo.contiene(LocalDate(2026, 3, 31)) shouldBe true
        }

        @Test
        fun `no contiene fechas de meses vecinos`() {
            val marzo = Mes.de(2026, 3)

            marzo.contiene(LocalDate(2026, 2, 28)) shouldBe false
            marzo.contiene(LocalDate(2026, 4, 1)) shouldBe false
        }

        // El mismo mes de otro anio no cuenta: es el error facil de cometer al
        // comparar solo el numero de mes.
        @Test
        fun `no contiene el mismo mes de otro anio`() {
            Mes.de(2026, 3).contiene(LocalDate(2025, 3, 15)) shouldBe false
        }
    }

    @Nested
    @DisplayName("orden")
    inner class Orden {
        @Test
        fun `ordena cronologicamente`() {
            (Mes.de(2026, 4) > Mes.de(2026, 3)) shouldBe true
            (Mes.de(2025, 12) < Mes.de(2026, 1)) shouldBe true
        }

        @Test
        fun `ordena una lista desordenada`() {
            val desordenados =
                listOf(Mes.de(2026, 3), Mes.de(2025, 12), Mes.de(2026, 1))

            desordenados.sorted() shouldBe
                listOf(Mes.de(2025, 12), Mes.de(2026, 1), Mes.de(2026, 3))
        }
    }

    @Nested
    @DisplayName("representacion")
    inner class Representacion {
        @Test
        fun `usa formato ISO con mes de dos cifras`() {
            Mes.de(2026, 3).toString() shouldBe "2026-03"
            Mes.de(2026, 12).toString() shouldBe "2026-12"
        }
    }
}
