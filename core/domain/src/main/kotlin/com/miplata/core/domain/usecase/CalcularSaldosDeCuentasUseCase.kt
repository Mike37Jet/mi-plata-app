package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar

/** Una cuenta y lo que hay en ella ahora mismo. */
data class CuentaConSaldo(
    val cuenta: Cuenta,
    val saldo: Money,
) {
    /** Una cuenta en numeros rojos. En una tarjeta de credito es lo normal. */
    val enNegativo: Boolean get() = saldo.esNegativo
}

/**
 * Los saldos de todas las cuentas y, si se pueden sumar, el total.
 *
 * @param cuentasEnOtraMoneda cuantas cuentas se quedaron fuera del total por
 *   estar en otra moneda. Si es mayor que cero, el total **no** es todo lo que
 *   tiene el usuario, y la pantalla tiene que decirlo.
 */
data class SaldosDeCuentas(
    val cuentas: List<CuentaConSaldo>,
    val total: Money,
    val monedaDelTotal: Moneda,
    val cuentasEnOtraMoneda: Int,
)

/**
 * Calcula cuanto hay en cada cuenta a partir del saldo inicial y los
 * movimientos.
 *
 * Existe porque `Cuenta` **no guarda el saldo** (docs/03): un campo actualizado
 * a mano se desincroniza en cuanto una operacion falle a medias, y a partir de
 * ahi la app miente sin que nada lo detecte. Aqui el saldo se deriva, asi que
 * no puede mentir.
 *
 * Ojo a la diferencia con [CalcularResumenMensualUseCase], que es deliberada y
 * facil de leer al reves: alli las **transferencias se ignoran**, porque mover
 * dinero entre cuentas propias no es ingreso ni gasto. Aqui **si cuentan**,
 * porque cambian donde esta el dinero, que es justo lo que esta pantalla
 * responde. Los mismos datos, tratados al contrario, y las dos veces bien.
 */
class CalcularSaldosDeCuentasUseCase {
    operator fun invoke(
        cuentas: List<Cuenta>,
        transacciones: List<Transaccion>,
        monedaDelTotal: Moneda,
    ): SaldosDeCuentas {
        val movimientoPorCuenta = movimientoPorCuenta(transacciones)

        val conSaldo =
            cuentas
                .map { cuenta ->
                    CuentaConSaldo(
                        cuenta = cuenta,
                        saldo = cuenta.saldoInicial + (movimientoPorCuenta[cuenta.id] ?: Money.ZERO),
                    )
                }
                // Las archivadas al final: siguen ahi para que cuadre el
                // historial, pero ya no son en lo que se piensa al abrir la
                // pantalla.
                .sortedBy { it.cuenta.archivada }

        val sumables = conSaldo.filter { cuentaSuma(it.cuenta, monedaDelTotal) }

        return SaldosDeCuentas(
            cuentas = conSaldo,
            total = sumables.map { it.saldo }.sumar(),
            monedaDelTotal = monedaDelTotal,
            // Sumar euros con dolares da un numero que no significa nada. En vez
            // de inventarse un cambio -que estaria desactualizado el mismo dia-,
            // se dejan fuera y se cuenta cuantas son.
            cuentasEnOtraMoneda =
                conSaldo.count {
                    cuentaEntraEnElTotal(it.cuenta) && it.cuenta.moneda != monedaDelTotal
                },
        )
    }

    private fun cuentaSuma(
        cuenta: Cuenta,
        moneda: Moneda,
    ): Boolean = cuentaEntraEnElTotal(cuenta) && cuenta.moneda == moneda

    /** Archivar o desmarcar del total son decisiones del usuario, no avisos. */
    private fun cuentaEntraEnElTotal(cuenta: Cuenta): Boolean = cuenta.incluirEnTotal && !cuenta.archivada

    /**
     * Cuanto se ha movido en cada cuenta desde su saldo inicial.
     *
     * Una transferencia toca dos cuentas a la vez: sale de una y entra en otra.
     * Por eso no vale con agrupar por `cuentaOrigenId`.
     */
    private fun movimientoPorCuenta(transacciones: List<Transaccion>): Map<CuentaId, Money> =
        transacciones
            .flatMap { efectoEnCuentas(it) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, montos) -> montos.sumar() }

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
}
