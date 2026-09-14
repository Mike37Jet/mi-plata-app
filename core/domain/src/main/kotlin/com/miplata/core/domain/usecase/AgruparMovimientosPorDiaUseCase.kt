package com.miplata.core.domain.usecase

import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.sumar
import kotlinx.datetime.LocalDate

/**
 * Los movimientos de un dia y lo que sumaron entre todos.
 *
 * @param neto lo que entro menos lo que salio ese dia. Las transferencias no
 *   participan: el dinero cambio de cuenta, pero el dia no fue ni mejor ni peor.
 */
data class DiaConMovimientos(
    val fecha: LocalDate,
    val movimientos: List<Transaccion>,
    val neto: Money,
)

/**
 * Reparte los movimientos por dia, del mas reciente al mas antiguo.
 *
 * Un dia es la unidad en la que la gente recuerda lo que gasto -"el sabado se me
 * fue la mano"-, asi que es la unidad en la que conviene ensenarlo. El total del
 * dia responde de un vistazo a "¿que tal fue?" sin tener que sumar la columna
 * con el dedo.
 *
 * Lo mas reciente arriba porque lo que se acaba de anotar es lo que se quiere
 * ver, y porque asi el movimiento recien creado aparece donde esta mirando el
 * usuario en vez de al final de una lista larga.
 */
class AgruparMovimientosPorDiaUseCase {
    operator fun invoke(transacciones: List<Transaccion>): List<DiaConMovimientos> =
        transacciones
            .groupBy { it.fecha }
            .map { (fecha, delDia) ->
                DiaConMovimientos(
                    fecha = fecha,
                    // Dentro del dia tambien lo ultimo primero. El id desempata
                    // para que dos movimientos de la misma fecha no bailen de
                    // orden entre recomposiciones.
                    movimientos = delDia.sortedByDescending { it.id.valor },
                    neto = netoDe(delDia),
                )
            }.sortedByDescending { it.fecha }

    private fun netoDe(transacciones: List<Transaccion>): Money =
        transacciones
            .mapNotNull { transaccion ->
                when (transaccion.tipo) {
                    TipoDeTransaccion.INGRESO -> transaccion.monto
                    TipoDeTransaccion.GASTO -> -transaccion.monto
                    // Una transferencia sale de una cuenta y entra en otra: en el
                    // total del dia se anulan, asi que ni se cuenta.
                    TipoDeTransaccion.TRANSFERENCIA -> null
                }
            }.sumar()
}
