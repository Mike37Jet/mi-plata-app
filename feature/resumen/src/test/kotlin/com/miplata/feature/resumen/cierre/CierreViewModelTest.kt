package com.miplata.feature.resumen.cierre

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCierreRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.CalcularCierreDeMesUseCase
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.CerrarMesUseCase
import com.miplata.core.domain.usecase.MaterializarPlanDelMesUseCase
import com.miplata.core.domain.usecase.ReabrirMesUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test

private val MARZO = Mes.de(2026, 3)
private val USD = Moneda("USD")

private val NORMAL =
    Cuenta(CuentaId("normal"), "Normal", TipoDeCuenta.BANCARIA, Money.ZERO, USD, rol = RolDeCuenta.Principal)
private val LIBERTAD =
    Cuenta(
        CuentaId("libertad"),
        "Libertad financiera",
        TipoDeCuenta.AHORRO,
        Money.deUnidades(100),
        USD,
        rol = RolDeCuenta.Sobre(Reparto.Porcentaje(10), intocable = true),
    )
private val DIVERSION =
    Cuenta(CuentaId("diversion"), "Diversion", TipoDeCuenta.BANCARIA, Money.ZERO, USD, rol = RolDeCuenta.Sobre())

private val PLAN =
    PlanMensual(
        id = PlanId("marzo"),
        mes = MARZO,
        lineas =
            listOf(
                LineaDePlan(LineaId("sueldo"), "Sueldo", TipoDeLinea.INGRESO, Money.deUnidades(633)),
                LineaDePlan(LineaId("arriendo"), "Arriendo", TipoDeLinea.GASTO_FIJO, Money.deUnidades(175)),
            ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class CierreViewModelTest {
    private val planes = FakePlanRepository(listOf(PLAN))
    private val cuentas = FakeCuentaRepository(listOf(NORMAL, LIBERTAD, DIVERSION))
    private val transacciones = FakeTransaccionRepository()
    private val cierres = FakeCierreRepository()
    private val calendario =
        object : Calendario {
            override fun hoy() = LocalDate(2026, 4, 1)
        }

    private fun viewModel(): CierreViewModel {
        val ids = GeneradorDeIdsSecuencial()
        return CierreViewModel(
            estadoGuardado = SavedStateHandle(mapOf(ARGUMENTO_MES to MARZO.toString())),
            planes = planes,
            abrirPlan = AbrirPlanDelMesUseCase(planes, MaterializarPlanDelMesUseCase(ids)),
            cuentas = cuentas,
            transacciones = transacciones,
            ajustes = FakeAjustesRepository(),
            cierres = cierres,
            calendario = calendario,
            calcularPlan = CalcularPlanPorCuentasUseCase(),
            cerrarMes = CerrarMesUseCase(transacciones, cierres, CalcularCierreDeMesUseCase(ids), calendario),
            reabrirMes = ReabrirMesUseCase(transacciones, cierres),
        )
    }

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    @Test
    fun `propone lo que calcula la app como saldo del banco`() =
        runTest {
            viewModel().uiState.test {
                val estado = esperarHasta { it.filas.isNotEmpty() }

                estado.cerrado shouldBe false
                estado.filas.map { it.cuenta.id } shouldBe listOf(NORMAL.id, LIBERTAD.id, DIVERSION.id)
                estado.filas.first { it.cuenta.id == LIBERTAD.id }.real shouldBe Money.deUnidades(100)
                estado.puedeCerrar shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sin cuenta principal explica que falta`() =
        runTest {
            cuentas.sustituirTodo(listOf(DIVERSION))

            viewModel().uiState.test {
                esperarHasta { !it.cargando }.sinMetodo shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un saldo por escribir no deja cerrar`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarHasta { it.filas.isNotEmpty() }
                vm.alEvento(EventoDelCierre.CambiarReal(DIVERSION.id, null))

                esperarHasta { !it.puedeCerrar }.filas.first { it.cuenta.id == DIVERSION.id }.real shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `solo un sobre que bajo puede haber cubierto a la principal`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarHasta { it.filas.isNotEmpty() }
                vm.alEvento(EventoDelCierre.CambiarReal(DIVERSION.id, Money.deUnidades(20)))
                vm.alEvento(EventoDelCierre.CambiarReal(NORMAL.id, Money.deUnidades(-500)))

                val estado =
                    esperarHasta { e ->
                        e.filas.first { it.cuenta.id == DIVERSION.id }.real ==
                            Money.deUnidades(20)
                    }
                estado.filas.first { it.cuenta.id == DIVERSION.id }.puedeCubrir shouldBe true
                estado.filas.first { it.cuenta.id == NORMAL.id }.puedeCubrir shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `avisa si una intocable termina con menos de lo que empezo`() {
        val fila =
            FilaDeCierre(
                LIBERTAD,
                empiezaCon = Money.deUnidades(100),
                esperado = Money.deUnidades(163),
                real = Money.deUnidades(90),
            )

        fila.intocableBajo shouldBe true
        fila.copy(real = Money.deUnidades(150)).intocableBajo shouldBe false
    }

    @Test
    fun `cerrar guarda el cierre, deja los movimientos y muestra el mes cerrado`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarHasta { it.filas.isNotEmpty() }
                vm.alEvento(EventoDelCierre.CambiarReal(DIVERSION.id, Money.deUnidades(20)))
                esperarHasta { e -> e.filas.first { it.cuenta.id == DIVERSION.id }.real == Money.deUnidades(20) }
                vm.alEvento(EventoDelCierre.Cerrar)

                val cerrado = esperarHasta { it.cerrado }
                cerrado.filas.first { it.cuenta.id == DIVERSION.id }.diferencia shouldBe Money.deCentavos(-4_330)
                cierres.obtenerDe(MARZO).shouldNotBeNull()
                transacciones.observarTodas().first().any { it.esSinDetalle } shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reabrir borra el cierre y sus movimientos`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarHasta { it.filas.isNotEmpty() }
                vm.alEvento(EventoDelCierre.Cerrar)
                esperarHasta { it.cerrado }

                vm.alEvento(EventoDelCierre.Reabrir)

                esperarHasta { !it.cerrado && !it.trabajando }
                // No habia nada anotado: todo lo que hay lo puso el cierre.
                transacciones.observarTodas().first().shouldBeEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private suspend fun TurbineTestContext<CierreUiState>.esperarHasta(
    condicion: (CierreUiState) -> Boolean,
): CierreUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
