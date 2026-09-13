package com.miplata.core.domain.model

/**
 * Como se porto una linea del plan frente a la realidad.
 *
 * "Planificaste 400 de comida, llevas gastados 520."
 */
data class DesviacionLinea(
    val linea: LineaDePlan,
    val planificado: Money,
    val real: Money,
) {
    /** Real menos planificado. El signo hay que leerlo junto al tipo de linea. */
    val desviacion: Money get() = real - planificado

    /** Que parte de lo planificado se ha ejecutado. Nulo si no se planifico nada. */
    val porcentajeEjecutado: Double? get() = real.porcentajeDe(planificado)

    /**
     * Si la desviacion es una mala noticia.
     *
     * No basta con mirar el signo: en un gasto, pasarse es malo; en un ingreso,
     * lo malo es quedarse corto. Esta es exactamente la confusion que hace que
     * un informe de finanzas se lea al reves.
     */
    val esDesfavorable: Boolean
        get() =
            if (linea.tipo == TipoDeLinea.INGRESO) {
                desviacion.esNegativo
            } else {
                desviacion.esPositivo
            }
}

/**
 * La respuesta a "¿me alcanza este mes?".
 *
 * Cruza las dos mitades del modelo: lo que planificaste ([PlanMensual]) y lo que
 * de verdad paso ([Transaccion]).
 */
data class ResumenMensual(
    val periodo: PeriodoMensual,
    val ingresosPlanificados: Money,
    val ingresosReales: Money,
    val gastosPlanificados: Money,
    val gastosReales: Money,
    val desviacionPorLinea: List<DesviacionLinea>,
) {
    /** Ingresos menos salidas, segun el plan. Negativo: el plan no cuadra sobre el papel. */
    val disponiblePlanificado: Money get() = ingresosPlanificados - gastosPlanificados

    /** Ingresos menos salidas, segun lo ocurrido. */
    val disponibleReal: Money get() = ingresosReales - gastosReales

    /** Se ha gastado mas de lo que ha entrado. */
    val enSobregiro: Boolean get() = disponibleReal.esNegativo

    /**
     * El sobregiro se explica por ingresos que no llegaron, no por haber gastado
     * de mas.
     *
     * Importa porque la reaccion es distinta: si te pasaste gastando, recortas;
     * si el ingreso no llego, el problema es de cobro y recortar no lo arregla.
     */
    val sobregiroPorIngresosQueNoLlegaron: Boolean
        get() = enSobregiro && ingresosReales < ingresosPlanificados && gastosReales <= gastosPlanificados

    /** Lineas que se desviaron para mal, de la peor a la menos mala. */
    val desviacionesDesfavorables: List<DesviacionLinea>
        get() =
            desviacionPorLinea
                .filter { it.esDesfavorable }
                .sortedByDescending { it.desviacion.valorAbsoluto() }
}
