package com.miplata.core.domain.usecase

import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIdsSecuencial
import com.miplata.core.domain.model.CierreDeMes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.model.SaldoDeCierre
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import com.miplata.core.domain.repository.FakeCierreRepository
import com.miplata.core.domain.repository.FakeTransaccionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
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
private val PERIODO = PeriodoMensual(MARZO)
private val MEDIADOS = LocalDate(2026, 3, 15)

private fun dinero(texto: String) = Money.deDecimal(texto.toBigDecimal())

private fun cuenta(
    id: String,
    rol: RolDeCuenta = RolDeCuenta.Independiente,
    saldoInicial: String = "0",
) = Cuenta(
    id = CuentaId(id),
    nombre = id,
    tipo = TipoDeCuenta.BANCARIA,
    saldoInicial = dinero(saldoInicial),
    moneda = Moneda("USD"),
    rol = rol,
)

private fun sobre(
    id: String,
    reparto: Reparto = Reparto.PorDefecto,
    intocable: Boolean = false,
) = cuenta(id, RolDeCuenta.Sobre(reparto, intocable))

private val NORMAL = cuenta("normal", RolDeCuenta.Principal)
private val LIBERTAD = sobre("libertad", intocable = true)
private val AHORROS = sobre("ahorros")
private val DIVERSION = sobre("diversion")
private val ENTRENAMIENTO = sobre("entrenamiento")
private val CUENTAS = listOf(NORMAL, LIBERTAD, AHORROS, DIVERSION, ENTRENAMIENTO)

private var siguienteLinea = 0

private fun linea(
    monto: String,
    cuenta: Cuenta? = NORMAL,
    tipo: TipoDeLinea = TipoDeLinea.GASTO_FIJO,
    activa: Boolean = true,
) = LineaDePlan(
    id = LineaId("l-${++siguienteLinea}"),
    nombre = "",
    tipo = tipo,
    montoPlanificado = dinero(monto),
    cuentaId = cuenta?.id,
    activa = activa,
)

/** El plan de la hoja de calculo con la que empezo todo esto (ADR 0007). */
private val PLAN_DE_LA_HOJA =
    PlanMensual(
        id = PlanId("marzo"),
        mes = MARZO,
        lineas =
            listOf(
                linea("633", tipo = TipoDeLinea.INGRESO),
                // Normal: arriendo, luz, agua, internet, comida, deudas.
                linea("175"),
                linea("13"),
                linea("13"),
                linea("13"),
                linea("100", tipo = TipoDeLinea.GASTO_VARIABLE),
                linea("65"),
                // Ahorros: pasajes, Spotify, megas, corte de cabello.
                linea("30", AHORROS, TipoDeLinea.GASTO_VARIABLE),
                linea("3.30", AHORROS),
                linea("11", AHORROS),
                linea("12", AHORROS, TipoDeLinea.GASTO_VARIABLE),
                // Entrenamiento: la suscripcion a Claude.
                linea("20", ENTRENAMIENTO),
            ),
    )

private fun movimiento(
    monto: String,
    tipo: TipoDeTransaccion,
    cuenta: Cuenta,
    fecha: LocalDate = MEDIADOS,
    destino: Cuenta? = null,
) = Transaccion(
    id = TransaccionId("t-$monto-${cuenta.id}-$fecha"),
    fecha = fecha,
    monto = dinero(monto),
    tipo = tipo,
    cuentaOrigenId = cuenta.id,
    cuentaDestinoId = destino?.id,
)

private fun planPorCuentas(
    cuentas: List<Cuenta> = CUENTAS,
    plan: PlanMensual? = PLAN_DE_LA_HOJA,
    transacciones: List<Transaccion> = emptyList(),
    hoy: LocalDate = MEDIADOS,
) = CalcularPlanPorCuentasUseCase()(PERIODO, cuentas, plan, transacciones, hoy)

