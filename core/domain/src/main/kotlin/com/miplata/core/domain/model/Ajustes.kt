package com.miplata.core.domain.model

enum class Tema {
    CLARO,
    OSCURO,

    /** El que tenga el sistema. Es el valor por defecto. */
    SEGUN_EL_SISTEMA,
}

/**
 * Lo que el usuario ha configurado.
 *
 * Vive en el dominio porque dos de estos campos **son reglas de negocio**:
 * [moneda] decide en que se expresan todas las cifras, y
 * [primerDiaDelMesFinanciero] decide que movimientos entran en cada resumen.
 * [tema] es una preferencia de presentacion que viaja con ellos por no montar
 * dos almacenes para cuatro valores; no la consulta ninguna regla.
 */
data class Ajustes(
    val moneda: Moneda = Moneda("USD"),
    /**
     * El dia en que empieza el mes economico del usuario.
     *
     * Para casi todo el mundo es 1, pero quien cobra el 25 tiene su mes del 25
     * al 24 (ver [PeriodoMensual]).
     */
    val primerDiaDelMesFinanciero: Int = 1,
    val tema: Tema = Tema.SEGUN_EL_SISTEMA,
    /** Cuando se exporto el ultimo backup, o `null` si no se ha hecho ninguno. */
    val ultimoBackupEnMillis: Long? = null,
) {
    init {
        require(primerDiaDelMesFinanciero in 1..DIA_MAXIMO) {
            "El primer dia del mes debe estar entre 1 y $DIA_MAXIMO, no $primerDiaDelMesFinanciero"
        }
    }

    /** El periodo que corresponde a [mes] segun la configuracion del usuario. */
    fun periodoDe(mes: Mes): PeriodoMensual = PeriodoMensual(mes, primerDiaDelMesFinanciero)

    private companion object {
        const val DIA_MAXIMO = 31
    }
}
