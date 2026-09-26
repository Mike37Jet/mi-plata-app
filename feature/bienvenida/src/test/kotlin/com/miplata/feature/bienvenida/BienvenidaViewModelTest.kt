package com.miplata.feature.bienvenida

import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.usecase.CompletarPrimerosPasosUseCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

private val MARZO = Mes.de(2026, 3)

@OptIn(ExperimentalCoroutinesApi::class)
class BienvenidaViewModelTest {
    private val ajustes = FakeAjustesRepository()
    private val cuentas = FakeCuentaRepository()
    private val planes = FakePlanRepository()

    @Before
    fun fijarDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun soltarDispatcher() = Dispatchers.resetMain()

    private fun viewModel(repositorioDeCuentas: CuentaRepository = cuentas) =
        BienvenidaViewModel(
            CompletarPrimerosPasosUseCase(
                ajustes,
                repositorioDeCuentas,
                planes,
                GeneradorDeIdsSecuencial(),
                object : Calendario {
                    override fun hoy() = LocalDate(2026, 3, 10)
                },
            ),
        )

    private fun BienvenidaViewModel.hastaLaCuenta() {
        alEvento(EventoDeBienvenida.Siguiente)
        alEvento(EventoDeBienvenida.Siguiente)
    }

    private fun BienvenidaViewModel.rellenarLaCuenta() {
        hastaLaCuenta()
        alEvento(EventoDeBienvenida.CambiarNombreDeLaCuenta("Cartera"))
        alEvento(EventoDeBienvenida.ElegirTipoDeCuenta(TipoDeCuenta.EFECTIVO))
        alEvento(EventoDeBienvenida.CambiarSaldo(Money.deUnidades(80)))
        alEvento(EventoDeBienvenida.Siguiente)
    }

    @Test
    fun `los pasos van en orden y atras deshace`() {
        val vm = viewModel()

        vm.uiState.value.paso shouldBe PasoDeBienvenida.BIENVENIDA
        vm.alEvento(EventoDeBienvenida.Siguiente)
        vm.uiState.value.paso shouldBe PasoDeBienvenida.TU_MES
        vm.alEvento(EventoDeBienvenida.Atras)
        vm.uiState.value.paso shouldBe PasoDeBienvenida.BIENVENIDA
    }

    @Test
    fun `sin nombre de cuenta no se puede seguir`() {
        val vm = viewModel()
        vm.hastaLaCuenta()

        vm.uiState.value.puedeSeguir shouldBe false
        vm.alEvento(EventoDeBienvenida.Siguiente)
        vm.uiState.value.paso shouldBe PasoDeBienvenida.PRIMERA_CUENTA

        // Solo espacios tampoco es un nombre.
        vm.alEvento(EventoDeBienvenida.CambiarNombreDeLaCuenta("   "))
        vm.uiState.value.puedeSeguir shouldBe false
    }

    @Test
    fun `el dia del mes no se sale de 1 a 31`() {
        val vm = viewModel()

        vm.alEvento(EventoDeBienvenida.CambiarPrimerDia(0))
        vm.uiState.value.primerDiaDelMes shouldBe 1
        vm.alEvento(EventoDeBienvenida.CambiarPrimerDia(32))
        vm.uiState.value.primerDiaDelMes shouldBe 31
    }

    @Test
    fun `nada se guarda hasta el final`() =
        runTest {
            val vm = viewModel()
            vm.rellenarLaCuenta()
            vm.alEvento(EventoDeBienvenida.CambiarIngreso(Money.deUnidades(1000)))

            cuentas.observarTodas().first().shouldBeEmpty()
            ajustes.obtener().moneda shouldBe Moneda("USD")
        }

