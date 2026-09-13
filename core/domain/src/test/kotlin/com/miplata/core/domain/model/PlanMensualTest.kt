package com.miplata.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun linea(
    id: String,
    tipo: TipoDeLinea,
    monto: Long,
    activa: Boolean = true,
    diaDelMes: Int? = null,
) = LineaDePlan(
    id = LineaId(id),
    nombre = "Linea $id",
    tipo = tipo,
    montoPlanificado = Money.deUnidades(monto),
    activa = activa,
    diaDelMes = diaDelMes,
)

private fun plan(vararg lineas: LineaDePlan) =
    PlanMensual(id = PlanId("p1"), mes = Mes.de(2026, 3), lineas = lineas.toList())

@DisplayName("LineaDePlan")
class LineaDePlanTest {
    @Test
    fun `exige un nombre`() {
        assertThrows<IllegalArgumentException> {
            LineaDePlan(
                id = LineaId("l1"),
                nombre = "   ",
                tipo = TipoDeLinea.GASTO_FIJO,
                montoPlanificado = Money.deUnidades(100),
            )
        }
    }

    // El monto es una magnitud; quien decide si suma o resta es el tipo.
    @Test
    fun `rechaza montos negativos`() {
        assertThrows<IllegalArgumentException> {
            linea("l1", TipoDeLinea.GASTO_FIJO, -100)
        }
    }

    // Un gasto previsto de cero es legitimo: la linea existe y todavia no se ha
    // decidido cuanto.
    @Test
    fun `acepta un monto de cero`() {
        linea("l1", TipoDeLinea.GASTO_VARIABLE, 0).montoPlanificado shouldBe Money.ZERO
    }

    @Test
    fun `rechaza dias fuera del mes`() {
        assertThrows<IllegalArgumentException> {
            linea("l1", TipoDeLinea.GASTO_FIJO, 100, diaDelMes = 0)
        }
        assertThrows<IllegalArgumentException> {
            linea("l1", TipoDeLinea.GASTO_FIJO, 100, diaDelMes = 32)
        }
    }

    @Test
    fun `acepta los extremos del mes`() {
        linea("l1", TipoDeLinea.GASTO_FIJO, 100, diaDelMes = 1).diaDelMes shouldBe 1
        linea("l2", TipoDeLinea.GASTO_FIJO, 100, diaDelMes = 31).diaDelMes shouldBe 31
    }

    @Test
    fun `solo el ingreso no resta del disponible`() {
        TipoDeLinea.INGRESO.restaDelDisponible shouldBe false
        TipoDeLinea.GASTO_FIJO.restaDelDisponible shouldBe true
        TipoDeLinea.GASTO_VARIABLE.restaDelDisponible shouldBe true
        TipoDeLinea.AHORRO.restaDelDisponible shouldBe true
    }
}

@DisplayName("PlanMensual")
class PlanMensualTest {
    @Nested
    @DisplayName("integridad")
    inner class Integridad {
        @Test
        fun `rechaza lineas con el mismo id`() {
            assertThrows<IllegalArgumentException> {
                plan(
                    linea("repetida", TipoDeLinea.INGRESO, 1000),
                    linea("repetida", TipoDeLinea.GASTO_FIJO, 400),
                )
            }
        }

        @Test
        fun `un plan vacio es valido`() {
            plan().lineas shouldBe emptyList()
        }
    }

    @Nested
    @DisplayName("totales")
    inner class Totales {
        @Test
        fun `suma las lineas de un tipo`() {
            val p =
                plan(
                    linea("i1", TipoDeLinea.INGRESO, 1000),
                    linea("i2", TipoDeLinea.INGRESO, 500),
                    linea("g1", TipoDeLinea.GASTO_FIJO, 400),
                )

            p.totalPlanificadoDe(TipoDeLinea.INGRESO) shouldBe Money.deUnidades(1500)
            p.totalPlanificadoDe(TipoDeLinea.GASTO_FIJO) shouldBe Money.deUnidades(400)
        }

        @Test
        fun `un tipo sin lineas suma cero`() {
            plan().totalPlanificadoDe(TipoDeLinea.AHORRO) shouldBe Money.ZERO
        }

        // Desactivar una linea es la forma de decir "este mes esto no toca" sin
        // perderla para los meses siguientes.
        @Test
        fun `las lineas desactivadas no cuentan`() {
            val p =
                plan(
                    linea("g1", TipoDeLinea.GASTO_FIJO, 400),
                    linea("g2", TipoDeLinea.GASTO_FIJO, 100, activa = false),
                )

            p.totalPlanificadoDe(TipoDeLinea.GASTO_FIJO) shouldBe Money.deUnidades(400)
            p.lineasActivas.size shouldBe 1
            p.lineas.size shouldBe 2
        }
    }

    @Nested
    @DisplayName("disponible planificado")
    inner class Disponible {
        @Test
        fun `resta del ingreso todo lo demas`() {
            val p =
                plan(
                    linea("i1", TipoDeLinea.INGRESO, 2000),
                    linea("g1", TipoDeLinea.GASTO_FIJO, 800),
                    linea("g2", TipoDeLinea.GASTO_VARIABLE, 500),
                    linea("a1", TipoDeLinea.AHORRO, 300),
                )

            p.disponiblePlanificado() shouldBe Money.deUnidades(400)
        }

        // El ahorro resta del disponible aunque el dinero siga siendo tuyo: si
        // no restara, la app diria que puedes gastarte lo que ya decidiste
        // apartar.
        @Test
        fun `el ahorro resta como un gasto mas`() {
            val p =
                plan(
                    linea("i1", TipoDeLinea.INGRESO, 1000),
                    linea("a1", TipoDeLinea.AHORRO, 1000),
                )

            p.disponiblePlanificado() shouldBe Money.ZERO
        }

        @Test
        fun `un plan sobregirado da negativo`() {
            val p =
                plan(
                    linea("i1", TipoDeLinea.INGRESO, 1000),
                    linea("g1", TipoDeLinea.GASTO_FIJO, 1200),
                )

            p.disponiblePlanificado() shouldBe Money.deUnidades(-200)
            p.disponiblePlanificado().esNegativo shouldBe true
        }

        @Test
        fun `un plan vacio no tiene nada disponible`() {
            plan().disponiblePlanificado() shouldBe Money.ZERO
        }

        @Test
        fun `las lineas desactivadas no afectan al disponible`() {
            val p =
                plan(
                    linea("i1", TipoDeLinea.INGRESO, 1000),
                    linea("g1", TipoDeLinea.GASTO_FIJO, 900, activa = false),
                )

            p.disponiblePlanificado() shouldBe Money.deUnidades(1000)
        }

        @Test
        fun `un mes sin ingresos deja todo el gasto en negativo`() {
            val p = plan(linea("g1", TipoDeLinea.GASTO_FIJO, 300))

            p.disponiblePlanificado() shouldBe Money.deUnidades(-300)
        }
    }

    @Nested
    @DisplayName("consulta de lineas")
    inner class Consulta {
        @Test
        fun `filtra por tipo y solo devuelve activas`() {
            val p =
                plan(
                    linea("g1", TipoDeLinea.GASTO_VARIABLE, 200),
                    linea("g2", TipoDeLinea.GASTO_VARIABLE, 100, activa = false),
                    linea("i1", TipoDeLinea.INGRESO, 1000),
                )

            p.lineasDe(TipoDeLinea.GASTO_VARIABLE).map { it.id } shouldBe listOf(LineaId("g1"))
        }
    }
}
