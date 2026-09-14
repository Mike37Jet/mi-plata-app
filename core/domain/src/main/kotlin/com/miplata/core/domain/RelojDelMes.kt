package com.miplata.core.domain

import com.miplata.core.domain.model.Mes
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * En que mes estamos.
 *
 * Es una interfaz y no una llamada directa al reloj para que los tests puedan
 * fijarlo. Un ViewModel que preguntara la fecha por dentro tendria tests que
 * fallan el dia 1 de cada mes, o en diciembre, o nunca hasta que fallan.
 */
fun interface RelojDelMes {
    fun mesActual(): Mes

    companion object {
        val DEL_SISTEMA =
            RelojDelMes { Mes.de(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    }
}
