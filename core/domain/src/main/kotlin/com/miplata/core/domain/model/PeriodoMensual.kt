package com.miplata.core.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * El tramo de dias que cuenta como "un mes" para las finanzas del usuario.
 *
 * Para casi todo el mundo coincide con el mes del calendario, pero no para todo
 * el mundo: si cobras el 25, tu mes economico va del 25 al 24. Obligar a esa
 * persona a razonar en meses naturales parte su sueldo en dos y hace que ningun
 * resumen le cuadre.
 *
 * El periodo **etiquetado** como un mes empieza el dia [primerDia] de ese mes y
 * termina la vispera del mismo dia del mes siguiente. Con `primerDia = 25`, el
 * periodo de marzo de 2026 va del 25 de marzo al 24 de abril.
 *
 * Si el dia elegido no existe en algun mes -un 31 en febrero- se recorta al
 * ultimo dia disponible.
 */
data class PeriodoMensual(
    val mes: Mes,
    val primerDia: Int = 1,
) {
    init {
        require(primerDia in 1..DIA_MAXIMO) {
            "El primer dia del mes debe estar entre 1 y $DIA_MAXIMO, no $primerDia"
        }
    }

    val inicio: LocalDate get() = diaDe(mes, primerDia)

    /** Ultimo dia del periodo, **incluido**. */
    val fin: LocalDate get() = diaDe(mes.siguiente(), primerDia).minus(1, DateTimeUnit.DAY)

    fun contiene(fecha: LocalDate): Boolean = fecha >= inicio && fecha <= fin

    fun siguiente(): PeriodoMensual = copy(mes = mes.siguiente())

    fun anterior(): PeriodoMensual = copy(mes = mes.anterior())

    private fun diaDe(
        mes: Mes,
        dia: Int,
    ): LocalDate {
        val primero = LocalDate(mes.anio, mes.numeroDeMes, 1)
        val ultimoDelMes = primero.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return LocalDate(mes.anio, mes.numeroDeMes, minOf(dia, ultimoDelMes.dayOfMonth))
    }

    private companion object {
        const val DIA_MAXIMO = 31
    }
}
