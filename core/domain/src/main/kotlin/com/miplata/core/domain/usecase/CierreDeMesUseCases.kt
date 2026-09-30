package com.miplata.core.domain.usecase

import com.miplata.core.domain.Calendario
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.model.CierreDeMes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.SaldoDeCierre
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.TransaccionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Lo que hay que escribir para cerrar un mes: el registro del cierre y los
 * movimientos que hacen cuadrar la app con el banco.
 */
data class ResultadoDelCierre(
    val cierre: CierreDeMes,
    /** Reparto pendiente, coberturas y ajustes "Sin detalle", en ese orden. */
    val movimientos: List<Transaccion>,
)

/**
 * Calcula el cierre de un mes, sin guardar nada (docs/adr/0007).
 *
 * Por cada cuenta del plan:
 * 1. **Reparto.** Si un sobre no tiene anotada la transferencia de su reparto,
 *    se da por hecha: el metodo es transferir al empezar el mes. Sin esto, cada
 *    reparto sin anotar saldria como un gasto "Sin detalle" en la principal y
 *    un ingreso en el sobre, e inflaria el resumen por los dos lados.
 * 2. **Cobertura.** Si el usuario dice que una cuenta bajo por cubrir a la
 *    principal, se registra una transferencia de esa cuenta a la principal por
 *    lo que bajo de mas. Asi el gasto de mas se le atribuye a la principal, que
 *    es quien lo hizo, y no al sobre que puso el dinero.
 * 3. **Ajuste.** Lo que sigue sin cuadrar entre el saldo que calcula la app y
 *    el del banco queda como un ingreso o gasto "Sin detalle". Despues de esto,
 *    el saldo derivado es el del banco.
 *
 * Es una funcion pura: el mismo plan, movimientos y saldos dan siempre el
 * mismo cierre.
 */
class CalcularCierreDeMesUseCase(
    private val ids: GeneradorDeIds,
) {
    /** @param fecha el dia en que se anotan los movimientos del cierre. */
    operator fun invoke(
        periodo: PeriodoMensual,
        plan: PlanPorCuentas,
        transacciones: List<Transaccion>,
        banco: SaldosDelBanco,
        fecha: LocalDate,
    ): ResultadoDelCierre {
        val movimiento = FabricaDeMovimientos(ids, fecha, periodo.mes)
        val principal = plan.principal.cuenta.id
        val cerradas = plan.cuentas.filter { banco.reales.containsKey(it.cuenta.id) }
        val saldos = cerradas.map { SaldoDeCierre(it.cuenta.id, it.terminaCon, banco.reales.getValue(it.cuenta.id)) }

        val repartos =
            repartoPendiente(periodo, plan, transacciones)
                .filterKeys { banco.reales.containsKey(it) }
                .map { (sobre, monto) -> movimiento.transferencia(monto, desde = principal, hacia = sobre) }

        val coberturas =
            saldos
                .filter { it.cuentaId in banco.coberturas && it.cuentaId != principal && it.diferencia.esNegativo }
                .map { movimiento.transferencia(it.diferencia.valorAbsoluto(), desde = it.cuentaId, hacia = principal) }

        val transferencias = repartos + coberturas
        val derivados = saldosAl(fecha, cerradas.map { it.cuenta }, transacciones + transferencias)
        val ajustes =
            saldos.mapNotNull { saldo ->
                (saldo.real - derivados.getValue(saldo.cuentaId))
                    .takeUnless { it.esCero }
                    ?.let { movimiento.sinDetalle(it, saldo.cuentaId) }
            }

        return ResultadoDelCierre(CierreDeMes(periodo.mes, saldos), transferencias + ajustes)
    }
}

/**
 * Lo que el usuario escribe al cerrar un mes.
 *
 * @property reales el saldo de cada cuenta segun el banco. Las cuentas del plan
 *   que no esten aqui se quedan fuera del cierre.
 * @property coberturas las cuentas que bajaron por cubrir a la principal.
 */
data class SaldosDelBanco(
    val reales: Map<CuentaId, Money>,
    val coberturas: Set<CuentaId> = emptySet(),
)

