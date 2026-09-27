package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * El mes de una cuenta, segun el plan.
 *
 * "Diversion empieza con 7, recibe 63,30, planeas gastar 20: deberia terminar
 * con 50,30."
 *
 * @property lineas las lineas del plan que salen de esta cuenta, activas o no,
 *   en el orden del plan. Los totales solo cuentan las activas.
 */
data class PlanDeCuenta(
    val cuenta: Cuenta,
    /** Con cuanto empieza el mes: el saldo al terminar el anterior. */
    val empiezaCon: Money,
    /** Ingresos planeados que caen en esta cuenta. */
    val ingresos: Money,
    /** Lo que recibe de la principal, si es un sobre. */
    val recibe: Money,
    /** Lo que reparte a los sobres, si es la principal. */
    val reparte: Money,
    /** Gastos y aportes planeados desde esta cuenta. */
    val gastos: Money,
    val lineas: List<LineaDePlan>,
    /** Lo que hay hoy, con lo anotado hasta ahora. */
    val saldoActual: Money,
) {
    /** Con cuanto deberia terminar el mes si todo va segun el plan. */
    val terminaCon: Money get() = empiezaCon + ingresos + recibe - reparte - gastos

    /** Si el plan la deja en negativo: hara falta cubrirla. */
    val quedaCorta: Boolean get() = terminaCon.esNegativo
}

/**
 * El plan del mes visto por cuentas.
 *
 * @property sinRepartir lo que queda del ingreso de la principal despues de
 *   repartir a los sobres y pagar sus gastos. Es el "0,80" de la hoja de
 *   calculo: cero es un plan que cuadra; negativo, uno que no alcanza.
 */
data class PlanPorCuentas(
    val cuentas: List<PlanDeCuenta>,
    val ingresoPlaneado: Money,
    val sinRepartir: Money,
) {
    val principal: PlanDeCuenta get() = cuentas.first { it.cuenta.esPrincipal }

    fun de(cuentaId: CuentaId): PlanDeCuenta? = cuentas.firstOrNull { it.cuenta.id == cuentaId }
}

/**
 * Reparte el plan del mes entre las cuentas del metodo (docs/adr/0007).
 *
 * Devuelve `null` si no hay cuenta principal: sin ella no hay a quien le llegue
 * el ingreso ni desde donde repartir, y la app sigue con el plan de siempre.
 *
 * Reglas:
 * - Una linea sin cuenta, o de una cuenta archivada o que ya no existe, sale de
 *   la principal. Es lo que pasaba con todas las lineas antes de los sobres.
 * - El reparto de cada sobre se calcula sobre **todos** los ingresos planeados
 *   del mes: "el 10% de lo que cobro".
 * - Las cuentas independientes solo aparecen si alguna linea sale de ellas.
 */
class CalcularPlanPorCuentasUseCase {
    operator fun invoke(
        periodo: PeriodoMensual,
        cuentas: List<Cuenta>,
        plan: PlanMensual?,
        transacciones: List<Transaccion>,
        hoy: LocalDate,
    ): PlanPorCuentas? {
        val vigentes = cuentas.filterNot { it.archivada }
        val principal = vigentes.firstOrNull { it.esPrincipal } ?: return null
        val sobres = vigentes.filter { it.sobre != null }

        val lineas = plan?.lineas.orEmpty()
        val idsVigentes = vigentes.map { it.id }.toSet()
        val lineasPorCuenta =
            lineas.groupBy { linea -> linea.cuentaId?.takeIf { it in idsVigentes } ?: principal.id }

        val ingresoPlaneado = lineas.filter { it.activa && it.tipo == TipoDeLinea.INGRESO }.total()
        val repartoPorSobre = sobres.associate { it.id to it.sobre!!.reparto.de(ingresoPlaneado) }

        val alEmpezar = saldosAl(periodo.inicio.minus(1, DateTimeUnit.DAY), vigentes, transacciones)
        val hoyOElFin = minOf(maxOf(hoy, periodo.inicio), periodo.fin)
        val actuales = saldosAl(hoyOElFin, vigentes, transacciones)

        val independientesConLineas =
            vigentes.filter { !it.estaEnElMetodo && lineasPorCuenta.containsKey(it.id) }

        val planes =
            (listOf(principal) + sobres + independientesConLineas).map { cuenta ->
                val suyas = lineasPorCuenta[cuenta.id].orEmpty()
                val activas = suyas.filter { it.activa }
                PlanDeCuenta(
                    cuenta = cuenta,
                    empiezaCon = alEmpezar.getValue(cuenta.id),
                    ingresos = activas.filter { it.tipo == TipoDeLinea.INGRESO }.total(),
                    recibe = repartoPorSobre[cuenta.id] ?: Money.ZERO,
                    reparte = if (cuenta.esPrincipal) repartoPorSobre.values.sumar() else Money.ZERO,
                    gastos = activas.filter { it.tipo.restaDelDisponible }.total(),
                    lineas = suyas,
                    saldoActual = actuales.getValue(cuenta.id),
                )
            }

        val dePrincipal = planes.first()
        return PlanPorCuentas(
            cuentas = planes,
            ingresoPlaneado = ingresoPlaneado,
            sinRepartir = dePrincipal.ingresos - dePrincipal.reparte - dePrincipal.gastos,
        )
    }

    private fun List<LineaDePlan>.total(): Money = map { it.montoPlanificado }.sumar()
}
