package com.miplata.feature.resumen

import com.miplata.core.domain.model.DesviacionLinea
import com.miplata.core.domain.model.FueraDelPlan
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta

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
    /**
     * Cada cuenta del metodo: con cuanto deberia terminar y con cuanto va, o
     * termino si el mes esta cerrado (docs/adr/0007). Vacio sin cuenta principal.
     */
    val porCuenta: List<CuentaDelResumen> = emptyList(),
    /** Si el mes esta cerrado, o nulo si no hay metodo con el que cerrarlo. */
    val cerrado: Boolean? = null,
    /**
     * Si se ofrece cerrar este mes: sin cerrar y en sus ultimos dias, o ya
     * pasado (`esHoraDeCerrar`). Antes, el boton solo estorbaria.
     */
    val ofrecerCierre: Boolean = false,
    /** Si hay algun mes cerrado que ver en el historial. */
    val hayMesesCerrados: Boolean = false,
    /** Lo gastado sin estar en el plan: imprevistos y "Sin detalle". */
    val fueraDelPlan: FueraDelPlan = FueraDelPlan(),
    /** Si hay cuenta del sueldo: la pantalla se ordena por cuentas y por pasos. */
    val conMetodo: Boolean = false,
    val paso: PasoDelMes = PasoDelMes.NINGUNO,
    /** Lo que falta pasar a cada cuenta, para el paso [PasoDelMes.REPARTIR]. */
    val partesPendientes: List<PartePendiente> = emptyList(),
    /** Que dia del mes es hoy, o nulo si se mira otro mes. */
    val dia: Int? = null,
    val diasDelMes: Int = 0,
    /** Si el mes ya termino. */
    val esPasado: Boolean = false,
) {
    val esElMesActual: Boolean get() = dia != null

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

/**
 * @property esperado con cuanto deberia terminar el mes segun el plan.
 * @property actual con cuanto va hoy o, en un mes cerrado, con cuanto termino.
 */
data class CuentaDelResumen(
    val nombre: String,
    val esperado: Money,
    val actual: Money,
    val intocable: Boolean = false,
    val tipo: TipoDeCuenta = TipoDeCuenta.BANCARIA,
)

/**
 * Lo que toca hacer ahora en el mes, segun el metodo (docs/adr/0007).
 *
 * Uno solo a la vez y en el orden en que pasan las cosas: quien abre la app
 * tiene que ver que hacer, no buscarlo entre bloques.
 */
enum class PasoDelMes {
    /** El mes no tiene plan: hay que armarlo. */
    PLANEAR,

    /** Falta pasar a las cuentas su parte del sueldo. */
    REPARTIR,

    /** Fin de mes: comparar con el banco. */
    COMPARAR,

    /** El mes ya se comparo con el banco. */
    CERRADO,

    /** Nada que hacer: vivir el mes. */
    NINGUNO,
}

/** Lo que falta pasar a una cuenta este mes. */
data class PartePendiente(
    val cuenta: String,
    val monto: Money,
)

sealed interface EventoDelResumen {
    data object MesAnterior : EventoDelResumen

    data object MesSiguiente : EventoDelResumen

    /** "Ya lo hice": anota las transferencias de la parte de cada cuenta. */
    data object YaRepartí : EventoDelResumen
}
