package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.repository.PlanRepository

/**
 * El plan de un mes, listo para editar.
 *
 * Si el mes ya tiene plan, ese. Si no, uno **materializado a partir del ultimo
 * plan anterior que exista**, aunque sea de hace tres meses: el usuario se
 * encuentra su plan ya armado y solo ajusta lo que cambio (ADR 0003).
 *
 * **Lo materializado no se guarda aqui.** Se devuelve como borrador y solo llega
 * a la base cuando el usuario edita algo. Guardarlo al abrir crearia un plan por
 * cada mes que alguien mire de pasada: navegar hasta diciembre de 2030 dejaria
 * ahi una copia de los gastos de hoy, que en 2030 ya no significarian nada.
 */
class AbrirPlanDelMesUseCase(
    private val planes: PlanRepository,
    private val materializar: MaterializarPlanDelMesUseCase,
) {
    suspend operator fun invoke(mes: Mes): PlanAbierto {
        planes.obtenerDe(mes)?.let { return PlanAbierto(it, esBorrador = false) }

        val anterior = planes.obtenerUltimoAnteriorA(mes)
        return PlanAbierto(materializar(mes, anterior), esBorrador = true)
    }
}

/**
 * @property esBorrador si el plan todavia no existe en la base. Sirve para que
 *   la pantalla pueda decir "esto viene de tu mes anterior" en vez de dar a
 *   entender que ya esta guardado.
 */
data class PlanAbierto(
    val plan: PlanMensual,
    val esBorrador: Boolean,
)
