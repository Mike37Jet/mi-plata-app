package com.miplata.feature.resumen.cierre

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money

/**
 * Una cuenta en el cierre de mes (docs/adr/0007).
 *
 * @property real lo que el usuario escribio como saldo del banco. Empieza con
 *   lo que calcula la app, para que quien lo anota todo solo tenga que
 *   confirmar. Nulo mientras el campo no tiene un importe valido.
 */
data class FilaDeCierre(
    val cuenta: Cuenta,
    val empiezaCon: Money,
    val esperado: Money,
    val real: Money?,
    /** Si el usuario dice que bajo por cubrir a la principal. */
    val cubre: Boolean = false,
) {
    /** Positivo: sobro. Negativo: se gasto de mas. */
    val diferencia: Money? get() = real?.minus(esperado)

    /**
     * Solo un sobre que termino por debajo de lo esperado puede haber cubierto a
     * la principal, y nunca uno intocable: ese dinero no se usa para cubrir
     * (docs/adr/0007). Si bajo, se avisa aparte.
     */
    val puedeCubrir: Boolean
        get() = !cuenta.esPrincipal && !cuenta.esIntocable && diferencia?.esNegativo == true

    /** Una cuenta que solo deberia subir termino el mes con menos de lo que empezo. */
    val intocableBajo: Boolean get() = cuenta.esIntocable && real != null && real < empiezaCon
}

data class CierreUiState(
    val mes: Mes,
    val moneda: Moneda = Moneda("USD"),
    val cargando: Boolean = true,
    /** Sin cuenta principal no hay plan por cuentas contra el que cerrar. */
    val sinMetodo: Boolean = false,
    val cerrado: Boolean = false,
    val filas: List<FilaDeCierre> = emptyList(),
    /** Cuantos meses posteriores estan cerrados: reabrir este los reabre tambien. */
    val posterioresCerrados: Int = 0,
    val trabajando: Boolean = false,
) {
    val principal: Cuenta? get() = filas.firstOrNull { it.cuenta.esPrincipal }?.cuenta

    val puedeCerrar: Boolean get() = !cerrado && !trabajando && filas.isNotEmpty() && filas.all { it.real != null }
}

sealed interface EventoDelCierre {
    data class CambiarReal(
        val cuentaId: CuentaId,
        val real: Money?,
    ) : EventoDelCierre

    data class CambiarCobertura(
        val cuentaId: CuentaId,
        val cubre: Boolean,
    ) : EventoDelCierre

    data object Cerrar : EventoDelCierre

    data object Reabrir : EventoDelCierre
}
