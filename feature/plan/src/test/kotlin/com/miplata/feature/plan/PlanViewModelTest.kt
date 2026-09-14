package com.miplata.feature.plan

import app.cash.turbine.test
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.RelojDelMes
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.MaterializarPlanDelMesUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private val MARZO = Mes.de(2026, 3)
private val FEBRERO = Mes.de(2026, 2)

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

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {
    private val planes = FakePlanRepository()
    private val ajustes = FakeAjustesRepository()

    private fun viewModel(mes: Mes = MARZO): PlanViewModel {
        val ids = GeneradorDeIdsSecuencial()
        return PlanViewModel(
            planes = planes,
            abrirPlan = AbrirPlanDelMesUseCase(planes, MaterializarPlanDelMesUseCase(ids)),
            ids = ids,
            ajustes = ajustes,
            reloj = RelojDelMes { mes },
        )
    }

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
    fun `muestra el plan guardado repartido en secciones`() =
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

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.ingresos shouldBe Money.deUnidades(2000)
                estado.salidas shouldBe Money.deUnidades(450)
                estado.disponible shouldBe Money.deUnidades(1550)
                estado.esBorrador shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Lo que hace que la app se sienta flexible: al abrir un mes nuevo el plan ya
    // esta armado, pero marcado como propuesta hasta que se toque algo.
    @Test
    fun `un mes sin plan se abre con el del mes anterior, como borrador`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("feb"),
                    mes = FEBRERO,
                    lineas = listOf(linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450)),
                ),
            )

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.esBorrador shouldBe true
                estado.salidas shouldBe Money.deUnidades(450)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `abrir un mes no guarda nada`() =
        runTest {
            planes.guardar(PlanMensual(id = PlanId("feb"), mes = FEBRERO))

            viewModel().uiState.test {
                esperarCargado()
                cancelAndIgnoreRemainingEvents()
            }

            planes.obtenerDe(MARZO) shouldBe null
        }

    // El borrador deja de serlo en cuanto el usuario toca algo: ahi pasa a ser su
    // plan y se guarda.
    @Test
    fun `la primera edicion guarda el borrador`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("feb"),
                    mes = FEBRERO,
                    lineas = listOf(linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 450)),
                ),
            )
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                inicial.esBorrador shouldBe true

                val arriendo =
                    inicial.secciones
                        .first { it.tipo == TipoDeLinea.GASTO_FIJO }
                        .lineas
                        .single()
                vm.alEvento(EventoDelPlan.CambiarMonto(arriendo, Money.deUnidades(500)))

                val despues = esperarHasta { !it.esBorrador }
                despues.salidas shouldBe Money.deUnidades(500)
                cancelAndIgnoreRemainingEvents()
            }

            planes.obtenerDe(MARZO) shouldBe planes.obtenerDe(MARZO)!!.copy()
        }

    @Test
    fun `anadir una linea la deja vacia y lista para escribir`() =
        runTest {
            planes.guardar(PlanMensual(id = PlanId("p"), mes = MARZO))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.AnadirLinea(TipoDeLinea.INGRESO))

                val estado = esperarHasta { it.secciones.any { s -> s.lineas.isNotEmpty() } }
                val nueva =
                    estado.secciones
                        .first { it.tipo == TipoDeLinea.INGRESO }
                        .lineas
                        .single()
                nueva.nombre shouldBe ""
                nueva.montoPlanificado shouldBe Money.ZERO
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Desactivar no es borrar: la linea sigue ahi pero deja de contar.
    @Test
    fun `desactivar una linea la saca de los totales sin eliminarla`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("f", "Gimnasio", TipoDeLinea.GASTO_FIJO, 60)),
                ),
            )
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                val gimnasio =
                    inicial.secciones
                        .first { it.tipo == TipoDeLinea.GASTO_FIJO }
                        .lineas
                        .single()

                vm.alEvento(EventoDelPlan.CambiarActiva(gimnasio, false))

                val estado = esperarHasta { it.salidas == Money.ZERO }
                estado.secciones
                    .first { it.tipo == TipoDeLinea.GASTO_FIJO }
                    .lineas.size shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar una linea la quita del plan`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("f", "Gimnasio", TipoDeLinea.GASTO_FIJO, 60)),
                ),
            )
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                val gimnasio =
                    inicial.secciones
                        .first { it.tipo == TipoDeLinea.GASTO_FIJO }
                        .lineas
                        .single()

                vm.alEvento(EventoDelPlan.EliminarLinea(gimnasio))

                esperarHasta { it.estaVacio }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `gastar mas de lo que entra deja el mes en sobregiro`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("i", "Sueldo", TipoDeLinea.INGRESO, 1000),
                            linea("f", "Arriendo", TipoDeLinea.GASTO_FIJO, 1200),
                        ),
                ),
            )

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.enSobregiro shouldBe true
                estado.disponible shouldBe Money.deUnidades(-200)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `navegar cambia de mes`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()

                vm.alEvento(EventoDelPlan.MesAnterior)
                esperarHasta { it.mes == FEBRERO }

                vm.alEvento(EventoDelPlan.MesSiguiente)
                esperarHasta { it.mes == MARZO }
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private suspend fun app.cash.turbine.TurbineTestContext<PlanUiState>.esperarCargado(): PlanUiState =
    esperarHasta { !it.cargando }

private suspend fun app.cash.turbine.TurbineTestContext<PlanUiState>.esperarHasta(
    condicion: (PlanUiState) -> Boolean,
): PlanUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
