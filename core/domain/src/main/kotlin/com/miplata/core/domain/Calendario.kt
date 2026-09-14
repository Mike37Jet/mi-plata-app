package com.miplata.core.domain

import com.miplata.core.domain.model.Mes
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Que dia es hoy.
 *
 * Es una interfaz y no una llamada directa al reloj para que los tests puedan
 * fijarlo. Un ViewModel que preguntara la fecha por dentro tendria tests que
 * fallan el dia 1 de cada mes, o en diciembre, o nunca hasta que fallan.
 *
 * Hay uno solo para toda la app: el mes se deduce del dia, no al reves, asi que
 * dos relojes distintos podrian discrepar justo en el cambio de mes.
 */
interface Calendario {
    fun hoy(): LocalDate

    fun mesActual(): Mes = Mes.de(hoy())

    companion object {
        val DEL_SISTEMA =
            object : Calendario {
                override fun hoy(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
            }
    }
}
