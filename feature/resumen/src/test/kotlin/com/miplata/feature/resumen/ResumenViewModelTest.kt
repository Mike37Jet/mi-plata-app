package com.miplata.feature.resumen

import app.cash.turbine.test
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test

private val MARZO = Mes.de(2026, 3)
private val FEBRERO = Mes.de(2026, 2)
private val CUENTA = CuentaId("c")

private fun linea(
    id: String,
    nombre: String,
    tipo: TipoDeLinea,
    monto: Long,
) = LineaDePlan(
    id = LineaId(id),
    nombre = nombre,
    tipo = tipo,
    montoPlanificado = Money.deUnidades(monto),
)

private fun movimiento(
    id: String,
    dia: Int,
    monto: Long,
    tipo: TipoDeTransaccion,
    lineaId: String? = null,
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, 3, dia),
    monto = Money.deUnidades(monto),
    tipo = tipo,
    cuentaOrigenId = CUENTA,
    lineaDePlanId = lineaId?.let(::LineaId),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModelTest {
    private val planes = FakePlanRepository()
    private val transacciones = FakeTransaccionRepository()
    private val ajustes = FakeAjustesRepository()

    /** @param hoy el dia del mes en el que se situa el calendario, para el progreso. */
    private fun viewModel(
        mes: Mes = MARZO,
        hoy: Int = 1,
    ) = ResumenViewModel(
        planes = planes,
        transacciones = transacciones,
        ajustes = ajustes,
        calendario =
            object : Calendario {
                override fun hoy() = LocalDate(mes.anio, mes.numeroDeMes, hoy)
            },
        calcular = CalcularResumenMensualUseCase(),
    )

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    @Test
    fun `arranca en el mes actual`() =
        runTest {
            viewModel().uiState.test {
                awaitItem().mes shouldBe MARZO
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sin plan y sin movimientos no hay nada que resumir`() =
        runTest {
            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.hayPlan shouldBe false
                estado.disponibleReal shouldBe Money.ZERO
                estado.enSobregiro shouldBe false
                estado.progresoDelGasto shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cruza el plan con los movimientos del mes`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000),
                            linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
                        ),
                ),
            )
            transacciones.guardar(movimiento("t1", 5, 2000, TipoDeTransaccion.INGRESO, "i"))
            transacciones.guardar(movimiento("t2", 6, 450, TipoDeTransaccion.GASTO, "f"))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.hayPlan shouldBe true
                estado.ingresosPlanificados shouldBe Money.deUnidades(2000)
                estado.ingresosReales shouldBe Money.deUnidades(2000)
                estado.gastosReales shouldBe Money.deUnidades(450)
                estado.disponibleReal shouldBe Money.deUnidades(1550)
                estado.enSobregiro shouldBe false
                estado.desviaciones.shouldBeEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La distincion que justifica la pantalla: el mes puede estar en rojo porque
    // gastaste de mas o porque el ingreso no llego, y no se arreglan igual.
    @Test
    fun `un ingreso que no llega deja el mes en rojo sin haber gastado de mas`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000),
                            linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
                        ),
                ),
            )
            transacciones.guardar(movimiento("t2", 6, 450, TipoDeTransaccion.GASTO, "f"))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.enSobregiro shouldBe true
                estado.sobregiroPorIngresosQueNoLlegaron shouldBe true
                estado.disponibleReal shouldBe Money.deUnidades(-450)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `pasarse gastando es un sobregiro que no se explica por el ingreso`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("i", "Sueldo", TipoDeLinea.INGRESO, 1000)),
                ),
            )
            transacciones.guardar(movimiento("t1", 2, 1000, TipoDeTransaccion.INGRESO, "i"))
            transacciones.guardar(movimiento("t2", 3, 1200, TipoDeTransaccion.GASTO))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.enSobregiro shouldBe true
                estado.sobregiroPorIngresosQueNoLlegaron shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La senal util del resumen: a dia 2 del mes con el 80% del presupuesto
    // gastado, el total todavia cuadra pero el ritmo no.
    @Test
    fun `avisa cuando el gasto va mas deprisa que el mes`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 400)),
                ),
            )
            transacciones.guardar(movimiento("t1", 2, 320, TipoDeTransaccion.GASTO, "f"))

            viewModel(hoy = 2).uiState.test {
                val estado = esperarCargado()

                estado.progresoDelGasto!! shouldBeGreaterThan estado.progresoDelMes
                estado.gastaMasDeprisaQuePasaElMes shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `gastar al ritmo del mes no dispara ningun aviso`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("f", "Comida", TipoDeLinea.GASTO_VARIABLE, 310)),
                ),
            )
            transacciones.guardar(movimiento("t1", 2, 10, TipoDeTransaccion.GASTO, "f"))

            viewModel(hoy = 15).uiState.test {
                val estado = esperarCargado()

                estado.gastaMasDeprisaQuePasaElMes shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `las desviaciones desfavorables salen ordenadas de peor a menos mala`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("comida", "Comida", TipoDeLinea.GASTO_VARIABLE, 400),
                            linea("ocio", "Ocio", TipoDeLinea.GASTO_VARIABLE, 100),
                            linea("luz", "Luz", TipoDeLinea.GASTO_FIJO, 80),
                        ),
                ),
            )
            transacciones.guardar(movimiento("t1", 4, 450, TipoDeTransaccion.GASTO, "comida"))
            transacciones.guardar(movimiento("t2", 5, 300, TipoDeTransaccion.GASTO, "ocio"))
            transacciones.guardar(movimiento("t3", 6, 70, TipoDeTransaccion.GASTO, "luz"))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.desviaciones.map { it.linea.nombre } shouldBe listOf("Ocio", "Comida")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Un movimiento de otro mes no debe ensuciar este resumen.
    @Test
    fun `los movimientos de otro mes no cuentan`() =
        runTest {
            transacciones.guardar(
                Transaccion(
                    id = TransaccionId("viejo"),
                    fecha = LocalDate(2026, 2, 20),
                    monto = Money.deUnidades(999),
                    tipo = TipoDeTransaccion.GASTO,
                    cuentaOrigenId = CUENTA,
                ),
            )

            viewModel().uiState.test {
                esperarCargado().gastosReales shouldBe Money.ZERO
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Quien cobra el 25 tiene su mes del 25 al 24, y el resumen lo respeta.
    @Test
    fun `el periodo sale de los ajustes, no del calendario`() =
        runTest {
            ajustes.guardar(Ajustes(primerDiaDelMesFinanciero = 25))
            transacciones.guardar(movimiento("t1", 26, 100, TipoDeTransaccion.GASTO))

            viewModel(mes = FEBRERO).uiState.test {
                // Marzo empieza el 25 de febrero: el gasto del 26 de marzo cae
                // fuera del periodo de febrero.
                esperarCargado().gastosReales shouldBe Money.ZERO
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `navegar cambia de mes`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()

                vm.alEvento(EventoDelResumen.MesAnterior)
                esperarHasta { it.mes == FEBRERO }

                vm.alEvento(EventoDelResumen.MesSiguiente)
                esperarHasta { it.mes == MARZO }
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private suspend fun app.cash.turbine.TurbineTestContext<ResumenUiState>.esperarCargado(): ResumenUiState =
    esperarHasta { !it.cargando }

private suspend fun app.cash.turbine.TurbineTestContext<ResumenUiState>.esperarHasta(
    condicion: (ResumenUiState) -> Boolean,
): ResumenUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
