package com.miplata.core.domain.repository

import app.cash.turbine.test
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private fun cuenta(
    id: String,
    nombre: String = "Cuenta $id",
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.ZERO,
    moneda = Moneda("USD"),
)

private fun transaccion(
    id: String,
    dia: Int,
    mes: Int = 3,
    cuenta: String = "c1",
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, mes, dia),
    monto = Money.deUnidades(10),
    tipo = TipoDeTransaccion.GASTO,
    cuentaOrigenId = CuentaId(cuenta),
)

/**
 * Los fakes no son codigo de produccion, pero si mienten, todos los tests
 * construidos sobre ellos mienten tambien. Estos tests fijan el comportamiento
 * que la implementacion real con Room tendra que reproducir.
 */
@DisplayName("Fakes en memoria")
class FakesEnMemoriaTest {
    @Nested
    @DisplayName("FakeCuentaRepository")
    inner class Cuentas {
        @Test
        fun `guarda y devuelve`() =
            runTest {
                val repo = FakeCuentaRepository()

                repo.guardar(cuenta("c1"))

                repo.obtener(CuentaId("c1"))?.nombre shouldBe "Cuenta c1"
            }

        @Test
        fun `devuelve nulo si no existe`() =
            runTest {
                FakeCuentaRepository().obtener(CuentaId("fantasma")).shouldBeNull()
            }

        @Test
        fun `guardar con el mismo id reemplaza`() =
            runTest {
                val repo = FakeCuentaRepository(listOf(cuenta("c1", "Viejo")))

                repo.guardar(cuenta("c1", "Nuevo"))

                repo.observarTodas().first().size shouldBe 1
                repo.obtener(CuentaId("c1"))?.nombre shouldBe "Nuevo"
            }

        // Lo que de verdad importa del fake: que emita al cambiar, como hara
        // Room. Un fake que no emitiera dejaria pasar tests de UI que luego
        // fallan con la implementacion real.
        @Test
        fun `el flujo emite al guardar y al eliminar`() =
            runTest {
                val repo = FakeCuentaRepository()

                repo.observarTodas().test {
                    awaitItem() shouldBe emptyList()

                    repo.guardar(cuenta("c1"))
                    awaitItem().map { it.id } shouldBe listOf(CuentaId("c1"))

                    repo.eliminar(CuentaId("c1"))
                    awaitItem() shouldBe emptyList()
                }
            }
    }

    @Nested
    @DisplayName("FakeCategoriaRepository")
    inner class Categorias {
        private fun categoria(
            id: String,
            nombre: String = "Categoria $id",
            padre: String? = null,
        ) = Categoria(
            id = CategoriaId(id),
            nombre = nombre,
            padreId = padre?.let { CategoriaId(it) },
        )

        @Test
        fun `guarda y devuelve`() =
            runTest {
                val repo = FakeCategoriaRepository()

                repo.guardar(categoria("comida", "Comida"))

                repo.obtener(CategoriaId("comida"))?.nombre shouldBe "Comida"
            }

        @Test
        fun `devuelve nulo si no existe`() =
            runTest {
                FakeCategoriaRepository().obtener(CategoriaId("fantasma")).shouldBeNull()
            }

        @Test
        fun `guardar con el mismo id reemplaza`() =
            runTest {
                val repo = FakeCategoriaRepository(listOf(categoria("c", "Viejo")))

                repo.guardar(categoria("c", "Nuevo"))

                repo.observarTodas().first().size shouldBe 1
                repo.obtener(CategoriaId("c"))?.nombre shouldBe "Nuevo"
            }

        @Test
        fun `conserva la jerarquia de dos niveles`() =
            runTest {
                val repo =
                    FakeCategoriaRepository(
                        listOf(
                            categoria("comida", "Comida"),
                            categoria("resto", "Restaurantes", padre = "comida"),
                        ),
                    )

                repo.observarTodas().first().count { it.esRaiz } shouldBe 1
                repo.obtener(CategoriaId("resto"))?.padreId shouldBe CategoriaId("comida")
            }

        @Test
        fun `el flujo emite al guardar y al eliminar`() =
            runTest {
                val repo = FakeCategoriaRepository()

                repo.observarTodas().test {
                    awaitItem() shouldBe emptyList()

                    repo.guardar(categoria("c"))
                    awaitItem().map { it.id } shouldBe listOf(CategoriaId("c"))

                    repo.eliminar(CategoriaId("c"))
                    awaitItem() shouldBe emptyList()
                }
            }
    }

