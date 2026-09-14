package com.miplata.feature.resumen

import com.miplata.core.domain.model.DesviacionLinea
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money

/**
 * Todo lo que la pantalla del resumen necesita para dibujarse.
 *
 * Es la respuesta a "¿me alcanza este mes?", que es la pregunta que justifica la
 * app entera (docs/00).
 */
data class ResumenUiState(
    val mes: Mes,
    val moneda: Moneda = Moneda("USD"),
    val cargando: Boolean = true,
    val hayPlan: Boolean = false,
    val ingresosPlanificados: Money = Money.ZERO,
    val ingresosReales: Money = Money.ZERO,
    val gastosPlanificados: Money = Money.ZERO,
    val gastosReales: Money = Money.ZERO,
    val disponiblePlanificado: Money = Money.ZERO,
    val disponibleReal: Money = Money.ZERO,
    val enSobregiro: Boolean = false,
    /**
     * El mes esta en rojo porque falto ingreso, no porque sobrara gasto.
     *
     * La pantalla lo distingue porque la reaccion es distinta: si te pasaste
     * gastando, recortas; si el ingreso no llego, recortar no arregla nada.
     */
    val sobregiroPorIngresosQueNoLlegaron: Boolean = false,
    /** Que parte del mes ha pasado, de 0 a 1. */
    val progresoDelMes: Double = 0.0,
    /**
     * Que parte del gasto planificado se lleva ejecutada, de 0 a 1 o mas.
     *
     * Nulo si no hay nada planificado: "llevas el 0 por ciento de nada" no es
     * una frase que signifique algo.
     */
    val progresoDelGasto: Double? = null,
    /** Las lineas que se desviaron para mal, de la peor a la menos mala. */
    val desviaciones: List<DesviacionLinea> = emptyList(),
) {
    /**
     * Se esta gastando mas deprisa de lo que pasa el mes.
     *
     * Es la senal util del resumen: a mitad de mes con el 80% del presupuesto
     * gastado, el problema no es el total todavia, es el ritmo.
     */
    val gastaMasDeprisaQuePasaElMes: Boolean
        get() = progresoDelGasto != null && progresoDelGasto > progresoDelMes && !mesTerminado

    private val mesTerminado: Boolean get() = progresoDelMes >= 1.0
}

sealed interface EventoDelResumen {
    data object MesAnterior : EventoDelResumen

    data object MesSiguiente : EventoDelResumen
}
