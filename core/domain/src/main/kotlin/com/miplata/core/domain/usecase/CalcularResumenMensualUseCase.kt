package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.DesviacionLinea
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.ResumenMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar

/**
 * Cruza el plan del mes con los movimientos reales y responde "¿me alcanza?".
 *
 * Es una **funcion pura**: recibe los datos, devuelve el resumen. Sin base de
 * datos, sin Android, sin reloj. Por eso se puede probar a conciencia en
 * milisegundos, que es justo lo que hace falta en la pieza que decide si las
 * cifras que ve el usuario son correctas.
 *
 * Vive aqui y no en SQL a proposito (docs/01): es la logica mas valiosa del
 * proyecto y la que mas va a cambiar. Room se ocupa de traer filas.
 */
class CalcularResumenMensualUseCase {
    operator fun invoke(
        periodo: PeriodoMensual,
        plan: PlanMensual?,
        transacciones: List<Transaccion>,
    ): ResumenMensual {
        // Pasar el plan de abril para un periodo de marzo produciria un resumen
        // etiquetado como marzo con las cifras de abril: cifras plausibles y
        // silenciosamente equivocadas, que es la peor clase de error en una app
        // de finanzas. Es un fallo de programacion -el repositorio busca el plan
        // por mes- asi que falla rapido y ruidoso (docs/01).
        require(plan == null || plan.mes == periodo.mes) {
            "El plan es de ${plan?.mes} pero el periodo es de ${periodo.mes}"
        }

        // Dos filtros antes de sumar nada, y los dos importan:
        //
        // 1. Solo cuentan los movimientos del periodo. Un gasto de abril no
        //    ensucia el resumen de marzo aunque venga en la misma lista.
        // 2. Fuera las transferencias. Mover dinero entre cuentas propias no es
        //    ingreso ni gasto: el patrimonio no cambia. Contarlas es el error
        //    clasico que infla los informes.
        val delPeriodo =
            transacciones.filter { periodo.contiene(it.fecha) && !it.esTransferencia }

        val lineasActivas = plan?.lineasActivas.orEmpty()

        return ResumenMensual(
            periodo = periodo,
            ingresosPlanificados = totalPlanificado(lineasActivas) { it.tipo == TipoDeLinea.INGRESO },
            gastosPlanificados = totalPlanificado(lineasActivas) { it.tipo.restaDelDisponible },
            ingresosReales = totalReal(delPeriodo, TipoDeTransaccion.INGRESO),
            gastosReales = totalReal(delPeriodo, TipoDeTransaccion.GASTO),
            desviacionPorLinea = desviaciones(lineasActivas, delPeriodo),
        )
    }

    private fun totalPlanificado(
        lineas: List<LineaDePlan>,
        criterio: (LineaDePlan) -> Boolean,
    ): Money = lineas.filter(criterio).map { it.montoPlanificado }.sumar()

    private fun totalReal(
        transacciones: List<Transaccion>,
        tipo: TipoDeTransaccion,
    ): Money = transacciones.filter { it.tipo == tipo }.map { it.monto }.sumar()

    /**
     * Una desviacion por cada linea activa del plan.
     *
     * Las transacciones sin linea asociada -los gastos imprevistos- no aparecen
     * aqui, pero **si** cuentan en los totales reales. Esa asimetria es
     * intencionada: un imprevisto no pertenece a ninguna linea, pero desde luego
     * se te ha ido del bolsillo.
     */
    private fun desviaciones(
        lineas: List<LineaDePlan>,
        transacciones: List<Transaccion>,
    ): List<DesviacionLinea> {
        val realPorLinea =
            transacciones
                .mapNotNull { transaccion -> transaccion.lineaDePlanId?.let { it to transaccion.monto } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, montos) -> montos.sumar() }

        return lineas.map { linea ->
            DesviacionLinea(
                linea = linea,
                planificado = linea.montoPlanificado,
                real = realPorLinea[linea.id] ?: Money.ZERO,
            )
        }
    }
}