@DisplayName("Presupuesto por cuentas")
class PresupuestoPorCuentasTest {
    @Nested
    @DisplayName("el reparto")
    inner class ElReparto {
        @Test
        fun `por porcentaje es esa parte del ingreso`() {
            Reparto.Porcentaje(10).de(dinero("633")) shouldBe dinero("63.30")
        }

        @Test
        fun `redondea al centavo`() {
            // 3% de 10,15 son 0,3045: se redondea a 0,30.
            Reparto.Porcentaje(3).de(dinero("10.15")) shouldBe dinero("0.30")
        }

        @Test
        fun `por monto es siempre el mismo`() {
            Reparto.Monto(dinero("50")).de(dinero("633")) shouldBe dinero("50")
            Reparto.Monto(dinero("50")).de(Money.ZERO) shouldBe dinero("50")
        }

        @Test
        fun `sin ingreso un porcentaje es cero`() {
            Reparto.Porcentaje(10).de(Money.ZERO) shouldBe Money.ZERO
        }

        @Test
        fun `no admite porcentajes imposibles ni montos vacios`() {
            shouldThrow<IllegalArgumentException> { Reparto.Porcentaje(0) }
            shouldThrow<IllegalArgumentException> { Reparto.Porcentaje(101) }
            shouldThrow<IllegalArgumentException> { Reparto.Monto(Money.ZERO) }
        }
    }

    @Nested
    @DisplayName("el plan por cuentas")
    inner class ElPlan {
        @Test
        fun `sin cuenta principal no hay plan por cuentas`() {
            planPorCuentas(cuentas = listOf(AHORROS, cuenta("cartera"))).shouldBeNull()
        }

        @Test
        fun `cuadra con la hoja de calculo`() {
            val plan = planPorCuentas().shouldNotBeNull()

            plan.ingresoPlaneado shouldBe dinero("633")
            plan.principal.reparte shouldBe dinero("253.20")
            plan.principal.gastos shouldBe dinero("379")
            plan.sinRepartir shouldBe dinero("0.80")

            plan.de(AHORROS.id)!!.terminaCon shouldBe dinero("7.00")
            plan.de(ENTRENAMIENTO.id)!!.terminaCon shouldBe dinero("43.30")
            plan.de(DIVERSION.id)!!.terminaCon shouldBe dinero("63.30")
            plan.de(LIBERTAD.id)!!.terminaCon shouldBe dinero("63.30")
        }

        @Test
        fun `ordena la principal primero y despues los sobres`() {
            planPorCuentas().shouldNotBeNull().cuentas.map { it.cuenta.id } shouldContainExactly
                listOf(NORMAL.id, LIBERTAD.id, AHORROS.id, DIVERSION.id, ENTRENAMIENTO.id)
        }

        @Test
        fun `un sobre con monto fijo recibe ese monto`() {
            val fijo = sobre("fijo", Reparto.Monto(dinero("50")))

            val plan = planPorCuentas(cuentas = listOf(NORMAL, fijo)).shouldNotBeNull()

            plan.de(fijo.id)!!.recibe shouldBe dinero("50")
            plan.principal.reparte shouldBe dinero("50")
        }

        @Test
        fun `empieza con lo que quedo el mes anterior`() {
            val antes = LocalDate(2026, 2, 20)
            val movimientos =
                listOf(
                    movimiento("7", TipoDeTransaccion.INGRESO, DIVERSION, antes),
                    // Lo de este mes no cuenta para el arranque.
                    movimiento("5", TipoDeTransaccion.GASTO, DIVERSION),
                )

            val diversion = planPorCuentas(transacciones = movimientos).shouldNotBeNull().de(DIVERSION.id)!!

            diversion.empiezaCon shouldBe dinero("7")
            diversion.terminaCon shouldBe dinero("70.30")
            diversion.saldoActual shouldBe dinero("2")
        }

        @Test
        fun `una linea sin cuenta sale de la principal`() {
            val plan = PLAN_DE_LA_HOJA.copy(lineas = PLAN_DE_LA_HOJA.lineas + linea("10", cuenta = null))

            planPorCuentas(plan = plan).shouldNotBeNull().principal.gastos shouldBe dinero("389")
        }

        @Test
        fun `una linea de una cuenta archivada sale de la principal`() {
            val vieja = cuenta("vieja").copy(archivada = true)
            val plan = PLAN_DE_LA_HOJA.copy(lineas = PLAN_DE_LA_HOJA.lineas + linea("10", vieja))

            val porCuentas = planPorCuentas(cuentas = CUENTAS + vieja, plan = plan).shouldNotBeNull()

            porCuentas.principal.gastos shouldBe dinero("389")
            porCuentas.de(vieja.id).shouldBeNull()
        }

        @Test
        fun `las lineas desactivadas se muestran pero no suman`() {
            val apagada = linea("40", DIVERSION, activa = false)
            val plan = PLAN_DE_LA_HOJA.copy(lineas = PLAN_DE_LA_HOJA.lineas + apagada)

            val diversion = planPorCuentas(plan = plan).shouldNotBeNull().de(DIVERSION.id)!!

            diversion.lineas shouldContainExactly listOf(apagada)
            diversion.gastos shouldBe Money.ZERO
        }

        @Test
        fun `una cuenta independiente solo aparece si tiene lineas`() {
            val cartera = cuenta("cartera")
            val sinLineas = planPorCuentas(cuentas = CUENTAS + cartera).shouldNotBeNull()
            sinLineas.de(cartera.id).shouldBeNull()

            val plan = PLAN_DE_LA_HOJA.copy(lineas = PLAN_DE_LA_HOJA.lineas + linea("15", cartera))
            val conLineas = planPorCuentas(cuentas = CUENTAS + cartera, plan = plan).shouldNotBeNull()
            conLineas.de(cartera.id)!!.terminaCon shouldBe dinero("-15")
        }

        @Test
        fun `avisa si el plan deja una cuenta en negativo`() {
            val plan = PLAN_DE_LA_HOJA.copy(lineas = PLAN_DE_LA_HOJA.lineas + linea("80", DIVERSION))

            planPorCuentas(plan = plan).shouldNotBeNull().de(DIVERSION.id)!!.quedaCorta shouldBe true
        }
    }

