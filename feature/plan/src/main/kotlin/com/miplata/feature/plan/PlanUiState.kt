package com.miplata.feature.plan

import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea

/**
 * Un bloque de la pantalla: todas las lineas de un tipo, con su total.
 */
data class SeccionDelPlan(
    val tipo: TipoDeLinea,
    val lineas: List<LineaDePlan>,
    val total: Money,
)

/**
 * Todo lo que la pantalla necesita para dibujarse.
 *
 * Un solo tipo, siempre renderizable, con banderas en vez de estados
 * mutuamente excluyentes (docs/01): mientras carga ya se puede pintar el mes y
 * las secciones vacias, y eso evita el parpadeo de una pantalla en blanco cada
 * vez que se cambia de mes.
 */
data class PlanUiState(
    val mes: Mes,
    val moneda: Moneda = Moneda("USD"),
    val secciones: List<SeccionDelPlan> = emptyList(),
    val ingresos: Money = Money.ZERO,
    val salidas: Money = Money.ZERO,
    val cargando: Boolean = true,
    /**
     * El plan viene copiado del mes anterior y todavia no se ha guardado.
     *
     * La pantalla lo dice en vez de dejar creer que ya esta guardado: el usuario
     * tiene que poder distinguir "esto es tuyo" de "esto es una propuesta".
     */
    val esBorrador: Boolean = false,
) {
    val disponible: Money get() = ingresos - salidas

    val enSobregiro: Boolean get() = disponible.esNegativo

    val estaVacio: Boolean get() = secciones.all { it.lineas.isEmpty() }
}

/** Lo que el usuario puede hacer en la pantalla. */
sealed interface EventoDelPlan {
    data object MesAnterior : EventoDelPlan

    data object MesSiguiente : EventoDelPlan

    data class AnadirLinea(
        val tipo: TipoDeLinea,
    ) : EventoDelPlan

    data class CambiarNombre(
        val linea: LineaDePlan,
        val nombre: String,
    ) : EventoDelPlan

    data class CambiarMonto(
        val linea: LineaDePlan,
        val monto: Money,
    ) : EventoDelPlan

    data class CambiarActiva(
        val linea: LineaDePlan,
        val activa: Boolean,
    ) : EventoDelPlan

    data class EliminarLinea(
        val linea: LineaDePlan,
    ) : EventoDelPlan
}
