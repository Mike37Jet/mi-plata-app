package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanId
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private val MARZO = PeriodoMensual(Mes.de(2026, 3))
private val CUENTA = CuentaId("banco")
private val OTRA_CUENTA = CuentaId("efectivo")

private fun linea(
    id: String,
    tipo: TipoDeLinea,
    monto: Long,
    activa: Boolean = true,
) = LineaDePlan(
    id = LineaId(id),
    nombre = "Linea $id",
    tipo = tipo,
    montoPlanificado = Money.deUnidades(monto),
    activa = activa,
)

private fun plan(vararg lineas: LineaDePlan) =
    PlanMensual(id = PlanId("p"), mes = Mes.de(2026, 3), lineas = lineas.toList())

private fun fecha(
    dia: Int,
    mes: Int = 3,
) = LocalDate(2026, mes, dia)

private fun movimiento(
    tipo: TipoDeTransaccion,
    monto: Long,
    fecha: LocalDate = fecha(15),
    linea: String? = null,
    destino: CuentaId? = null,
) = Transaccion(
    id = TransaccionId("t-$tipo-$monto-$fecha-${linea.orEmpty()}"),
    fecha = fecha,
    monto = Money.deUnidades(monto),
    tipo = tipo,
    cuentaOrigenId = CUENTA,
    cuentaDestinoId = destino,
    lineaDePlanId = linea?.let { LineaId(it) },
)

@DisplayName("CalcularResumenMensualUseCase")
class CalcularResumenMensualUseCaseTest {
    private val calcular = CalcularResumenMensualUseCase()

    @Nested
    @DisplayName("cuando no hay nada")
    inner class SinDatos {
        @Test
        fun `un mes sin plan y sin movimientos esta todo a cero`() {
            val resumen = calcular(MARZO, plan = null, transacciones = emptyList())

            resumen.ingresosPlanificados shouldBe Money.ZERO
            resumen.gastosPlanificados shouldBe Money.ZERO
            resumen.ingresosReales shouldBe Money.ZERO
            resumen.gastosReales shouldBe Money.ZERO
            resumen.disponiblePlanificado shouldBe Money.ZERO
            resumen.disponibleReal shouldBe Money.ZERO
            resumen.desviacionPorLinea shouldBe emptyList()
        }

        // Sin esto, calcular "cuanto llevo ejecutado" dividiria por cero.
        @Test
        fun `un mes vacio no esta en sobregiro`() {
            calcular(MARZO, null, emptyList()).enSobregiro shouldBe false
        }

        @Test
        fun `sin plan pero con movimientos, lo real si cuenta`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones = listOf(movimiento(TipoDeTransaccion.GASTO, 50)),
                )

