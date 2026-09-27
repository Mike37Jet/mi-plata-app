package com.miplata.feature.transacciones

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.IconoEnCirculo
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.fechaLarga
import com.miplata.core.designsystem.formato.fechaRelativa
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import kotlinx.datetime.LocalDate

// La lista de un mes: cada dia en su grupo, cada movimiento en su fila.

/**
 * Los movimientos de un dia en un solo grupo, con el dia y su total encima.
 *
 * Un dia es la unidad en la que la gente recuerda lo que gasto -"el sabado se me
 * fue la mano"-, y el total responde a "¿que tal fue?" sin sumar la columna.
 *
 * Antes cada movimiento era una tarjeta: el marco pesaba mas que el dato, y los
 * dias se separaban con una linea que se perdia entre tarjetas (docs/10).
 */
@Composable
internal fun GrupoDelDia(
    dia: DiaEnLista,
    hoy: LocalDate?,
    dinero: FormateadorDeDinero,
    alPulsar: (MovimientoEnLista) -> Unit,
) {
    GrupoDeLista(
        titulo = hoy?.let { fechaRelativa(dia.fecha, it) } ?: fechaLarga(dia.fecha),
        alLadoDelTitulo =
            if (dia.tieneTotal) {
                {
                    Text(
                        text = dinero.formatearConSigno(dia.neto),
                        style = EstilosDeDinero.secundario,
                        // Verde solo si el dia fue a mas. Un dia que quedo en
                        // cero no es un dia de ingresos.
                        color =
                            if (dia.neto.esPositivo) {
                                MiPlataTheme.dinero.ingreso
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            } else {
                null
            },
        modifier = Modifier.padding(top = Espacio.s),
    ) {
        dia.movimientos.forEachIndexed { i, movimiento ->
            FilaDeMovimiento(movimiento, dinero, conSeparador = i > 0) { alPulsar(movimiento) }
        }
    }
}

@Composable
private fun FilaDeMovimiento(
    movimiento: MovimientoEnLista,
    dinero: FormateadorDeDinero,
    conSeparador: Boolean,
    alPulsar: () -> Unit,
) {
    FilaDeLista(
        titulo = tituloDe(movimiento),
        detalle = detalleDe(movimiento).takeIf { it.isNotBlank() },
        inicio = { IconoDelTipo(movimiento.tipo) },
        final = {
            Text(
                text = montoDe(movimiento, dinero),
                style = EstilosDeDinero.enLista,
                color = colorDe(movimiento.tipo),
            )
        },
        alPulsar = alPulsar,
        // Todas las filas se tocan y llevan al mismo sitio, el editor: un
        // chevron en cada una seria ruido.
        conChevron = false,
        conSeparador = conSeparador,
    )
}

/**
 * El tipo de movimiento de un vistazo: lo que entra, lo que sale y lo que solo
 * cambia de sitio, en un circulo de 34dp -un paso de la escala aurea- tintado
 * con su color.
 */
@Composable
private fun IconoDelTipo(tipo: TipoDeTransaccion) {
    IconoEnCirculo(
        icono =
            when (tipo) {
                TipoDeTransaccion.INGRESO -> Icons.Outlined.ArrowDownward
                TipoDeTransaccion.GASTO -> Icons.Outlined.ArrowUpward
                TipoDeTransaccion.TRANSFERENCIA -> Icons.Outlined.SwapHoriz
            },
        color = colorDe(tipo),
    )
}

/**
 * Lo que mejor identifica el movimiento, de lo mas concreto a lo mas generico.
 *
 * La nota gana porque es lo que el usuario escribio a mano: si se molesto en
 * poner "cena con Ana", eso es lo que quiere leer, no "Comida".
 */
@Composable
private fun tituloDe(movimiento: MovimientoEnLista): String =
    movimiento.nota
        ?: if (movimiento.tipo == TipoDeTransaccion.TRANSFERENCIA) {
            // Una transferencia no tiene categoria, y por eso salia como "Sin
            // categoria". Lo que la identifica es de donde a donde fue.
            entreCuentas(movimiento)
        } else {
            movimiento.lineaDePlan
                ?: movimiento.categoria
                ?: stringResource(R.string.transacciones_sin_categoria)
        }

@Composable
private fun entreCuentas(movimiento: MovimientoEnLista): String =
    stringResource(R.string.transacciones_transferencia_entre, movimiento.cuenta, movimiento.cuentaDestino.orEmpty())

@Composable
private fun detalleDe(movimiento: MovimientoEnLista): String =
    if (movimiento.tipo == TipoDeTransaccion.TRANSFERENCIA) {
        // Si el titulo es la nota, las cuentas bajan al detalle: sin ellas, una
        // transferencia no cuenta lo unico que importa, a donde fue el dinero.
        if (movimiento.nota != null) entreCuentas(movimiento) else stringResource(R.string.transacciones_transferencia)
    } else {
        buildList {
            add(movimiento.cuenta)
            movimiento.categoria?.takeIf { it != tituloDe(movimiento) }?.let { add(it) }
        }.filter { it.isNotBlank() }
            .joinToString(" · ")
    }

/**
 * Lo que se enseña a la derecha. Con signo -en una lista de movimientos el
 * signo es la informacion-, salvo en una transferencia: el dinero no se gano ni
 * se perdio, solo cambio de cuenta, y un "-€100" lo leia como un gasto.
 */
private fun montoDe(
    movimiento: MovimientoEnLista,
    dinero: FormateadorDeDinero,
): String =
    if (movimiento.tipo == TipoDeTransaccion.TRANSFERENCIA) {
        dinero.formatear(movimiento.monto)
    } else {
        dinero.formatearConSigno(conSigno(movimiento))
    }

/** El monto con el signo que le toca; el modelo lo guarda siempre positivo. */
private fun conSigno(movimiento: MovimientoEnLista): Money =
    when (movimiento.tipo) {
        TipoDeTransaccion.INGRESO -> movimiento.monto
        else -> -movimiento.monto
    }

@Composable
private fun colorDe(tipo: TipoDeTransaccion): Color =
    when (tipo) {
        TipoDeTransaccion.INGRESO -> MiPlataTheme.dinero.ingreso
        // Una transferencia no es un gasto: no deberia leerse como dinero
        // perdido, solo como dinero movido.
        TipoDeTransaccion.TRANSFERENCIA -> MaterialTheme.colorScheme.onSurfaceVariant
        TipoDeTransaccion.GASTO -> MiPlataTheme.dinero.gasto
    }