    @Nested
    @DisplayName("el cierre de mes")
    inner class ElCierre {
        private val fin = PERIODO.fin

        private fun cerrar(
            reales: Map<CuentaId, String>,
            coberturas: Set<CuentaId> = emptySet(),
            transacciones: List<Transaccion> = emptyList(),
        ) = CalcularCierreDeMesUseCase(GeneradorDeIdsSecuencial())(
            periodo = PERIODO,
            plan = planPorCuentas(transacciones = transacciones).shouldNotBeNull(),
            transacciones = transacciones,
            banco = SaldosDelBanco(reales.mapValues { dinero(it.value) }, coberturas),
            fecha = fin,
        )

        @Test
        fun `guarda lo esperado y lo real de cada cuenta`() {
            val resultado = cerrar(mapOf(DIVERSION.id to "20", AHORROS.id to "7"))

            resultado.cierre.saldoDe(DIVERSION.id) shouldBe SaldoDeCierre(DIVERSION.id, dinero("63.30"), dinero("20"))
            resultado.cierre.saldoDe(DIVERSION.id)!!.diferencia shouldBe dinero("-43.30")
            resultado.cierre.saldoDe(AHORROS.id)!!.diferencia shouldBe Money.ZERO
            resultado.cierre.saldoDe(NORMAL.id).shouldBeNull()
        }

        @Test
        fun `da por hecho el reparto que no se anoto`() {
            val reparto = cerrar(mapOf(DIVERSION.id to "63.30")).movimientos.single()

            reparto.tipo shouldBe TipoDeTransaccion.TRANSFERENCIA
            reparto.cuentaOrigenId shouldBe NORMAL.id
            reparto.cuentaDestinoId shouldBe DIVERSION.id
            reparto.monto shouldBe dinero("63.30")
            reparto.ajusteDeCierre shouldBe MARZO
            reparto.esSinDetalle shouldBe false
        }

        @Test
        fun `lo que no se anoto queda como ajuste Sin detalle`() {
            // Con el reparto, la app cree que Diversion tiene 63,30 y el banco dice 20.
            val ajuste = cerrar(mapOf(DIVERSION.id to "20")).movimientos.single { it.esSinDetalle }

            ajuste.tipo shouldBe TipoDeTransaccion.GASTO
            ajuste.monto shouldBe dinero("43.30")
            ajuste.cuentaOrigenId shouldBe DIVERSION.id
            ajuste.ajusteDeCierre shouldBe MARZO
            ajuste.fecha shouldBe fin
        }

        @Test
        fun `un saldo mayor de lo calculado es un ingreso Sin detalle`() {
            val ajuste = cerrar(mapOf(DIVERSION.id to "70")).movimientos.single { it.esSinDetalle }

            ajuste.tipo shouldBe TipoDeTransaccion.INGRESO
            ajuste.monto shouldBe dinero("6.70")
        }

        @Test
        fun `el ajuste es la diferencia con lo anotado, no con el plan`() {
            val anotado =
                listOf(
                    movimiento("63.30", TipoDeTransaccion.TRANSFERENCIA, NORMAL, destino = DIVERSION),
                    movimiento("30", TipoDeTransaccion.GASTO, DIVERSION),
                )

            val ajuste = cerrar(mapOf(DIVERSION.id to "20"), transacciones = anotado).movimientos.single()

            // La app calcula 33,30; el banco dice 20: se escaparon 13,30.
            ajuste.tipo shouldBe TipoDeTransaccion.GASTO
            ajuste.monto shouldBe dinero("13.30")
        }

        @Test
        fun `si todo cuadra no crea movimientos`() {
            val anotado = listOf(movimiento("63.30", TipoDeTransaccion.TRANSFERENCIA, NORMAL, destino = DIVERSION))

            cerrar(mapOf(DIVERSION.id to "63.30", NORMAL.id to "-63.30"), transacciones = anotado)
                .movimientos
                .shouldBeEmpty()
        }

        @Test
        fun `una cobertura pasa el gasto de mas del sobre a la principal`() {
            // Diversion termino 30 por debajo de lo esperado porque cubrio Normal.
            val resultado =
                cerrar(
                    reales = mapOf(DIVERSION.id to "33.30", NORMAL.id to "0"),
                    coberturas = setOf(DIVERSION.id),
                )

            val cobertura = resultado.movimientos.single { it.cuentaDestinoId == NORMAL.id }
            cobertura.tipo shouldBe TipoDeTransaccion.TRANSFERENCIA
            cobertura.cuentaOrigenId shouldBe DIVERSION.id
            cobertura.monto shouldBe dinero("30")

            // Diversion ya cuadra con la cobertura: el ajuste es solo de Normal.
            resultado.movimientos.filter { it.esSinDetalle }.map { it.cuentaOrigenId } shouldContainExactly
                listOf(NORMAL.id)

            val despues = saldosAl(fin, CUENTAS, resultado.movimientos)
            despues[DIVERSION.id] shouldBe dinero("33.30")
            despues[NORMAL.id] shouldBe Money.ZERO
        }

        @Test
        fun `no hay cobertura si el sobre no bajo`() {
            val resultado = cerrar(mapOf(DIVERSION.id to "70"), coberturas = setOf(DIVERSION.id))

            resultado.movimientos.none { it.cuentaDestinoId == NORMAL.id } shouldBe true
        }
    }

