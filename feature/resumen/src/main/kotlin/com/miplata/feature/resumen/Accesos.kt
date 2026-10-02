package com.miplata.feature.resumen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.nombreDelMes
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme

// El bloque de accesos del resumen y la vista por cuenta (docs/adr/0007).

/** A donde lleva cada fila del bloque de accesos; lo decide `:app`. */
internal class Accesos(
    val alAbrirCierre: () -> Unit,
    val alAbrirMovimientos: () -> Unit,
    val alAbrirMesesCerrados: () -> Unit,
    val alAbrirFueraDelPlan: () -> Unit,
    val alAbrirPlan: () -> Unit,
)

/**
 * Las puertas a lo que ya paso: el cierre del mes, los movimientos y el
 * historial de meses cerrados (docs/adr/0007).
 *
 * Movimientos esta aqui y no en la barra inferior: anotar es opcional, y quien
 * lo quiere lo tiene a un toque.
 */
@Composable
internal fun BloqueDeAccesos(
    estado: ResumenUiState,
    accesos: Accesos,
) {
    GrupoDeLista {
        // Cada fila solo cuando tiene algo que ofrecer: un bloque con opciones
        // que no llevan a nada abruma sin ayudar.
        val filas =
            buildList<@Composable (Boolean) -> Unit> {
                if (estado.cerrado == true || estado.ofrecerCierre) {
                    add { separada -> FilaDelCierre(estado, separada, accesos.alAbrirCierre) }
                }
                add { separada ->
                    FilaDeLista(
                        titulo = stringResource(R.string.resumen_movimientos),
                        detalle = stringResource(R.string.resumen_movimientos_detalle),
                        alPulsar = accesos.alAbrirMovimientos,
                        conSeparador = separada,
                    )
                }
                if (estado.hayMesesCerrados) {
                    add { separada ->
                        FilaDeLista(
                            titulo = stringResource(R.string.resumen_meses_cerrados),
                            alPulsar = accesos.alAbrirMesesCerrados,
                            conSeparador = separada,
                        )
                    }
                }
            }
        filas.forEachIndexed { i, fila -> fila(i > 0) }
    }
}

@Composable
private fun FilaDelCierre(
    estado: ResumenUiState,
    conSeparador: Boolean,
    alAbrir: () -> Unit,
) {
    val cerrado = estado.cerrado == true
    FilaDeLista(
        titulo =
            stringResource(
                if (cerrado) R.string.resumen_mes_cerrado else R.string.resumen_cerrar_mes,
                nombreDelMes(estado.mes),
            ),
        detalle = stringResource(if (cerrado) R.string.resumen_ver_cierre else R.string.resumen_cerrar_detalle),
        alPulsar = alAbrir,
        conSeparador = conSeparador,
    )
}

/**
 * Cada cuenta del metodo: con cuanto va y con cuanto deberia terminar.
 *
 * Es la pregunta que se hacia con la hoja de calculo al final del mes, sin
 * esperar al final: a mitad de mes ya se ve que sobre va justo.
 */
@Composable
internal fun PorCuenta(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
) {
    val detalle = if (estado.cerrado == true) R.string.resumen_termino_esperado else R.string.resumen_deberia_terminar
    GrupoDeLista(titulo = stringResource(R.string.resumen_por_cuenta)) {
        estado.porCuenta.forEachIndexed { i, cuenta ->
            FilaDeLista(
                titulo = cuenta.nombre,
                detalle = stringResource(detalle, dinero.formatear(cuenta.esperado)),
                final = {
                    Text(
                        text = dinero.formatear(cuenta.actual),
                        style = EstilosDeDinero.enLista,
                        color =
                            if (cuenta.actual.esNegativo) {
                                MiPlataTheme.dinero.sobregiro
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                },
                conSeparador = i > 0,
            )
        }
    }
}

/**
 * Lo que salio sin estar en el plan, que es donde se escapa la plata.
 *
 * Solo aparece si hay algo: un grupo en cero no dice nada. Cada fila, solo si
 * tiene importe. Las dos llevan a los mismos movimientos, los de fuera del
 * plan, para ver en que se fue.
 */
@Composable
internal fun FueraDelPlanUi(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alAbrir: () -> Unit,
) {
    val fuera = estado.fueraDelPlan
    GrupoDeLista(
        titulo = stringResource(R.string.resumen_fuera_del_plan),
        alLadoDelTitulo = {
            Text(
                text = dinero.formatear(fuera.total),
                style = EstilosDeDinero.secundario,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        val filas =
            listOfNotNull(
                (R.string.resumen_imprevistos to fuera.imprevistos).takeIf { fuera.imprevistos.esPositivo },
                (R.string.resumen_sin_detalle to fuera.sinDetalle).takeIf { fuera.sinDetalle.esPositivo },
            )
        filas.forEachIndexed { i, (titulo, monto) ->
            FilaDeLista(
                titulo = stringResource(titulo),
                final = {
                    Text(
                        text = dinero.formatear(monto),
                        style = EstilosDeDinero.enLista,
                        color = MiPlataTheme.dinero.gasto,
                    )
                },
                alPulsar = alAbrir,
                conSeparador = i > 0,
            )
        }
    }
}
