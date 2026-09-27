package com.miplata.feature.cuentas

import app.cash.turbine.test
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import com.miplata.core.domain.usecase.CalcularSaldosDeCuentasUseCase
import com.miplata.core.domain.usecase.GuardarCuentaUseCase
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

private val USD = Moneda("USD")

private fun cuenta(
    id: String,
    nombre: String = id,
    saldoInicial: Long = 0,
    tipo: TipoDeCuenta = TipoDeCuenta.BANCARIA,
) = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = tipo,
    saldoInicial = Money.deUnidades(saldoInicial),
    moneda = USD,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CuentasViewModelTest {
    private val cuentas = FakeCuentaRepository()
    private val transacciones = FakeTransaccionRepository()
    private val ajustes = FakeAjustesRepository()

    private fun viewModel() =
        CuentasViewModel(
            cuentas = cuentas,
            guardarCuenta = GuardarCuentaUseCase(cuentas),
            ids = GeneradorDeIdsSecuencial(),
            transacciones = transacciones,
            ajustes = ajustes,
            calcularSaldos = CalcularSaldosDeCuentasUseCase(),
        )

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    @Test
    fun `sin cuentas la pantalla queda vacia`() =
        runTest {
            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.estaVacio shouldBe true
                estado.total shouldBe Money.ZERO
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `muestra las cuentas con su saldo y el total`() =
        runTest {
            cuentas.guardar(cuenta("banco", saldoInicial = 1000))
            cuentas.guardar(cuenta("cartera", saldoInicial = 50))

            viewModel().uiState.test {
                val estado = esperarCargado()

                estado.cuentas.size shouldBe 2
                estado.total shouldBe Money.deUnidades(1050)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // El saldo no esta guardado en ningun sitio: si cambia un movimiento, cambia
    // el saldo, y la pantalla se entera sin que nadie la refresque.
    @Test
    fun `un gasto nuevo baja el saldo sin tocar la cuenta`() =
        runTest {
            cuentas.guardar(cuenta("banco", saldoInicial = 1000))
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado().total shouldBe Money.deUnidades(1000)

                transacciones.guardar(
                    Transaccion(
                        id = TransaccionId("g"),
                        fecha = LocalDate(2026, 3, 10),
                        monto = Money.deUnidades(200),
                        tipo = TipoDeTransaccion.GASTO,
                        cuentaOrigenId = CuentaId("banco"),
                    ),
                )

                esperarHasta { it.total == Money.deUnidades(800) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `crear abre el editor vacio`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado().editor.shouldBeNull()

                vm.alEvento(EventoDeCuentas.CrearCuenta)

                val editor = esperarHasta { it.editor != null }.editor.shouldNotBeNull()
                editor.esNueva shouldBe true
                editor.nombre shouldBe ""
                editor.puedeGuardar shouldBe false
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `guardar una cuenta nueva la deja en la lista y cierra el editor`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeCuentas.CambiarNombre("Cartera"))
                vm.alEvento(EventoDeCuentas.CambiarTipo(TipoDeCuenta.EFECTIVO))
                vm.alEvento(EventoDeCuentas.CambiarSaldoInicial(Money.deUnidades(120)))
                vm.alEvento(EventoDeCuentas.Guardar)

                val estado = esperarHasta { it.cuentas.isNotEmpty() }
                estado.editor.shouldBeNull()
                val guardada = estado.cuentas.single().cuenta
                guardada.nombre shouldBe "Cartera"
                guardada.tipo shouldBe TipoDeCuenta.EFECTIVO
                guardada.saldoInicial shouldBe Money.deUnidades(120)
                estado.total shouldBe Money.deUnidades(120)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La unica regla que impone el dominio: una cuenta sin nombre no existe.
    // El editor la deja escribir pero no guardarla, en vez de reventar.
    @Test
    fun `una cuenta sin nombre no se guarda`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeCuentas.CambiarNombre("   "))
                vm.alEvento(EventoDeCuentas.Guardar)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            cuentas.observarTodas().test {
                awaitItem() shouldBe emptyList()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editar una cuenta carga sus valores y los guarda cambiados`() =
        runTest {
            cuentas.guardar(cuenta("banco", nombre = "Banco", saldoInicial = 1000))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                vm.alEvento(EventoDeCuentas.EditarCuenta(inicial.cuentas.single().cuenta))

                val editor = esperarHasta { it.editor != null }.editor.shouldNotBeNull()
                editor.esNueva shouldBe false
                editor.nombre shouldBe "Banco"
                editor.saldoInicial shouldBe Money.deUnidades(1000)

                vm.alEvento(EventoDeCuentas.CambiarNombre("Banco principal"))
                vm.alEvento(EventoDeCuentas.Guardar)

                val estado =
                    esperarHasta {
                        it.cuentas
                            .single()
                            .cuenta.nombre == "Banco principal"
                    }
                estado.cuentas.size shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editar no crea una cuenta nueva`() =
        runTest {
            cuentas.guardar(cuenta("banco", nombre = "Banco"))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                vm.alEvento(EventoDeCuentas.EditarCuenta(inicial.cuentas.single().cuenta))
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("Otro nombre"))
                vm.alEvento(EventoDeCuentas.Guardar)

                esperarHasta { it.editor == null }
                cancelAndIgnoreRemainingEvents()
            }

            cuentas.obtener(CuentaId("banco"))!!.nombre shouldBe "Otro nombre"
        }

    @Test
    fun `el nombre se guarda sin espacios de sobra`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("  Cartera  "))
                vm.alEvento(EventoDeCuentas.Guardar)

                esperarHasta { it.cuentas.isNotEmpty() }
                    .cuentas
                    .single()
                    .cuenta.nombre shouldBe "Cartera"
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Archivar no es borrar: la cuenta sigue en la lista pero deja de sumar.
    @Test
    fun `archivar deja la cuenta en la lista y la saca del total`() =
        runTest {
            cuentas.guardar(cuenta("vieja", saldoInicial = 300))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                inicial.total shouldBe Money.deUnidades(300)

                vm.alEvento(EventoDeCuentas.EditarCuenta(inicial.cuentas.single().cuenta))
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarArchivada(true))
                vm.alEvento(EventoDeCuentas.Guardar)

                val estado = esperarHasta { it.total == Money.ZERO }
                estado.cuentas.size shouldBe 1
                estado.cuentas
                    .single()
                    .cuenta.archivada shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `eliminar quita la cuenta de la lista`() =
        runTest {
            cuentas.guardar(cuenta("banco", saldoInicial = 100))
            val vm = viewModel()

            vm.uiState.test {
                val inicial = esperarCargado()
                vm.alEvento(EventoDeCuentas.EditarCuenta(inicial.cuentas.single().cuenta))
                esperarHasta { it.editor != null }

                vm.alEvento(EventoDeCuentas.Eliminar)

                val estado = esperarHasta { it.estaVacio }
                estado.editor.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelar cierra el editor sin guardar nada`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("Cartera"))

                vm.alEvento(EventoDeCuentas.CerrarEditor)

                esperarHasta { it.editor == null }.estaVacio shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }

    // La moneda de una cuenta nueva es la de los ajustes; no se pregunta en el
    // alta para no meter una decision mas en el camino de todos los dias.
    @Test
    fun `una cuenta nueva se crea en la moneda de los ajustes`() =
        runTest {
            ajustes.guardar(
                com.miplata.core.domain.model
                    .Ajustes(moneda = Moneda("EUR")),
            )
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("Cartera"))
                vm.alEvento(EventoDeCuentas.Guardar)

                esperarHasta { it.cuentas.isNotEmpty() }
                    .cuentas
                    .single()
                    .cuenta.moneda shouldBe Moneda("EUR")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un sobre con porcentaje se guarda con su reparto`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("Libertad financiera"))
                vm.alEvento(EventoDeCuentas.CambiarRol(PapelDeLaCuenta.SOBRE))
                vm.alEvento(EventoDeCuentas.CambiarIntocable(true))
                vm.alEvento(EventoDeCuentas.Guardar)

                esperarHasta { it.cuentas.isNotEmpty() }
                    .cuentas
                    .single()
                    .cuenta.rol shouldBe RolDeCuenta.Sobre(Reparto.Porcentaje(10), intocable = true)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un sobre con monto fijo guarda el monto y no el porcentaje`() =
        runTest {
            val vm = viewModel()

            vm.uiState.test {
                esperarCargado()
                vm.alEvento(EventoDeCuentas.CrearCuenta)
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarNombre("Entrenamiento"))
                vm.alEvento(EventoDeCuentas.CambiarRol(PapelDeLaCuenta.SOBRE))
                vm.alEvento(EventoDeCuentas.CambiarModoDeReparto(enPorcentaje = false))

                // Sin monto todavia no se puede guardar.
                esperarHasta { it.editor?.repartoEnPorcentaje == false }.editor!!.puedeGuardar shouldBe false

                vm.alEvento(EventoDeCuentas.CambiarMontoFijo(Money.deUnidades(50)))
                vm.alEvento(EventoDeCuentas.Guardar)

                esperarHasta { it.cuentas.isNotEmpty() }
                    .cuentas
                    .single()
                    .cuenta.rol shouldBe RolDeCuenta.Sobre(Reparto.Monto(Money.deUnidades(50)))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `un porcentaje fuera de rango no deja guardar`() {
        val sobre = EditorDeCuenta(nombre = "Ahorros", rol = PapelDeLaCuenta.SOBRE)

        sobre.puedeGuardar shouldBe true
        sobre.copy(porcentaje = 0).puedeGuardar shouldBe false
        sobre.copy(porcentaje = 101).puedeGuardar shouldBe false
        sobre.copy(porcentaje = null).puedeGuardar shouldBe false
    }

    @Test
    fun `elegir otra principal deja a la anterior aparte`() =
        runTest {
            cuentas.guardar(cuenta("normal").copy(rol = RolDeCuenta.Principal))
            cuentas.guardar(cuenta("otra"))
            val vm = viewModel()

            vm.uiState.test {
                val otra = esperarCargado().cuentas.first { it.cuenta.id.valor == "otra" }.cuenta
                vm.alEvento(EventoDeCuentas.EditarCuenta(otra))
                esperarHasta { it.editor != null }
                vm.alEvento(EventoDeCuentas.CambiarRol(PapelDeLaCuenta.PRINCIPAL))
                vm.alEvento(EventoDeCuentas.Guardar)

                val estado =
                    esperarHasta { e -> e.cuentas.any { it.cuenta.id.valor == "otra" && it.cuenta.esPrincipal } }
                estado.cuentas
                    .single { it.cuenta.id.valor == "normal" }
                    .cuenta.rol shouldBe RolDeCuenta.Independiente
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private suspend fun app.cash.turbine.TurbineTestContext<CuentasUiState>.esperarCargado(): CuentasUiState =
    esperarHasta { !it.cargando }

private suspend fun app.cash.turbine.TurbineTestContext<CuentasUiState>.esperarHasta(
    condicion: (CuentasUiState) -> Boolean,
): CuentasUiState {
    while (true) {
        val estado = awaitItem()
        if (condicion(estado)) return estado
    }
}