/** Los movimientos que crea un cierre: todos del mismo dia y marcados con su mes. */
private class FabricaDeMovimientos(
    private val ids: GeneradorDeIds,
    private val fecha: LocalDate,
    private val mes: Mes,
) {
    fun transferencia(
        monto: Money,
        desde: CuentaId,
        hacia: CuentaId,
    ) = Transaccion(
        id = ids.nuevaTransaccionId(),
        fecha = fecha,
        monto = monto,
        tipo = TipoDeTransaccion.TRANSFERENCIA,
        cuentaOrigenId = desde,
        cuentaDestinoId = hacia,
        ajusteDeCierre = mes,
    )

    /** Positivo: entro dinero que no se anoto. Negativo: salio. */
    fun sinDetalle(
        diferencia: Money,
        cuenta: CuentaId,
    ) = Transaccion(
        id = ids.nuevaTransaccionId(),
        fecha = fecha,
        monto = diferencia.valorAbsoluto(),
        tipo = if (diferencia.esPositivo) TipoDeTransaccion.INGRESO else TipoDeTransaccion.GASTO,
        cuentaOrigenId = cuenta,
        ajusteDeCierre = mes,
    )
}

/**
 * Lo que falta por transferir a cada sobre este mes.
 *
 * El reparto que le toca menos lo que ya se anoto como transferencia de la
 * principal al sobre dentro del mes. Solo aparecen los sobres a los que les
 * falta algo.
 */
fun repartoPendiente(
    periodo: PeriodoMensual,
    plan: PlanPorCuentas,
    transacciones: List<Transaccion>,
): Map<CuentaId, Money> {
    val principal = plan.principal.cuenta.id
    return plan.cuentas
        .filter { it.recibe.esPositivo }
        .associate { sobre ->
            val hecho =
                transacciones
                    .filter {
                        it.esTransferencia &&
                            it.cuentaOrigenId == principal &&
                            it.cuentaDestinoId == sobre.cuenta.id &&
                            periodo.contiene(it.fecha)
                    }.map { it.monto }
                    .sumar()
            sobre.cuenta.id to sobre.recibe - hecho
        }.filterValues { it.esPositivo }
}

/**
 * "Ya transferi el reparto": anota hoy las transferencias que faltan de la
 * principal a cada sobre.
 *
 * Son movimientos normales, del usuario, no del cierre: reabrir un mes no los
 * borra, porque la transferencia en el banco si ocurrio.
 */
class RegistrarRepartoUseCase(
    private val transacciones: TransaccionRepository,
    private val ids: GeneradorDeIds,
    private val calendario: Calendario,
) {
    suspend operator fun invoke(
        periodo: PeriodoMensual,
        plan: PlanPorCuentas,
    ) {
        val fecha = CerrarMesUseCase.fechaDelCierre(periodo, calendario.hoy())
        repartoPendiente(periodo, plan, transacciones.observarTodas().first()).forEach { (sobre, monto) ->
            transacciones.guardar(
                Transaccion(
                    id = ids.nuevaTransaccionId(),
                    fecha = fecha,
                    monto = monto,
                    tipo = TipoDeTransaccion.TRANSFERENCIA,
                    cuentaOrigenId = plan.principal.cuenta.id,
                    cuentaDestinoId = sobre,
                ),
            )
        }
    }
}

/**
 * Cierra un mes: guarda sus movimientos de cierre y despues el cierre.
 *
 * En ese orden, y sin transaccion, porque cada estado intermedio es valido
 * (como en ADR 0006): si la app muere con los movimientos escritos y sin el
 * cierre, los saldos ya cuadran con el banco y el mes simplemente sigue
 * abierto; cerrarlo otra vez no crea ajustes nuevos, porque ya no hay
 * diferencia.
 */
