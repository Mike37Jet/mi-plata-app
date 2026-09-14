package com.miplata.core.domain.usecase

import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.FakePlanRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private val MARZO = Mes.de(2026, 3)

private fun linea(
    id: String,
    nombre: String,
    monto: Long = 100,
) = LineaDePlan(
    id = LineaId(id),
    nombre = nombre,
    tipo = TipoDeLinea.GASTO_FIJO,
    montoPlanificado = Money.deUnidades(monto),
)

private fun plan(
    mes: Mes,
    vararg lineas: LineaDePlan,
) = PlanMensual(id = PlanId("p-$mes"), mes = mes, lineas = lineas.toList())

@DisplayName("AbrirPlanDelMesUseCase")
class AbrirPlanDelMesUseCaseTest {
    private val repositorio = FakePlanRepository()
    private val abrir =
        AbrirPlanDelMesUseCase(
            repositorio,
            MaterializarPlanDelMesUseCase(GeneradorDeIdsSecuencial()),
        )

    @Test
    fun `si el mes ya tiene plan, devuelve ese`() =
        runTest {
            val guardado = plan(MARZO, linea("a", "Arriendo"))
            repositorio.guardar(guardado)

            val abierto = abrir(MARZO)

            abierto.plan shouldBe guardado
            abierto.esBorrador shouldBe false
        }

    @Test
    fun `si no hay plan, lo materializa del anterior y lo marca como borrador`() =
        runTest {
            repositorio.guardar(plan(Mes.de(2026, 2), linea("a", "Arriendo")))

            val abierto = abrir(MARZO)

            abierto.plan.mes shouldBe MARZO
            abierto.plan.lineas
                .single()
                .nombre shouldBe "Arriendo"
            abierto.esBorrador shouldBe true
        }

    // Si el usuario no abre la app en tres meses, se copia del ultimo plan que
    // exista, no del mes inmediatamente anterior, que no tiene ninguno.
    @Test
    fun `materializa desde el ultimo plan aunque haya huecos`() =
        runTest {
            repositorio.guardar(plan(Mes.de(2025, 11), linea("a", "Arriendo")))

            abrir(Mes.de(2026, 6))
                .plan.lineas
                .single()
                .nombre shouldBe "Arriendo"
        }

    @Test
    fun `el primer mes de todos sale vacio`() =
        runTest {
            val abierto = abrir(MARZO)

            abierto.plan.lineas shouldBe emptyList()
            abierto.esBorrador shouldBe true
        }

    // Guardarlo al abrir crearia un plan por cada mes que alguien mire de
    // pasada, con los gastos de hoy copiados a un futuro donde ya no significan
    // nada.
    @Test
    fun `abrir no guarda nada`() =
        runTest {
            repositorio.guardar(plan(Mes.de(2026, 2), linea("a", "Arriendo")))

            abrir(MARZO)

            repositorio.obtenerDe(MARZO) shouldBe null
            repositorio.observarDe(MARZO).first() shouldBe null
        }

    @Test
    fun `el borrador lleva identificadores nuevos, no los del mes anterior`() =
        runTest {
            repositorio.guardar(plan(Mes.de(2026, 2), linea("original", "Arriendo")))

            abrir(MARZO)
                .plan.lineas
                .single()
                .id shouldNotBe LineaId("original")
        }
}
