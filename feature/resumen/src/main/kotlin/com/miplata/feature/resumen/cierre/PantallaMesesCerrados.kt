package com.miplata.feature.resumen.cierre

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.nombreDelMes
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.usecase.MesCerrado
import com.miplata.feature.resumen.R

@Composable
fun PantallaMesesCerrados(
    alVolver: () -> Unit,
    alAbrirMes: (Mes) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MesesCerradosViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaMesesCerrados(estado, alVolver, alAbrirMes, modifier)
}

/**
 * Los meses cerrados, uno por linea: el mes y como termino.
 *
 * Solo el resultado, sin cifras de cada cuenta: eso esta un toque mas alla, en
 * el cierre del mes. Una lista con cinco numeros por fila no se lee de un
 * vistazo, y el vistazo es para lo que existe.
 */
@Composable
internal fun PantallaMesesCerrados(
    estado: MesesCerradosUiState,
    alVolver: () -> Unit,
    alAbrirMes: (Mes) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    PantallaConTituloGrande(
        titulo = stringResource(R.string.meses_cerrados_titulo),
        modifier = modifier,
        alVolver = alVolver,
    ) { relleno ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = relleno) {
            item {
                GrupoDeLista {
                    estado.meses.forEachIndexed { i, mes ->
                        FilaDeLista(
                            titulo = nombreDelMes(mes.mes),
                            detalle = resultadoDe(mes, dinero),
                            alPulsar = { alAbrirMes(mes.mes) },
                            conSeparador = i > 0,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resultadoDe(
    mes: MesCerrado,
    dinero: FormateadorDeDinero,
): String {
    val resultado =
        when {
            mes.diferencia.esPositivo -> stringResource(R.string.cierre_sobro, dinero.formatear(mes.diferencia))
            mes.diferencia.esNegativo ->
                stringResource(R.string.cierre_falto, dinero.formatear(mes.diferencia.valorAbsoluto()))
            else -> stringResource(R.string.cierre_cuadra)
        }
    return if (mes.intocableBajo) "$resultado · ${stringResource(R.string.meses_cerrados_intocable)}" else resultado
}
