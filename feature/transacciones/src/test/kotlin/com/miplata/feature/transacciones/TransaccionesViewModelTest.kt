package com.miplata.feature.transacciones

import app.cash.turbine.test
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCategoriaRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.AgruparMovimientosPorDiaUseCase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
private val BANCO = CuentaId("banco")
private val CARTERA = CuentaId("cartera")

private fun cuenta(
    id: CuentaId,
    nombre: String = id.valor,
    archivada: Boolean = false,
) = Cuenta(
    id = id,
    nombre = nombre,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = Money.ZERO,
    moneda = Moneda("USD"),
    archivada = archivada,
)

private fun movimiento(
    id: String,
    dia: Int,
    monto: Long,
    tipo: TipoDeTransaccion = TipoDeTransaccion.GASTO,
    destino: CuentaId? = null,
) = Transaccion(
    id = TransaccionId(id),
    fecha = LocalDate(2026, 3, dia),
    monto = Money.deUnidades(monto),
    tipo = tipo,
    cuentaOrigenId = BANCO,
    cuentaDestinoId = destino,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransaccionesViewModelTest {
    private val transacciones = FakeTransaccionRepository()
    private val cuentas = FakeCuentaRepository()
    private val categorias = FakeCategoriaRepository()
    private val planes = FakePlanRepository()
    private val ajustes = FakeAjustesRepository()

    /** @param hoy el dia del mes en el que se situa el calendario. */
    private fun viewModel(
        mes: Mes = MARZO,
        hoy: Int = 15,
    ) = TransaccionesViewModel(
        transacciones = transacciones,
        ids = GeneradorDeIdsSecuencial(),
        calendario =
            object : Calendario {
                override fun hoy() = LocalDate(mes.anio, mes.numeroDeMes, hoy)
            },
        cuentas = cuentas,
        categorias = categorias,
        planes = planes,
        ajustes = ajustes,
        agruparPorDia = AgruparMovimientosPorDiaUseCase(),
    )

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    @Test
    fun `sin movimientos la lista queda vacia`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.estaVacio shouldBe true
                estado.faltanCuentas shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Toda transaccion sale de una cuenta: sin ninguna, no hay nada que anotar.
    @Test
    fun `sin cuentas avisa en vez de ofrecer un formulario imposible`() =
        runTest {
            viewModel().uiState.test {
                esperarCargado().faltanCuentas shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `agrupa los movimientos por dia con sus totales`() =
        runTest {
            cuentas.guardar(cuenta(BANCO, "Banco"))
            transacciones.guardar(movimiento("a", dia = 10, monto = 30))
            transacciones.guardar(movimiento("b", dia = 10, monto = 20))
            transacciones.guardar(
                movimiento("c", dia = 12, monto = 2000, tipo = TipoDeTransaccion.INGRESO),
            )

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.dias.size shouldBe 2
                estado.dias.first().fecha shouldBe LocalDate(2026, 3, 12)
                estado.dias.last().neto shouldBe Money.deUnidades(-50)
                estado.ingresos shouldBe Money.deUnidades(2000)
                estado.gastos shouldBe Money.deUnidades(50)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resuelve el nombre de la cuenta y de la categoria`() =
        runTest {
            cuentas.guardar(cuenta(BANCO, "Cuenta del banco"))
            categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))
            transacciones.guardar(
                movimiento("a", dia = 10, monto = 30).copy(categoriaId = CategoriaId("comida")),
            )

            viewModel().uiState.test {
                val fila =
                    esperarHasta { it.dias.isNotEmpty() }
                        .dias
                        .single()
                        .movimientos
                        .single()

                fila.cuenta shouldBe "Cuenta del banco"
                fila.categoria shouldBe "Comida"
                cancelAndIgnoreRemainingEvents()
            }
        }

    // El puente con el plan: es lo que permite que el Resumen diga
    // "planificaste 400 de comida, llevas gastados 520".
    @Test
    fun `resuelve el nombre de la linea del plan`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            planes.guardar(
                PlanMensual(
                    id = PlanId("p"),
                    mes = MARZO,
                    lineas =
                        listOf(
                            LineaDePlan(
                                id = LineaId("comida"),
                                nombre = "Comida",
                                tipo = TipoDeLinea.GASTO_VARIABLE,
                                montoPlanificado = Money.deUnidades(400),
                            ),
                        ),
                ),
            )
            transacciones.guardar(
                movimiento("a", dia = 10, monto = 30).copy(lineaDePlanId = LineaId("comida")),
            )

            viewModel().uiState.test {
                esperarHasta { it.dias.isNotEmpty() }
                    .dias
                    .single()
                    .movimientos
                    .single()
                    .lineaDePlan shouldBe "Comida"
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Archivar una cuenta es decir "ya no la uso": no tiene sentido ofrecerla
    // para anotar algo nuevo.
    @Test
    fun `las cuentas archivadas no se ofrecen`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            cuentas.guardar(cuenta(CARTERA, archivada = true))

            viewModel().uiState.test {
                esperarCargado().cuentas.map { it.id } shouldBe listOf(BANCO)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La diferencia entre anotar un gasto en dos toques o en seis.
    @Test
    fun `un movimiento nuevo llega con la fecha de hoy y la primera cuenta`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel(hoy = 14)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)

                val editor = esperarHasta { it.editor != null }.editor.shouldNotBeNull()
                editor.fecha shouldBe LocalDate(2026, 3, 14)
                editor.cuentaOrigenId shouldBe BANCO
                editor.tipo shouldBe TipoDeTransaccion.GASTO
                editor.puedeGuardar shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Anotar algo con la fecha de hoy mientras miras otro mes lo haria
    // desaparecer de la lista nada mas guardarlo.
    @Test
    fun `si se mira otro mes la fecha por defecto cae dentro de ese mes`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel(hoy = 14)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.MesAnterior)
                esperarHasta { it.mes == FEBRERO }
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)

                esperarHasta { it.editor != null }
                    .editor
                    .shouldNotBeNull()
                    .fecha shouldBe LocalDate(2026, 2, 1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Quien cobra el 25 tiene su mes del 25 al 24, y la fecha por defecto de un
    // movimiento nuevo tiene que caer dentro de ese periodo.
    @Test
    fun `la fecha por defecto respeta el primer dia del mes financiero`() =
        runTest {
            ajustes.guardar(Ajustes(primerDiaDelMesFinanciero = 25))
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel(mes = MARZO, hoy = 10)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.MesSiguiente)
                esperarHasta { it.mes == Mes.de(2026, 4) }
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)

                // Con primerDia = 25, el periodo de abril va del 25 de abril al
                // 24 de mayo (ver PeriodoMensual). Hoy es el 10 de marzo, que
                // queda fuera, asi que la fecha por defecto es el inicio.
                esperarHasta { it.editor != null }
                    .editor
                    .shouldNotBeNull()
                    .fecha shouldBe LocalDate(2026, 4, 25)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `guardar un gasto lo deja en la lista y cierra el editor`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel(hoy = 10)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Monto(Money.deUnidades(42)))
                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Nota("  Cena con Ana  "))
                vm.alEvento(EventoDeMovimientos.Guardar)

                val estado = esperarHasta { it.dias.isNotEmpty() }
                estado.editor.shouldBeNull()
                val guardado =
                    estado.dias
                        .single()
                        .movimientos
                        .single()
                guardado.monto shouldBe Money.deUnidades(42)
                guardado.nota shouldBe "Cena con Ana"
                estado.gastos shouldBe Money.deUnidades(42)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // El modelo exige monto positivo; el editor lo comprueba antes de construirlo
    // para que el boton se vea apagado en vez de fallar al pulsarlo.
    @Test
    fun `un movimiento sin importe no se guarda`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)
                val editor = esperarHasta { it.editor != null }.editor.shouldNotBeNull()
                editor.puedeGuardar shouldBe false

                vm.alEvento(EventoDeMovimientos.Guardar)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `una transferencia necesita dos cuentas distintas`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            cuentas.guardar(cuenta(CARTERA))
            val vm = viewModel(hoy = 10)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Monto(Money.deUnidades(100)))
                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Tipo(TipoDeTransaccion.TRANSFERENCIA))
                esperarHasta { it.editor?.esTransferencia == true }
                    .editor
                    .shouldNotBeNull()
                    .puedeGuardar shouldBe false

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.CuentaDestino(CARTERA))
                esperarHasta { it.editor?.cuentaDestinoId != null }
                    .editor
                    .shouldNotBeNull()
                    .puedeGuardar shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Sin esta limpieza se podria guardar un gasto con cuenta de destino, que el
    // modelo rechaza: el error saldria al pulsar Guardar y no antes.
    @Test
    fun `cambiar de transferencia a gasto olvida la cuenta de destino`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            cuentas.guardar(cuenta(CARTERA))
            val vm = viewModel(hoy = 10)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Tipo(TipoDeTransaccion.TRANSFERENCIA))
                vm.alEvento(EventoDeMovimientos.CambioDeCampo.CuentaDestino(CARTERA))
                esperarHasta { it.editor?.cuentaDestinoId != null }

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Tipo(TipoDeTransaccion.GASTO))

                esperarHasta { it.editor?.esTransferencia == false }
                    .editor
                    .shouldNotBeNull()
                    .cuentaDestinoId
                    .shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Una transferencia no es un gasto: es dinero cambiando de sitio.
    @Test
    fun `pasar a transferencia olvida la categoria y la linea de plan`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            cuentas.guardar(cuenta(CARTERA))
            categorias.guardar(Categoria(id = CategoriaId("comida"), nombre = "Comida"))
            val vm = viewModel(hoy = 10)

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeMovimientos.AnotarMovimiento)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Categoria(CategoriaId("comida")))
                esperarHasta { it.editor?.categoriaId != null }

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Tipo(TipoDeTransaccion.TRANSFERENCIA))

                val editor = esperarHasta { it.editor?.esTransferencia == true }.editor.shouldNotBeNull()
                editor.categoriaId.shouldBeNull()
                editor.lineaDePlanId.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editar un movimiento carga sus valores y los guarda cambiados`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            transacciones.guardar(movimiento("a", dia = 10, monto = 30))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarHasta { it.dias.isNotEmpty() }
                val fila =
                    inicial.dias
                        .single()
                        .movimientos
                        .single()
                vm.alEvento(EventoDeMovimientos.EditarMovimiento(fila.transaccion))

                val editor = esperarHasta { it.editor != null }.editor.shouldNotBeNull()
                editor.esNuevo shouldBe false
                editor.monto shouldBe Money.deUnidades(30)

                vm.alEvento(EventoDeMovimientos.CambioDeCampo.Monto(Money.deUnidades(55)))
                vm.alEvento(EventoDeMovimientos.Guardar)

                val estado = esperarHasta { it.gastos == Money.deUnidades(55) }
                estado.dias
                    .single()
                    .movimientos.size shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar quita el movimiento de la lista`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            transacciones.guardar(movimiento("a", dia = 10, monto = 30))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarHasta { it.dias.isNotEmpty() }
                vm.alEvento(
                    EventoDeMovimientos.EditarMovimiento(
                        inicial.dias
                            .single()
                            .movimientos
                            .single()
                            .transaccion,
                    ),
                )
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeMovimientos.Eliminar)

                esperarHasta { it.estaVacio }.editor.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `los movimientos de otro mes no aparecen`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            transacciones.guardar(
                Transaccion(
                    id = TransaccionId("viejo"),
                    fecha = LocalDate(2026, 2, 20),
                    monto = Money.deUnidades(99),
                    tipo = TipoDeTransaccion.GASTO,
                    cuentaOrigenId = BANCO,
                ),
            )

            viewModel().uiState.test {
                esperarCargado().estaVacio shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `navegar cambia de mes`() =
        runTest {
            cuentas.guardar(cuenta(BANCO))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()

                vm.alEvento(EventoDeMovimientos.MesAnterior)
                esperarHasta { it.mes == FEBRERO }

                vm.alEvento(EventoDeMovimientos.MesSiguiente)
                esperarHasta { it.mes == MARZO }
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private suspend fun app.cash.turbine.TurbineTestContext<TransaccionesUiState>.esperarCargado(): TransaccionesUiState =
    esperarHasta { !it.cargando }

private suspend fun app.cash.turbine.TurbineTestContext<TransaccionesUiState>.esperarHasta(
    condicion: (TransaccionesUiState) -> Boolean,
): TransaccionesUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
