package com.miplata.feature.plan

import app.cash.turbine.test
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.CategoriaId
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
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.MaterializarPlanDelMesUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
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
    private val transacciones = FakeTransaccionRepository()
    private val ajustes = FakeAjustesRepository()

    private fun viewModel(
        mes: Mes = MARZO,
        repositorio: PlanRepository = planes,
    ): PlanViewModel {
        val ids = GeneradorDeIdsSecuencial()
        return PlanViewModel(
            planes = repositorio,
            transacciones = transacciones,
            abrirPlan = AbrirPlanDelMesUseCase(repositorio, MaterializarPlanDelMesUseCase(ids)),
            ids = ids,
            ajustes = ajustes,
            calendario =
                object : Calendario {
                    override fun hoy() = LocalDate(mes.anio, mes.numeroDeMes, 1)
                },
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

                vm.alEvento(EventoDelPlan.EditarLinea(inicial.lineaDe(TipoDeLinea.GASTO_FIJO)))
                vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(500)))
                vm.alEvento(EventoDelPlan.GuardarLinea)

                val despues = esperarHasta { !it.esBorrador }
                despues.salidas shouldBe Money.deUnidades(500)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Lo que hay en la hoja no es de nadie hasta pulsar Guardar. */
    @Test
    fun `una linea nueva se guarda al pulsar guardar y no antes`() =
        runTest {
            planes.guardar(PlanMensual(id = PlanId("p"), mes = MARZO))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.NuevaLinea)
                vm.alEvento(EventoDelPlan.CambioEnEditor.Nombre("  Sueldo  "))
                vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(2000)))
                esperarHasta { it.editor?.nombre == "  Sueldo  " }
                planes.obtenerDe(MARZO)!!.lineas shouldBe emptyList()

                vm.alEvento(EventoDelPlan.GuardarLinea)

                val estado = esperarHasta { !it.estaVacio }
                estado.editor.shouldBeNull()
                val nueva = estado.lineaDe(TipoDeLinea.INGRESO)
                nueva.nombre shouldBe "Sueldo"
                nueva.montoPlanificado shouldBe Money.deUnidades(2000)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Con el plan vacio se empieza por los ingresos, como dice el aviso. */
    @Test
    fun `con el plan vacio la hoja propone un ingreso`() =
        runTest {
            planes.guardar(PlanMensual(id = PlanId("p"), mes = MARZO))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.NuevaLinea)

                esperarHasta { it.editor != null }.editor?.tipo shouldBe TipoDeLinea.INGRESO
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelar no toca el plan`() =
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
                vm.alEvento(EventoDelPlan.EditarLinea(inicial.lineaDe(TipoDeLinea.GASTO_FIJO)))
                vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(999)))
                esperarHasta { it.editor?.monto == Money.deUnidades(999) }
                vm.alEvento(EventoDelPlan.CerrarEditor)

                esperarHasta { it.editor == null }
                cancelAndIgnoreRemainingEvents()
            }
            planes
                .obtenerDe(MARZO)!!
                .lineas
                .single()
                .montoPlanificado shouldBe Money.deUnidades(60)
        }

    /**
     * La hoja no enseña la categoria, la cuenta ni el dia de una linea. Editar su
     * nombre o su importe no puede borrarlos: la linea del sueldo que crea la
     * bienvenida viene enganchada a una cuenta y a un dia de cobro.
     */
    @Test
    fun `editar una linea conserva lo que la hoja no enseña`() =
        runTest {
            val sueldo =
                linea("i", "Sueldo", TipoDeLinea.INGRESO, 2000)
                    .copy(cuentaId = CuentaId("banco"), categoriaId = CategoriaId("sueldo"), diaDelMes = 25)
            planes.guardar(PlanMensual(id = PlanId("p"), mes = MARZO, lineas = listOf(sueldo)))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.EditarLinea(sueldo))
                vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(2100)))
                vm.alEvento(EventoDelPlan.GuardarLinea)
                esperarHasta { it.ingresos == Money.deUnidades(2100) }
                cancelAndIgnoreRemainingEvents()
            }
            planes.obtenerDe(MARZO)!!.lineas.single() shouldBe sueldo.copy(montoPlanificado = Money.deUnidades(2100))
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
                vm.alEvento(EventoDelPlan.EditarLinea(inicial.lineaDe(TipoDeLinea.GASTO_FIJO)))
                vm.alEvento(EventoDelPlan.CambioEnEditor.Activa(false))
                vm.alEvento(EventoDelPlan.GuardarLinea)

                val estado = esperarHasta { it.salidas == Money.ZERO }
                estado.secciones
                    .first { it.tipo == TipoDeLinea.GASTO_FIJO }
                    .lineas.size shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar una linea la quita del plan y la ofrece para deshacer`() =
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
                esperarCargado()
                vm.alEvento(EventoDelPlan.EliminarLinea(LineaId("f")))

                esperarHasta { it.estaVacio }.eliminada?.nombre shouldBe "Gimnasio"
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Deshacer tiene que dejarlo todo como estaba, incluidos los movimientos.
     *
     * Al borrar la linea, la base suelta los movimientos enganchados a ella
     * (`ON DELETE SET NULL`); volver a crearla no los engancha. Aqui el
     * repositorio hace lo mismo que Room, y el resumen tiene que volver a saber
     * que ese gasto era del gimnasio.
     */
    @Test
    fun `deshacer una eliminacion devuelve la linea a su sitio y le engancha sus movimientos`() =
        runTest {
            val lineas =
                listOf(
                    linea("a", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
                    linea("g", "Gimnasio", TipoDeLinea.GASTO_FIJO, 60),
                    linea("l", "Luz", TipoDeLinea.GASTO_FIJO, 40),
                )
            planes.guardar(PlanMensual(id = PlanId("p"), mes = MARZO, lineas = lineas))
            transacciones.guardar(gastoDe("t1", "g"))
            transacciones.guardar(gastoDe("t2", "g"))
            transacciones.guardar(gastoDe("t3", "a"))
            val vm = viewModel(repositorio = ComoRoom(planes, transacciones))

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.EliminarLinea(LineaId("g")))
                esperarHasta { it.eliminada != null }
                transacciones.obtener(TransaccionId("t1"))?.lineaDePlanId.shouldBeNull()

                vm.alEvento(EventoDelPlan.DeshacerEliminacion)

                esperarHasta { it.eliminada == null && it.lineaDe(TipoDeLinea.GASTO_FIJO, 1).id == LineaId("g") }
                cancelAndIgnoreRemainingEvents()
            }
            planes.obtenerDe(MARZO)!!.lineas.map { it.id.valor } shouldBe listOf("a", "g", "l")
            transacciones.obtener(TransaccionId("t1"))?.lineaDePlanId shouldBe LineaId("g")
            transacciones.obtener(TransaccionId("t2"))?.lineaDePlanId shouldBe LineaId("g")
            transacciones.obtener(TransaccionId("t3"))?.lineaDePlanId shouldBe LineaId("a")
        }

    /** Si el aviso se va sin pulsar Deshacer, la eliminacion se queda. */
    @Test
    fun `olvidar la eliminacion la deja hecha`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas = listOf(linea("g", "Gimnasio", TipoDeLinea.GASTO_FIJO, 60)),
                ),
            )
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDelPlan.EliminarLinea(LineaId("g")))
                esperarHasta { it.eliminada != null }
                vm.alEvento(EventoDelPlan.OlvidarEliminacion)
                vm.alEvento(EventoDelPlan.DeshacerEliminacion)

                esperarHasta { it.eliminada == null }.estaVacio shouldBe true
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

    /**
     * Dos guardados seguidos, con una base lenta, no se pisan: cada uno se
     * aplica sobre lo ultimo que se guardo y no sobre lo que habia en
     * pantalla. Con la edicion en la fila, escribir un nombre generaba una
     * edicion por tecla y "Transporte" quedo como "Trapotr" en el emulador; la
     * hoja guarda de una vez, pero el orden sigue importando.
     */
    @Test
    fun `los guardados seguidos se aplican en orden y sin pisarse aunque la base tarde`() =
        runTest {
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            linea("a", "Arriendo", TipoDeLinea.GASTO_FIJO, 450),
                            linea("l", "Luz", TipoDeLinea.GASTO_FIJO, 40),
                        ),
                ),
            )
            val vm = viewModel(repositorio = BaseQueTarda(planes))
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            // Todo seguido, sin dar tiempo a que vuelva ninguna escritura.
            vm.alEvento(EventoDelPlan.EditarLinea(linea("a", "Arriendo", TipoDeLinea.GASTO_FIJO, 450)))
            vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(500)))
            vm.alEvento(EventoDelPlan.GuardarLinea)
            vm.alEvento(EventoDelPlan.EditarLinea(linea("l", "Luz", TipoDeLinea.GASTO_FIJO, 40)))
            vm.alEvento(EventoDelPlan.CambioEnEditor.Monto(Money.deUnidades(55)))
            vm.alEvento(EventoDelPlan.GuardarLinea)
            advanceUntilIdle()

            planes.obtenerDe(MARZO)!!.lineas.map { it.montoPlanificado } shouldBe
                listOf(Money.deUnidades(500), Money.deUnidades(55))
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