    @Nested
    @DisplayName("FakeTransaccionRepository")
    inner class Transacciones {
        @Test
        fun `filtra por periodo`() =
            runTest {
                val repo =
                    FakeTransaccionRepository(
                        listOf(
                            transaccion("dentro", dia = 15, mes = 3),
                            transaccion("fuera", dia = 15, mes = 4),
                        ),
                    )

                val delPeriodo = repo.observarDelPeriodo(PeriodoMensual(Mes.de(2026, 3))).first()

                delPeriodo.map { it.id } shouldBe listOf(TransaccionId("dentro"))
            }

        @Test
        fun `respeta un periodo desplazado`() =
            runTest {
                val repo =
                    FakeTransaccionRepository(
                        listOf(
                            transaccion("antes", dia = 10, mes = 3),
                            transaccion("despues", dia = 10, mes = 4),
                        ),
                    )

                val periodo = PeriodoMensual(Mes.de(2026, 3), primerDia = 25)

                repo.observarDelPeriodo(periodo).first().map { it.id } shouldBe
                    listOf(TransaccionId("despues"))
            }

        @Test
        fun `devuelve los movimientos ordenados por fecha`() =
            runTest {
                val repo =
                    FakeTransaccionRepository(
                        listOf(transaccion("b", dia = 20), transaccion("a", dia = 5)),
                    )

                repo.observarDelPeriodo(PeriodoMensual(Mes.de(2026, 3))).first().map { it.id } shouldBe
                    listOf(TransaccionId("a"), TransaccionId("b"))
            }

        @Test
        fun `busca por cuenta incluyendo las de destino`() =
            runTest {
                val transferencia =
                    transaccion("t", dia = 1).copy(
                        tipo = TipoDeTransaccion.TRANSFERENCIA,
                        cuentaDestinoId = CuentaId("c2"),
                    )
                val repo = FakeTransaccionRepository(listOf(transferencia))

                repo.observarDeCuenta(CuentaId("c2")).first().size shouldBe 1
                repo.observarDeCuenta(CuentaId("c1")).first().size shouldBe 1
                repo.observarDeCuenta(CuentaId("c3")).first().size shouldBe 0
            }
    }

    @Nested
    @DisplayName("FakePlanRepository")
    inner class Planes {
        private fun plan(mes: Mes) = PlanMensual(id = PlanId("p-$mes"), mes = mes)

        @Test
        fun `encuentra el plan de un mes`() =
            runTest {
                val repo = FakePlanRepository(listOf(plan(Mes.de(2026, 3))))

                repo.obtenerDe(Mes.de(2026, 3))?.mes shouldBe Mes.de(2026, 3)
                repo.obtenerDe(Mes.de(2026, 4)).shouldBeNull()
            }

        // Lo que necesita la materializacion cuando hay huecos: el ultimo plan
        // que exista, no el del mes inmediatamente anterior.
        @Test
        fun `devuelve el ultimo plan anterior saltando huecos`() =
            runTest {
                val repo =
                    FakePlanRepository(
                        listOf(plan(Mes.de(2026, 1)), plan(Mes.de(2025, 11))),
                    )

                repo.obtenerUltimoAnteriorA(Mes.de(2026, 6))?.mes shouldBe Mes.de(2026, 1)
            }

        @Test
        fun `no devuelve planes futuros`() =
            runTest {
                val repo = FakePlanRepository(listOf(plan(Mes.de(2026, 6))))

                repo.obtenerUltimoAnteriorA(Mes.de(2026, 3)).shouldBeNull()
            }

        @Test
        fun `el mes en curso no cuenta como anterior a si mismo`() =
            runTest {
                val repo = FakePlanRepository(listOf(plan(Mes.de(2026, 3))))

                repo.obtenerUltimoAnteriorA(Mes.de(2026, 3)).shouldBeNull()
            }

        @Test
        fun `el flujo emite al guardar`() =
            runTest {
                val repo = FakePlanRepository()

                repo.observarDe(Mes.de(2026, 3)).test {
                    awaitItem().shouldBeNull()

                    repo.guardar(plan(Mes.de(2026, 3)))
                    awaitItem()?.mes shouldBe Mes.de(2026, 3)
                }
            }
    }
}