class CerrarMesUseCase(
    private val transacciones: TransaccionRepository,
    private val cierres: CierreRepository,
    private val calcular: CalcularCierreDeMesUseCase,
    private val calendario: Calendario,
) {
    suspend operator fun invoke(
        periodo: PeriodoMensual,
        plan: PlanPorCuentas,
        banco: SaldosDelBanco,
    ): CierreDeMes {
        val resultado =
            calcular(
                periodo = periodo,
                plan = plan,
                transacciones = transacciones.observarTodas().first(),
                banco = banco,
                fecha = fechaDelCierre(periodo, calendario.hoy()),
            )
        resultado.movimientos.forEach { transacciones.guardar(it) }
        cierres.guardar(resultado.cierre)
        return resultado.cierre
    }

    companion object {
        /**
         * El dia en que se anota el cierre: hoy si cae dentro del mes, o su
         * ultimo dia si el mes ya paso.
         *
         * Hoy y no el ultimo dia, cuando se cierra antes de que acabe: el
         * saldo del banco es el de hoy, y un gasto que se anote manana no
         * tiene que quedar antes del cierre.
         */
        fun fechaDelCierre(
            periodo: PeriodoMensual,
            hoy: LocalDate,
        ): LocalDate = minOf(maxOf(hoy, periodo.inicio), periodo.fin)
    }
}

/**
 * Deshace el cierre de un mes y el de todos los posteriores.
 *
 * Los posteriores tambien, porque partieron de el: si marzo cambia, los ajustes
 * de abril se calcularon sobre un saldo que ya no existe.
 *
 * Borra primero los movimientos y despues el cierre. Si la app muere a mitad,
 * queda un mes marcado como cerrado sin ajustes, y reabrirlo otra vez termina
 * el trabajo.
 */
class ReabrirMesUseCase(
    private val transacciones: TransaccionRepository,
    private val cierres: CierreRepository,
) {
    suspend operator fun invoke(mes: Mes) {
        val afectados =
            cierres
                .observarTodos()
                .first()
                .map { it.mes }
                .filter { it >= mes }
                .sortedDescending()
        val todas = transacciones.observarTodas().first()
        afectados.forEach { cerrado ->
            todas.filter { it.ajusteDeCierre == cerrado }.forEach { transacciones.eliminar(it.id) }
            cierres.eliminar(cerrado)
        }
    }
}

/** Cuantos dias antes del final se ofrece cerrar el mes. */
private const val DIAS_PARA_CERRAR = 3

/**
 * Si ya toca ofrecer el cierre de [periodo]: en sus ultimos tres dias, o
 * cuando ya termino.
 *
 * Antes no: el banco todavia no dice como termina el mes, y un boton de cerrar
 * todo el mes a la vista no hace mas que estorbar. Tres dias y no solo el
 * ultimo porque el sueldo, o las ganas de mirar el banco, llegan a veces un poco
 * antes; y si ese dia no se abre la app, el mes pasado sigue ofreciendolo.
 */
fun esHoraDeCerrar(
    periodo: PeriodoMensual,
    hoy: LocalDate,
): Boolean = hoy >= periodo.fin.minus(DIAS_PARA_CERRAR - 1, DateTimeUnit.DAY)

/**
 * Un mes cerrado, en una linea: para la lista de meses cerrados.
 *
 * @property diferencia lo real menos lo esperado, sumando todas las cuentas.
 *   Positivo: sobro. Negativo: se gasto de mas.
 * @property intocableBajo alguna cuenta intocable termino con menos de lo que
 *   empezo.
 */
data class MesCerrado(
    val mes: Mes,
    val diferencia: Money,
    val intocableBajo: Boolean,
)

/** Los meses cerrados, del mas reciente al mas antiguo, con su resultado. */
class ResumirCierresUseCase {
    operator fun invoke(
        cierres: List<CierreDeMes>,
        cuentas: List<Cuenta>,
        transacciones: List<Transaccion>,
        primerDiaDelMes: Int,
    ): List<MesCerrado> {
        val intocables = cuentas.filter { it.esIntocable }
        return cierres
            .sortedByDescending { it.mes }
            .map { cierre ->
                val inicio = PeriodoMensual(cierre.mes, primerDiaDelMes).inicio
                val alEmpezar = saldosAl(inicio.minus(1, DateTimeUnit.DAY), intocables, transacciones)
                MesCerrado(
                    mes = cierre.mes,
                    diferencia = cierre.saldos.map { it.diferencia }.sumar(),
                    intocableBajo =
                        intocables.any { cuenta ->
                            val saldo = cierre.saldoDe(cuenta.id) ?: return@any false
                            saldo.real < alEmpezar.getValue(cuenta.id)
                        },
                )
            }
    }
}