    @Test
    fun `al terminar se guarda lo elegido`() =
        runTest {
            val vm = viewModel()
            vm.alEvento(EventoDeBienvenida.ElegirMoneda(Moneda("EUR")))
            vm.alEvento(EventoDeBienvenida.CambiarPrimerDia(25))
            vm.rellenarLaCuenta()
            vm.alEvento(EventoDeBienvenida.CambiarIngreso(Money.deUnidades(1000)))

            vm.alEvento(EventoDeBienvenida.Terminar(conIngreso = true, nombreDelIngreso = "Sueldo"))

            ajustes.obtener().moneda shouldBe Moneda("EUR")
            ajustes.obtener().primerDiaDelMesFinanciero shouldBe 25
            val cuenta = cuentas.observarTodas().first().single()
            cuenta.nombre shouldBe "Cartera"
            cuenta.tipo shouldBe TipoDeCuenta.EFECTIVO
            cuenta.saldoInicial shouldBe Money.deUnidades(80)
            val ingreso =
                planes
                    .obtenerDe(MARZO)
                    .shouldNotBeNull()
                    .lineas
                    .single()
            ingreso.nombre shouldBe "Sueldo"
            ingreso.montoPlanificado shouldBe Money.deUnidades(1000)
        }

    /** "Lo hare despues" descarta lo tecleado: no es un ingreso a medio escribir. */
    @Test
    fun `dejar el ingreso para despues no crea el plan aunque se hubiera escrito`() =
        runTest {
            val vm = viewModel()
            vm.rellenarLaCuenta()
            vm.alEvento(EventoDeBienvenida.CambiarIngreso(Money.deUnidades(1000)))

            vm.alEvento(EventoDeBienvenida.Terminar(conIngreso = false, nombreDelIngreso = "Sueldo"))

            cuentas.observarTodas().first().size shouldBe 1
            planes.obtenerDe(MARZO).shouldBeNull()
        }

    @Test
    fun `si no se puede guardar, se avisa y se puede reintentar`() =
        runTest {
            var fallar = true
            val caprichosa =
                object : CuentaRepository by cuentas {
                    override suspend fun guardar(cuenta: com.miplata.core.domain.model.Cuenta) {
                        if (fallar) throw IOException("disco lleno")
                        cuentas.guardar(cuenta)
                    }
                }
            val vm = viewModel(caprichosa)
            vm.rellenarLaCuenta()

            vm.alEvento(EventoDeBienvenida.Terminar(conIngreso = false, nombreDelIngreso = "Sueldo"))

            vm.uiState.value.errorAlGuardar shouldBe "disco lleno"
            vm.uiState.value.guardando shouldBe false
            // Lo escrito sigue ahi.
            vm.uiState.value.nombreDeLaCuenta shouldBe "Cartera"

            fallar = false
            vm.alEvento(EventoDeBienvenida.Terminar(conIngreso = false, nombreDelIngreso = "Sueldo"))

            vm.uiState.value.errorAlGuardar
                .shouldBeNull()
            cuentas.observarTodas().first().size shouldBe 1
        }

    @Test
    fun `la moneda del telefono va primero y es la elegida`() {
        val sugeridas = monedasSugeridas(Locale.forLanguageTag("es-CO"))

        sugeridas.first() shouldBe Moneda("COP")
        BienvenidaViewModel.estadoInicial(Locale.forLanguageTag("es-CO")).moneda shouldBe Moneda("COP")
    }

    @Test
    fun `una moneda que no esta en la lista de la region se añade`() {
        val sugeridas = monedasSugeridas(Locale.forLanguageTag("ja-JP"))

        sugeridas.first() shouldBe Moneda("JPY")
        sugeridas.count { it == Moneda("USD") } shouldBe 1
    }

    /** Un idioma sin pais no tiene moneda: se empieza por el dolar. */
    @Test
    fun `sin pais se empieza por el dolar`() {
        monedasSugeridas(Locale.forLanguageTag("es")).first() shouldBe Moneda("USD")
    }

    @Test
    fun `un campo vacio es cero y un texto que no es importe se ignora`() {
        importeDe("", { null }) shouldBe Money.ZERO
        importeDe("abc", { null }).shouldBeNull()
    }
}