    @Nested
    @DisplayName("el reparto pendiente")
    inner class ElRepartoPendiente {
        private val transacciones = FakeTransaccionRepository()
        private val registrar =
            RegistrarRepartoUseCase(
                transacciones,
                GeneradorDeIdsSecuencial(),
                object : Calendario {
                    override fun hoy() = LocalDate(2026, 3, 2)
                },
            )

        @Test
        fun `es lo que le toca a cada sobre menos lo ya transferido este mes`() {
            val anotado =
                listOf(
                    movimiento("20", TipoDeTransaccion.TRANSFERENCIA, NORMAL, destino = DIVERSION),
                    // Del mes pasado: no cuenta.
                    movimiento("63.30", TipoDeTransaccion.TRANSFERENCIA, NORMAL, LocalDate(2026, 2, 3), AHORROS),
                    movimiento("63.30", TipoDeTransaccion.TRANSFERENCIA, NORMAL, destino = LIBERTAD),
                )

            val pendiente = repartoPendiente(PERIODO, planPorCuentas().shouldNotBeNull(), anotado)

            pendiente shouldBe
                mapOf(
                    AHORROS.id to dinero("63.30"),
                    DIVERSION.id to dinero("43.30"),
                    ENTRENAMIENTO.id to dinero("63.30"),
                )
        }

        @Test
        fun `registrarlo anota las transferencias de hoy y no se repite`() =
            runTest {
                val plan = planPorCuentas().shouldNotBeNull()

                registrar(PERIODO, plan)
                registrar(PERIODO, plan)

                val anotadas = transacciones.observarTodas().first()
                anotadas shouldHaveSize 4
                anotadas.all {
                    it.esTransferencia &&
                        it.fecha ==
                        LocalDate(
                            2026,
                            3,
                            2,
                        ) &&
                        !it.esAjusteDeCierre
                } shouldBe
                    true
                repartoPendiente(PERIODO, plan, anotadas) shouldBe emptyMap()
            }
    }

