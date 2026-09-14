package com.miplata.core.domain.model

import kotlinx.datetime.LocalDate

/**
 * Un mes concreto de un anio concreto: "marzo de 2026".
 *
 * Es el eje sobre el que gira la app. Un [PlanMensual] pertenece a un mes, los
 * resumenes se calculan por mes, y materializar el plan del mes que viene
 * significa copiar el del [anterior].
 *
 * Se define aqui en vez de usar un tipo de libreria porque es un concepto del
 * negocio y necesita operaciones propias ([siguiente], [anterior], [contiene]).
 * Tenerlo en el dominio tambien evita atar el modelo a la API de una libreria
 * de fechas que puede cambiar.
 *
 * Por dentro es un unico entero -los meses transcurridos desde el anio 0- y de
 * ahi salen gratis el orden natural y la aritmetica: el mes siguiente es sumar
 * uno, sin casos especiales en diciembre.
 */
@JvmInline
value class Mes private constructor(
    private val mesesAbsolutos: Int,
) : Comparable<Mes> {
    val anio: Int get() = mesesAbsolutos / MESES_POR_ANIO

    /** De 1 (enero) a 12 (diciembre). */
    val numeroDeMes: Int get() = mesesAbsolutos % MESES_POR_ANIO + 1

    fun siguiente(): Mes = Mes(mesesAbsolutos + 1)

    fun anterior(): Mes = Mes(mesesAbsolutos - 1)

    operator fun plus(meses: Int): Mes = Mes(mesesAbsolutos + meses)

    operator fun minus(meses: Int): Mes = Mes(mesesAbsolutos - meses)

    /** Cuantos meses hay entre este y [otro]. Negativo si [otro] es anterior. */
    fun mesesHasta(otro: Mes): Int = otro.mesesAbsolutos - mesesAbsolutos

    fun contiene(fecha: LocalDate): Boolean = fecha.year == anio && fecha.monthNumber == numeroDeMes

    override fun compareTo(other: Mes): Int = mesesAbsolutos.compareTo(other.mesesAbsolutos)

    /** Formato ISO `AAAA-MM`, estable e independiente del locale. */
    override fun toString(): String = "$anio-${numeroDeMes.toString().padStart(2, '0')}"

    companion object {
        private const val MESES_POR_ANIO = 12
        private const val SEPARADOR = "-"

        fun de(
            anio: Int,
            mes: Int,
        ): Mes {
            require(mes in 1..MESES_POR_ANIO) { "El mes debe estar entre 1 y 12, no $mes" }
            return Mes(anio * MESES_POR_ANIO + (mes - 1))
        }

        fun de(fecha: LocalDate): Mes = de(fecha.year, fecha.monthNumber)

        /**
         * Inverso exacto de [toString]: `"2026-03"` vuelve a ser marzo de 2026.
         *
         * Vive aqui, junto a [toString], y no en quien lo necesite. El formato
         * es una sola decision con dos mitades; separarlas es como acaban los
         * formatos por dejar de encajar sin que nadie se entere.
         *
         * Lo usan la capa de datos -SQLite guarda el mes como texto ISO porque
         * ordena bien- y lo usara el backup.
         */
        fun de(texto: String): Mes {
            val partes = texto.split(SEPARADOR)
            require(partes.size == 2) { "Un mes se escribe AAAA-MM, no '$texto'" }

            val anio = partes[0].toIntOrNull()
            val mes = partes[1].toIntOrNull()
            require(anio != null && mes != null) { "Un mes se escribe AAAA-MM, no '$texto'" }

            return de(anio, mes)
        }
    }
}
