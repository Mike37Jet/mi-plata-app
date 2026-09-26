package com.miplata.core.domain.usecase

import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.FakeAjustesRepository
import com.miplata.core.domain.repository.FakeCuentaRepository
import com.miplata.core.domain.repository.FakePlanRepository
import com.miplata.core.domain.repository.PlanRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private val MARZO = Mes.de(2026, 3)

private val CALENDARIO =
    object : Calendario {
        override fun hoy() = LocalDate(2026, 3, 10)
    }

private fun pasos(ingresoMensual: Money? = Money.deUnidades(1500)) =
    PrimerosPasos(
        moneda = Moneda("EUR"),
        primerDiaDelMes = 25,
        nombreDeLaCuenta = "Cuenta nómina",
        tipoDeCuenta = TipoDeCuenta.BANCARIA,
        saldoActual = Money.deUnidades(320),
        ingresoMensual = ingresoMensual,
        nombreDelIngreso = "Sueldo",
    )

@DisplayName("Primeros pasos")
class PrimerosPasosUseCaseTest {
    private val ajustes = FakeAjustesRepository()
    private val cuentas = FakeCuentaRepository()
    private val planes = FakePlanRepository()

    private fun completar(repositorioDePlanes: PlanRepository = planes) =
        CompletarPrimerosPasosUseCase(ajustes, cuentas, repositorioDePlanes, GeneradorDeIdsSecuencial(), CALENDARIO)

    @Nested
    @DisplayName("hay que dar la bienvenida")
    inner class HayQueDarLaBienvenida {
        private val hayQue = HayQueDarLaBienvenidaUseCase(cuentas)

        @Test
        fun `mientras no hay ninguna cuenta`() =
            runTest {
                hayQue().first() shouldBe true
            }

        @Test
        fun `ya no en cuanto existe una`() =
            runTest {
                cuentas.guardar(Cuenta(CuentaId("c"), "Cartera", TipoDeCuenta.EFECTIVO, Money.ZERO, Moneda("USD")))

                hayQue().first() shouldBe false
            }

        /** Una cuenta archivada sigue teniendo historial: no es una app vacia. */
        @Test
        fun `tampoco si la unica cuenta esta archivada`() =
            runTest {
                cuentas.guardar(
                    Cuenta(CuentaId("c"), "Vieja", TipoDeCuenta.EFECTIVO, Money.ZERO, Moneda("USD"), archivada = true),
                )

                hayQue().first() shouldBe false
            }
    }

    @Nested
    @DisplayName("al completarlos")
    inner class AlCompletar {
        @Test
        fun `guarda la moneda y el dia en que empieza el mes`() =
            runTest {
                completar()(pasos())

                val guardados = ajustes.obtener()
                guardados.moneda shouldBe Moneda("EUR")
                guardados.primerDiaDelMesFinanciero shouldBe 25
            }

        @Test
        fun `no toca el resto de ajustes`() =
            runTest {
                ajustes.guardar(Ajustes(tema = Tema.OSCURO, ultimoBackupEnMillis = 42))

                completar()(pasos())

                ajustes.obtener().tema shouldBe Tema.OSCURO
                ajustes.obtener().ultimoBackupEnMillis shouldBe 42
            }

        @Test
        fun `crea la cuenta en la moneda elegida`() =
            runTest {
                completar()(pasos())

                val cuenta = cuentas.observarTodas().first().single()
                cuenta.nombre shouldBe "Cuenta nómina"
                cuenta.tipo shouldBe TipoDeCuenta.BANCARIA
                cuenta.saldoInicial shouldBe Money.deUnidades(320)
                cuenta.moneda shouldBe Moneda("EUR")
            }

        @Test
        fun `el nombre de la cuenta se guarda sin espacios sobrantes`() =
            runTest {
                completar()(pasos().copy(nombreDeLaCuenta = "  Cartera  "))

                cuentas
                    .observarTodas()
                    .first()
                    .single()
                    .nombre shouldBe "Cartera"
            }

        /**
         * El ingreso entra en la cuenta que se acaba de crear y el dia en que
         * empieza el mes: quien dice que su mes empieza el 25 es porque cobra
         * el 25.
         */
        @Test
        fun `el ingreso abre el plan del mes, ligado a la cuenta y al dia de cobro`() =
            runTest {
                completar()(pasos())

                val plan = planes.obtenerDe(MARZO).shouldNotBeNull()
                val ingreso = plan.lineas.single()
                ingreso.nombre shouldBe "Sueldo"
                ingreso.tipo shouldBe TipoDeLinea.INGRESO
                ingreso.montoPlanificado shouldBe Money.deUnidades(1500)
                ingreso.cuentaId shouldBe
                    cuentas
                        .observarTodas()
                        .first()
                        .single()
                        .id
                ingreso.diaDelMes shouldBe 25
            }

        @Test
        fun `sin ingreso no se crea ningun plan`() =
            runTest {
                completar()(pasos(ingresoMensual = null))

                planes.obtenerDe(MARZO).shouldBeNull()
            }

        @Test
        fun `un ingreso de cero cuenta como no haberlo dicho`() =
            runTest {
                completar()(pasos(ingresoMensual = Money.ZERO))

                planes.obtenerDe(MARZO).shouldBeNull()
            }

        /**
         * Quien borra todas sus cuentas vuelve a ver la bienvenida, pero su plan
         * del mes sigue ahi. Los primeros pasos no pueden pisarlo.
         */
        @Test
        fun `no pisa un plan que ya existe`() =
            runTest {
                val existente =
                    PlanMensual(
                        PlanId("p"),
                        MARZO,
                        listOf(LineaDePlan(LineaId("l"), "Arriendo", TipoDeLinea.GASTO_FIJO, Money.deUnidades(450))),
                    )
                planes.guardar(existente)

                completar()(pasos())

                planes.obtenerDe(MARZO) shouldBe existente
            }

        /**
         * La linea de ingreso apunta a la cuenta nueva, y la base exige que esa
         * cuenta exista (clave foranea). Los repositorios en memoria no lo
         * comprueban; este test si. Con el plan guardado antes que la cuenta,
         * la bienvenida fallaba en el telefono y aqui todo pasaba.
         */
        @Test
        fun `cuando se guarda el plan, la cuenta a la que apunta ya existe`() =
            runTest {
                val cuentasQueFaltaban = mutableListOf<CuentaId>()
                val comoLaBase =
                    object : PlanRepository by planes {
                        override suspend fun guardar(plan: PlanMensual) {
                            plan.lineas.mapNotNull { it.cuentaId }.forEach { id ->
                                if (cuentas.obtener(id) == null) cuentasQueFaltaban += id
                            }
                            planes.guardar(plan)
                        }
                    }

                completar(comoLaBase)(pasos())

                cuentasQueFaltaban.shouldBeEmpty()
            }
    }
}
