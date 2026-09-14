package com.miplata.core.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("PeriodoMensual")
class PeriodoMensualTest {
    @Nested
    @DisplayName("mes natural")
    inner class MesNatural {
        private val marzo = PeriodoMensual(Mes.de(2026, 3))

        @Test
        fun `va del uno al ultimo dia`() {
            marzo.inicio shouldBe LocalDate(2026, 3, 1)
            marzo.fin shouldBe LocalDate(2026, 3, 31)
        }

        @Test
        fun `contiene sus propias fechas y no las vecinas`() {
            marzo.contiene(LocalDate(2026, 3, 1)) shouldBe true
            marzo.contiene(LocalDate(2026, 3, 31)) shouldBe true
            marzo.contiene(LocalDate(2026, 2, 28)) shouldBe false
            marzo.contiene(LocalDate(2026, 4, 1)) shouldBe false
        }

        @Test
        fun `febrero de un anio bisiesto acaba el 29`() {
            PeriodoMensual(Mes.de(2024, 2)).fin shouldBe LocalDate(2024, 2, 29)
        }

        @Test
        fun `febrero de un anio normal acaba el 28`() {
            PeriodoMensual(Mes.de(2026, 2)).fin shouldBe LocalDate(2026, 2, 28)
        }
    }

    @Nested
    @DisplayName("progreso")
    inner class Progreso {
        private val marzo = PeriodoMensual(Mes.de(2026, 3))

        // El dia 1 no es "cero avanzado": es un dia de treinta y uno.
        @Test
        fun `el primer dia ya cuenta como transcurrido`() {
            marzo.progreso(LocalDate(2026, 3, 1)) shouldBe (1.0 / 31)
        }

        @Test
        fun `el ultimo dia esta completo`() {
            marzo.progreso(LocalDate(2026, 3, 31)) shouldBe 1.0
        }

        @Test
        fun `a mitad de mes va por la mitad`() {
            marzo.progreso(LocalDate(2026, 3, 16)) shouldBe (16.0 / 31)
        }

        // Un mes pasado esta completo y uno futuro no ha empezado: sin recortar,
        // la barra de progreso se saldria de la pantalla o iria hacia atras.
        @Test
        fun `fuera del periodo se recorta`() {
            marzo.progreso(LocalDate(2026, 2, 15)) shouldBe 0.0
            marzo.progreso(LocalDate(2026, 5, 1)) shouldBe 1.0
        }

        @Test
        fun `cuenta bien los dias de un mes desplazado`() {
            val desde25 = PeriodoMensual(Mes.de(2026, 3), primerDia = 25)

            desde25.duracionEnDias shouldBe 31
            desde25.progreso(LocalDate(2026, 4, 24)) shouldBe 1.0
        }

        @Test
        fun `febrero bisiesto dura 29 dias`() {
            PeriodoMensual(Mes.de(2024, 2)).duracionEnDias shouldBe 29
        }
    }

    @Nested
    @DisplayName("mes que empieza otro dia")
    inner class MesDesplazado {
        // El caso de quien cobra el 25: su mes economico va del 25 al 24.
        private val marzoDesde25 = PeriodoMensual(Mes.de(2026, 3), primerDia = 25)

        @Test
        fun `empieza el dia elegido y acaba la vispera del siguiente`() {
            marzoDesde25.inicio shouldBe LocalDate(2026, 3, 25)
            marzoDesde25.fin shouldBe LocalDate(2026, 4, 24)
        }

        @Test
        fun `incluye fechas del mes siguiente`() {
            marzoDesde25.contiene(LocalDate(2026, 4, 10)) shouldBe true
        }

        @Test
        fun `excluye el principio de su propio mes natural`() {
            marzoDesde25.contiene(LocalDate(2026, 3, 24)) shouldBe false
        }

        // Un 31 no existe en todos los meses: se recorta al ultimo dia.
        @Test
        fun `recorta el dia cuando el mes es mas corto`() {
            val enero = PeriodoMensual(Mes.de(2026, 1), primerDia = 31)

            enero.inicio shouldBe LocalDate(2026, 1, 31)
            enero.fin shouldBe LocalDate(2026, 2, 27)
        }

        @Test
        fun `dos periodos consecutivos no dejan huecos ni se solapan`() {
            val siguiente = marzoDesde25.siguiente()

            siguiente.inicio shouldBe LocalDate(2026, 4, 25)
            marzoDesde25.fin shouldBe LocalDate(2026, 4, 24)
        }

        @Test
        fun `rechaza dias imposibles`() {
            assertThrows<IllegalArgumentException> { PeriodoMensual(Mes.de(2026, 3), primerDia = 0) }
            assertThrows<IllegalArgumentException> { PeriodoMensual(Mes.de(2026, 3), primerDia = 32) }
        }

        @Test
        fun `navega hacia atras`() {
            marzoDesde25.anterior().inicio shouldBe LocalDate(2026, 2, 25)
        }
    }
}
