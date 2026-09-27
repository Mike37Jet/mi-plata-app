package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar
import kotlinx.datetime.LocalDate

/**
 * Cuanto se ha movido en cada cuenta.
 *
 * Una transferencia toca dos cuentas a la vez: sale de una y entra en otra.
 * Por eso no vale con agrupar por `cuentaOrigenId`.
 */
internal fun movimientoPorCuenta(transacciones: List<Transaccion>): Map<CuentaId, Money> =
    transacciones
        .flatMap { efectoEnCuentas(it) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, montos) -> montos.sumar() }

/**
 * El saldo de cada cuenta al terminar el dia [hasta], incluido.
 *
 * Es el mismo calculo que el saldo de hoy, cortado en una fecha: con el dia
 * anterior al inicio de un mes da con cuanto empezo ese mes.
 */
internal fun saldosAl(
    hasta: LocalDate,
    cuentas: List<Cuenta>,
    transacciones: List<Transaccion>,
): Map<CuentaId, Money> {
    val movido = movimientoPorCuenta(transacciones.filter { it.fecha <= hasta })
    return cuentas.associate { it.id to it.saldoInicial + (movido[it.id] ?: Money.ZERO) }
}

private fun efectoEnCuentas(transaccion: Transaccion): List<Pair<CuentaId, Money>> =
    when (transaccion.tipo) {
        TipoDeTransaccion.INGRESO -> listOf(transaccion.cuentaOrigenId to transaccion.monto)
        TipoDeTransaccion.GASTO -> listOf(transaccion.cuentaOrigenId to -transaccion.monto)
        TipoDeTransaccion.TRANSFERENCIA ->
            listOf(
                transaccion.cuentaOrigenId to -transaccion.monto,
                // El modelo garantiza que una transferencia tiene destino
                // (`Transaccion.init`), asi que aqui no hay nada que decidir.
                transaccion.cuentaDestinoId!! to transaccion.monto,
            )
    }
