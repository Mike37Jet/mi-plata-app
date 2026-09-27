package com.miplata.core.domain.model

/**
 * Como termino una cuenta un mes cerrado: lo que el plan esperaba y lo que
 * decia el banco.
 */
data class SaldoDeCierre(
    val cuentaId: CuentaId,
    /** Con cuanto deberia haber terminado segun el plan. */
    val esperado: Money,
    /** Con cuanto termino de verdad, copiado del banco. */
    val real: Money,
) {
    /** Positivo: sobro. Negativo: se gasto de mas. */
    val diferencia: Money get() = real - esperado
}

/**
 * Un mes que el usuario cerro con los saldos reales de sus cuentas
 * (docs/adr/0007).
 *
 * Se guarda lo esperado junto a lo real, y no solo lo real, para que la
 * comparacion de un mes pasado no cambie si despues se toca el reparto de un
 * sobre o el plan: el cierre es una foto, igual que el plan (ADR 0003).
 *
 * Los saldos no son la fuente del saldo de una cuenta, que sigue siendo
 * derivado (docs/03). El cierre deja movimientos de ajuste que hacen cuadrar el
 * derivado con el real; esto es solo el registro de como fue.
 */
data class CierreDeMes(
    val mes: Mes,
    val saldos: List<SaldoDeCierre>,
) {
    init {
        val repetidas =
            saldos
                .groupingBy { it.cuentaId }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(repetidas.isEmpty()) { "El cierre de $mes repite cuentas: ${repetidas.joinToString()}" }
    }

    fun saldoDe(cuentaId: CuentaId): SaldoDeCierre? = saldos.firstOrNull { it.cuentaId == cuentaId }
}