    @Nested
    @DisplayName("cerrar y reabrir")
    inner class CerrarYReabrir {
        private val transacciones = FakeTransaccionRepository()
        private val cierres = FakeCierreRepository()
        private val calendario =
            object : Calendario {
                override fun hoy() = LocalDate(2026, 4, 2)
            }
        private val cerrar =
            CerrarMesUseCase(transacciones, cierres, CalcularCierreDeMesUseCase(GeneradorDeIdsSecuencial()), calendario)
        private val reabrir = ReabrirMesUseCase(transacciones, cierres)

        @Test
        fun `cerrar guarda el cierre y los ajustes, con fecha del ultimo dia si el mes paso`() =
            runTest {
                cerrar(PERIODO, planPorCuentas().shouldNotBeNull(), SaldosDelBanco(mapOf(DIVERSION.id to dinero("20"))))

                cierres.obtenerDe(MARZO).shouldNotBeNull()
                val movimientos = transacciones.observarTodas().first()
                movimientos shouldHaveSize 2
                movimientos.all { it.fecha == PERIODO.fin && it.ajusteDeCierre == MARZO } shouldBe true
            }

        @Test
        fun `cerrar dentro del mes anota con fecha de hoy`() {
            CerrarMesUseCase.fechaDelCierre(PERIODO, MEDIADOS) shouldBe MEDIADOS
            CerrarMesUseCase.fechaDelCierre(PERIODO, LocalDate(2026, 2, 1)) shouldBe PERIODO.inicio
        }

        @Test
        fun `reabrir borra el cierre, sus ajustes y los de los meses siguientes`() =
            runTest {
                val anotado = movimiento("5", TipoDeTransaccion.GASTO, DIVERSION)
                transacciones.guardar(anotado)
                val abril = MARZO.siguiente()
                val ajusteDeAbril =
                    movimiento(
                        "9",
                        TipoDeTransaccion.GASTO,
                        DIVERSION,
                        LocalDate(2026, 4, 30),
                    ).copy(ajusteDeCierre = abril)
                transacciones.guardar(ajusteDeAbril)
                cierres.guardar(CierreDeMes(abril, emptyList()))
                val febrero = CierreDeMes(MARZO.anterior(), emptyList())
                cierres.guardar(febrero)

                cerrar(PERIODO, planPorCuentas().shouldNotBeNull(), SaldosDelBanco(mapOf(DIVERSION.id to dinero("20"))))
                transacciones.observarTodas().first() shouldHaveSize 4

                reabrir(MARZO)

                transacciones.observarTodas().first() shouldContainExactly listOf(anotado)
                cierres.observarTodos().first() shouldContainExactly listOf(febrero)
            }
    }
}
