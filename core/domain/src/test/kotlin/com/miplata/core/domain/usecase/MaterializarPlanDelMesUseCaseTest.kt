package com.miplata.core.domain.usecase

import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TransaccionId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Generador predecible: `plan-1`, `linea-1`, `linea-2`...
 *
 * Un fake escrito a mano en vez de un mock. Son cinco lineas, se lee de un
 * vistazo y no depende de la API de ninguna libreria de dobles (docs/02).
 */
private class GeneradorSecuencial : GeneradorDeIds {
    private var planes = 0
    private var lineas = 0

    override fun nuevoPlanId() = PlanId("plan-${++planes}")

    override fun nuevaLineaId() = LineaId("linea-${++lineas}")

    override fun nuevaTransaccionId() = TransaccionId("tx-1")

    override fun nuevaCuentaId() = CuentaId("cuenta-1")

    override fun nuevaCategoriaId() = CategoriaId("categoria-1")
}

private fun linea(
    id: String,
    nombre: String = "Linea $id",
    tipo: TipoDeLinea = TipoDeLinea.GASTO_FIJO,
    monto: Long = 100,
    activa: Boolean = true,
) = LineaDePlan(
    id = LineaId(id),
    nombre = nombre,
    tipo = tipo,
    montoPlanificado = Money.deUnidades(monto),
    activa = activa,
)

@DisplayName("MaterializarPlanDelMesUseCase")
class MaterializarPlanDelMesUseCaseTest {
    private val materializar = MaterializarPlanDelMesUseCase(GeneradorSecuencial())

    private val febrero = Mes.de(2026, 2)
    private val marzo = Mes.de(2026, 3)

    private fun planDe(
        mes: Mes,
        vararg lineas: LineaDePlan,
    ) = PlanMensual(id = PlanId("anterior"), mes = mes, lineas = lineas.toList())

    @Nested
    @DisplayName("sin plan del que copiar")
    inner class SinAnterior {
        @Test
        fun `el primer mes arranca vacio`() {
            val plan = materializar(marzo, planAnterior = null)

            plan.mes shouldBe marzo
            plan.lineas shouldBe emptyList()
        }

        @Test
        fun `un plan anterior sin lineas activas tambien arranca vacio`() {
            val anterior = planDe(febrero, linea("a", activa = false), linea("b", activa = false))

            materializar(marzo, anterior).lineas shouldBe emptyList()
        }
    }

    @Nested
    @DisplayName("copiando el mes anterior")
    inner class Copiando {
        @Test
        fun `el plan nuevo es del mes pedido, no del anterior`() {
            val plan = materializar(marzo, planDe(febrero, linea("a")))

            plan.mes shouldBe marzo
        }

        @Test
        fun `copia solo las lineas activas`() {
            val anterior =
                planDe(
                    febrero,
                    linea("a", nombre = "Arriendo"),
                    linea("b", nombre = "Gimnasio", activa = false),
                    linea("c", nombre = "Internet"),
                )

            materializar(marzo, anterior).lineas.map { it.nombre } shouldContainExactly
                listOf("Arriendo", "Internet")
        }

        @Test
        fun `conserva el resto de los datos de cada linea`() {
            val original =
                linea(id = "a", nombre = "Arriendo", tipo = TipoDeLinea.GASTO_FIJO, monto = 450)
                    .copy(categoriaId = CategoriaId("vivienda"), diaDelMes = 5)

            val copia = materializar(marzo, planDe(febrero, original)).lineas.single()

            copia.nombre shouldBe "Arriendo"
            copia.tipo shouldBe TipoDeLinea.GASTO_FIJO
            copia.montoPlanificado shouldBe Money.deUnidades(450)
            copia.categoriaId shouldBe CategoriaId("vivienda")
            copia.diaDelMes shouldBe 5
            copia.activa shouldBe true
        }

        @Test
        fun `respeta el orden de las lineas`() {
            val anterior =
                planDe(
                    febrero,
                    linea("a", nombre = "Primera"),
                    linea("b", nombre = "Segunda"),
                    linea("c", nombre = "Tercera"),
                )

            materializar(marzo, anterior).lineas.map { it.nombre } shouldContainExactly
                listOf("Primera", "Segunda", "Tercera")
        }
    }

    @Nested
    @DisplayName("el snapshot es independiente")
    inner class Independencia {
        // La razon de ser del ADR 0003: editar marzo no debe tocar febrero.
        // Si las copias compartieran identificador con el original, subir el
        // arriendo en marzo reescribiria febrero y comparar meses no
        // significaria nada.
        @Test
        fun `las lineas copiadas tienen identificadores nuevos`() {
            val anterior = planDe(febrero, linea("a"), linea("b"))

            val copiadas = materializar(marzo, anterior).lineas

            copiadas.map { it.id } shouldContainExactly
                listOf(LineaId("linea-1"), LineaId("linea-2"))
            copiadas.forEach { it.id shouldNotBe LineaId("a") }
        }

        @Test
        fun `el plan nuevo tiene identificador propio`() {
            val plan = materializar(marzo, planDe(febrero, linea("a")))

            plan.id shouldBe PlanId("plan-1")
            plan.id shouldNotBe PlanId("anterior")
        }

        @Test
        fun `el plan anterior no se modifica`() {
            val original = linea("a", nombre = "Arriendo", monto = 400)
            val anterior = planDe(febrero, original)

            materializar(marzo, anterior)

            anterior.lineas.single() shouldBe original
            anterior.mes shouldBe febrero
        }

        // Materializar dos veces produce planes distintos: no hay estado
        // compartido escondido entre llamadas.
        @Test
        fun `dos materializaciones no comparten identificadores`() {
            val anterior = planDe(febrero, linea("a"))

            val primera = materializar(marzo, anterior)
            val segunda = materializar(marzo.siguiente(), anterior)

            primera.id shouldNotBe segunda.id
            primera.lineas.single().id shouldNotBe segunda.lineas.single().id
        }
    }

    @Nested
    @DisplayName("huecos entre meses")
    inner class Huecos {
        // Si el usuario no abre la app en tres meses, se copia del ultimo plan
        // que exista, no del mes inmediatamente anterior.
        @Test
        fun `copia de un plan que no es del mes inmediatamente anterior`() {
            val enero = planDe(Mes.de(2026, 1), linea("a", nombre = "Arriendo"))

            val junio = materializar(Mes.de(2026, 6), enero)

            junio.mes shouldBe Mes.de(2026, 6)
            junio.lineas.single().nombre shouldBe "Arriendo"
        }

        @Test
        fun `tambien sirve para rellenar un mes pasado`() {
            val plan = materializar(Mes.de(2025, 12), planDe(marzo, linea("a")))

            plan.mes shouldBe Mes.de(2025, 12)
            plan.lineas.size shouldBe 1
        }
    }
}