/**
 * Una base que tarda en escribir, y no siempre lo mismo, como Room: cada
 * guardado es una transaccion en otro hilo.
 *
 * Cada escritura tarda menos que la anterior, asi que si se lanzan en paralelo
 * la primera es la ultima en terminar y pisa a todas las demas.
 */
private class BaseQueTarda(
    private val real: FakePlanRepository,
) : PlanRepository by real {
    private var escrituras = 0

    override suspend fun guardar(plan: PlanMensual) {
        delay((LATENCIA_INICIAL - PASO * escrituras++).coerceAtLeast(0))
        real.guardar(plan)
    }

    private companion object {
        const val LATENCIA_INICIAL = 100L
        const val PASO = 10L
    }
}

private fun PlanUiState.lineaDe(
    tipo: TipoDeLinea,
    posicion: Int = 0,
): LineaDePlan = secciones.first { it.tipo == tipo }.lineas[posicion]

private fun gastoDe(
    id: String,
    lineaId: String,
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, 3, 10),
    monto = Money.deUnidades(10),
    tipo = TipoDeTransaccion.GASTO,
    cuentaOrigenId = CuentaId("banco"),
    lineaDePlanId = LineaId(lineaId),
)

/**
 * Un repositorio de planes que hace lo mismo que Room al quitar una linea:
 * soltar los movimientos enganchados a ella (`ON DELETE SET NULL`). Los
 * repositorios en memoria no tienen claves foraneas, y sin esto un deshacer que
 * no volviera a enganchar los movimientos pasaria el test.
 */
private class ComoRoom(
    private val real: FakePlanRepository,
    private val transacciones: FakeTransaccionRepository,
) : PlanRepository by real {
    override suspend fun guardar(plan: PlanMensual) {
        val antes =
            real
                .obtenerDe(plan.mes)
                ?.lineas
                .orEmpty()
                .map { it.id }
                .toSet()
        val quitadas = antes - plan.lineas.map { it.id }.toSet()
        transacciones.observarTodas().first().filter { it.lineaDePlanId in quitadas }.forEach {
            transacciones.guardar(it.copy(lineaDePlanId = null))
        }
        real.guardar(plan)
    }
}