            resumen.gastosReales shouldBe Money.deUnidades(50)
            resumen.disponibleReal shouldBe Money.deUnidades(-50)
            resumen.enSobregiro shouldBe true
        }
    }

    @Nested
    @DisplayName("que movimientos cuentan")
    inner class Filtrado {
        // Mover dinero entre cuentas propias no cambia el patrimonio. Contarlo
        // como gasto es el error que infla los informes de media industria.
        @Test
        fun `una transferencia entre cuentas propias no es ingreso ni gasto`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.TRANSFERENCIA, 500, destino = OTRA_CUENTA),
                        ),
                )

            resumen.ingresosReales shouldBe Money.ZERO
            resumen.gastosReales shouldBe Money.ZERO
            resumen.disponibleReal shouldBe Money.ZERO
        }

        @Test
        fun `un gasto de otro mes no ensucia este`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.GASTO, 100, fecha = fecha(15, mes = 3)),
                            movimiento(TipoDeTransaccion.GASTO, 999, fecha = fecha(15, mes = 4)),
                            movimiento(TipoDeTransaccion.GASTO, 888, fecha = fecha(15, mes = 2)),
                        ),
                )

            resumen.gastosReales shouldBe Money.deUnidades(100)
        }

        @Test
        fun `cuentan los movimientos del primer y del ultimo dia`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.GASTO, 10, fecha = fecha(1)),
                            movimiento(TipoDeTransaccion.GASTO, 20, fecha = fecha(31)),
                        ),
                )

            resumen.gastosReales shouldBe Money.deUnidades(30)
        }

        // Quien cobra el 25 tiene su mes economico desplazado.
        @Test
        fun `respeta un mes que empieza el 25`() {
            val periodo = PeriodoMensual(Mes.de(2026, 3), primerDia = 25)

            val resumen =
                calcular(
                    periodo,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.GASTO, 100, fecha = fecha(10, mes = 4)),
                            movimiento(TipoDeTransaccion.GASTO, 999, fecha = fecha(10, mes = 3)),
                        ),
                )

            resumen.gastosReales shouldBe Money.deUnidades(100)
        }
    }

    @Nested
    @DisplayName("totales planificados")
    inner class Planificado {
        @Test
        fun `el ahorro resta del disponible igual que un gasto`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("i", TipoDeLinea.INGRESO, 2000),
                        linea("f", TipoDeLinea.GASTO_FIJO, 800),
                        linea("v", TipoDeLinea.GASTO_VARIABLE, 400),
                        linea("a", TipoDeLinea.AHORRO, 300),
                    ),
                    emptyList(),
                )

            resumen.ingresosPlanificados shouldBe Money.deUnidades(2000)
            resumen.gastosPlanificados shouldBe Money.deUnidades(1500)
            resumen.disponiblePlanificado shouldBe Money.deUnidades(500)
        }

        @Test
        fun `una linea desactivada a mitad de mes deja de contar`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("i", TipoDeLinea.INGRESO, 1000),
                        linea("f", TipoDeLinea.GASTO_FIJO, 400, activa = false),
                    ),
                    emptyList(),
                )

            resumen.gastosPlanificados shouldBe Money.ZERO
            resumen.desviacionPorLinea.size shouldBe 1
        }

        @Test
        fun `un plan que no cuadra da disponible negativo`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("i", TipoDeLinea.INGRESO, 1000),
                        linea("f", TipoDeLinea.GASTO_FIJO, 1200),
                    ),
                    emptyList(),
                )

            resumen.disponiblePlanificado shouldBe Money.deUnidades(-200)
        }
    }

    @Nested
    @DisplayName("desviacion por linea")
    inner class Desviaciones {
        @Test
        fun `cruza cada linea con sus movimientos`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(linea("comida", TipoDeLinea.GASTO_VARIABLE, 400)),
                    listOf(
                        movimiento(TipoDeTransaccion.GASTO, 300, fecha = fecha(5), linea = "comida"),
                        movimiento(TipoDeTransaccion.GASTO, 220, fecha = fecha(20), linea = "comida"),
                    ),
                )

            val comida = resumen.desviacionPorLinea.single()
            comida.planificado shouldBe Money.deUnidades(400)
            comida.real shouldBe Money.deUnidades(520)
            comida.desviacion shouldBe Money.deUnidades(120)
            comida.porcentajeEjecutado shouldBe 130.0
            comida.esDesfavorable shouldBe true
        }

        // Un imprevisto no pertenece a ninguna linea, pero se te ha ido del
        // bolsillo igual: no sale en las desviaciones y si en el total real.
        @Test
        fun `un gasto sin linea no aparece en desviaciones pero si en el total`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(linea("comida", TipoDeLinea.GASTO_VARIABLE, 400)),
                    listOf(movimiento(TipoDeTransaccion.GASTO, 90)),
                )

            resumen.desviacionPorLinea.single().real shouldBe Money.ZERO
            resumen.gastosReales shouldBe Money.deUnidades(90)
        }

        @Test
        fun `una linea sin movimientos se ejecuto al cero por ciento`() {
            val resumen =
                calcular(MARZO, plan(linea("ocio", TipoDeLinea.GASTO_VARIABLE, 100)), emptyList())

            resumen.desviacionPorLinea.single().porcentajeEjecutado shouldBe 0.0
        }

        @Test
        fun `una linea planificada a cero no tiene porcentaje`() {
            val resumen =
                calcular(MARZO, plan(linea("ocio", TipoDeLinea.GASTO_VARIABLE, 0)), emptyList())

            resumen.desviacionPorLinea
                .single()
                .porcentajeEjecutado
                .shouldBeNull()
        }

        // El signo solo no basta: en un gasto pasarse es malo, en un ingreso lo
        // malo es quedarse corto. Leerlo al reves invierte todo el informe.
        @Test
        fun `quedarse corto es malo en un ingreso y bueno en un gasto`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("sueldo", TipoDeLinea.INGRESO, 2000),
                        linea("comida", TipoDeLinea.GASTO_VARIABLE, 400),
                    ),
                    listOf(
                        movimiento(TipoDeTransaccion.INGRESO, 1500, linea = "sueldo"),
                        movimiento(TipoDeTransaccion.GASTO, 300, linea = "comida"),
                    ),
                )

            val sueldo = resumen.desviacionPorLinea.first { it.linea.id == LineaId("sueldo") }
            val comida = resumen.desviacionPorLinea.first { it.linea.id == LineaId("comida") }

            sueldo.desviacion shouldBe Money.deUnidades(-500)
            sueldo.esDesfavorable shouldBe true
            comida.desviacion shouldBe Money.deUnidades(-100)
            comida.esDesfavorable shouldBe false
        }

        @Test
        fun `ordena las desviaciones malas de peor a menos mala`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("a", TipoDeLinea.GASTO_VARIABLE, 100),
                        linea("b", TipoDeLinea.GASTO_VARIABLE, 100),
                    ),
                    listOf(
                        movimiento(TipoDeTransaccion.GASTO, 150, fecha = fecha(4), linea = "a"),
                        movimiento(TipoDeTransaccion.GASTO, 400, fecha = fecha(5), linea = "b"),
                    ),
                )

            resumen.desviacionesDesfavorables.map { it.linea.id } shouldBe
                listOf(LineaId("b"), LineaId("a"))
        }
    }

    @Nested
    @DisplayName("sobregiro")
    inner class Sobregiro {
        @Test
        fun `gastar mas de lo que entra es sobregiro`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.INGRESO, 100, fecha = fecha(1)),
                            movimiento(TipoDeTransaccion.GASTO, 300, fecha = fecha(2)),
                        ),
                )

            resumen.enSobregiro shouldBe true
            resumen.disponibleReal shouldBe Money.deUnidades(-200)
        }

        // La reaccion es distinta: si te pasaste gastando, recortas; si el
        // ingreso no llego, recortar no arregla nada.
        @Test
        fun `distingue el sobregiro por un ingreso que no llego`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("sueldo", TipoDeLinea.INGRESO, 2000),
                        linea("fijo", TipoDeLinea.GASTO_FIJO, 900),
                    ),
                    listOf(movimiento(TipoDeTransaccion.GASTO, 800, linea = "fijo")),
                )

            resumen.enSobregiro shouldBe true
            resumen.sobregiroPorIngresosQueNoLlegaron shouldBe true
        }

        @Test
        fun `no lo confunde con haberse pasado gastando`() {
            val resumen =
                calcular(
                    MARZO,
                    plan(
                        linea("sueldo", TipoDeLinea.INGRESO, 1000),
                        linea("fijo", TipoDeLinea.GASTO_FIJO, 200),
                    ),
                    listOf(
                        movimiento(TipoDeTransaccion.INGRESO, 1000, fecha = fecha(1), linea = "sueldo"),
                        movimiento(TipoDeTransaccion.GASTO, 1500, fecha = fecha(2), linea = "fijo"),
                    ),
                )

            resumen.enSobregiro shouldBe true
            resumen.sobregiroPorIngresosQueNoLlegaron shouldBe false
        }

        @Test
        fun `quedarse justo a cero no es sobregiro`() {
            val resumen =
                calcular(
                    MARZO,
                    plan = null,
                    transacciones =
                        listOf(
                            movimiento(TipoDeTransaccion.INGRESO, 500, fecha = fecha(1)),
                            movimiento(TipoDeTransaccion.GASTO, 500, fecha = fecha(2)),
                        ),
                )

            resumen.enSobregiro shouldBe false
            resumen.disponibleReal shouldBe Money.ZERO
        }
    }

    @Nested
    @DisplayName("cifras grandes")
    inner class CifrasGrandes {
        // Monedas con mucha inflacion: sueldos de millones. Debe seguir siendo
        // exacto y no desbordar.
        @Test
        fun `maneja cantidades muy por encima del rango de un Int`() {
            val sueldo = 50_000_000L

            val resumen =
                calcular(
                    MARZO,
                    plan(linea("sueldo", TipoDeLinea.INGRESO, sueldo)),
                    listOf(movimiento(TipoDeTransaccion.INGRESO, sueldo, linea = "sueldo")),
                )

            resumen.ingresosReales shouldBe Money.deUnidades(sueldo)
            resumen.desviacionPorLinea.single().desviacion shouldBe Money.ZERO
        }
    }
}
