package com.miplata.feature.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.IconoEnCirculo
import com.miplata.core.designsystem.componentes.SelectorDeMes
import com.miplata.core.designsystem.componentes.icono
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.nombreDelMes
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme

/**
 * El mes con el metodo de las cuentas (docs/adr/0007): que toca hacer ahora y
 * cuanto le queda a cada cuenta.
 *
 * Una pregunta por bloque, y en el orden en que se hacen: primero que hacer,
 * despues como van las cuentas, despues lo que se escapo. Lo demas -los
 * movimientos, los meses anteriores- queda abajo, a un toque, sin cifras que
 * compitan con las de arriba.
 */
@Composable
internal fun ContenidoDelMes(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelResumen) -> Unit,
    relleno: PaddingValues,
    accesos: Accesos,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = relleno,
        verticalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        item { CabeceraDelMes(estado, alEvento) }
        if (estado.paso != PasoDelMes.NINGUNO) {
            item { TarjetaDelPaso(estado, dinero, alEvento, accesos) }
        }
        if (estado.porCuenta.isNotEmpty()) {
            item { TusCuentas(estado, dinero) }
        }
        if (estado.fueraDelPlan.hayAlgo) {
            item { FueraDelPlanEnUnaLinea(estado, dinero, accesos.alAbrirFueraDelPlan) }
        }
        item { MasDelMes(estado, accesos) }
    }
}

@Composable
private fun CabeceraDelMes(
    estado: ResumenUiState,
    alEvento: (EventoDelResumen) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SelectorDeMes(
            mes = estado.mes,
            alAnterior = { alEvento(EventoDelResumen.MesAnterior) },
            alSiguiente = { alEvento(EventoDelResumen.MesSiguiente) },
        )
        estado.dia?.let { dia ->
            Text(
                text = stringResource(R.string.mes_dia_de, dia, estado.diasDelMes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lo unico que toca hacer ahora, con su boton. */
@Composable
private fun TarjetaDelPaso(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelResumen) -> Unit,
    accesos: Accesos,
) {
    val mes = nombreDelMes(estado.mes)
    when (estado.paso) {
        PasoDelMes.PLANEAR ->
            Paso(
                titulo = stringResource(R.string.paso_planear_titulo, mes),
                texto = stringResource(R.string.paso_planear_texto),
                boton = stringResource(R.string.paso_planear_boton),
                alPulsar = accesos.alAbrirPlan,
            )
        PasoDelMes.REPARTIR ->
            Paso(
                titulo = stringResource(R.string.paso_repartir_titulo),
                texto = stringResource(R.string.paso_repartir_texto),
                boton = stringResource(R.string.paso_repartir_boton),
                alPulsar = { alEvento(EventoDelResumen.YaRepartí) },
            ) {
                estado.partesPendientes.forEach { parte ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(parte.cuenta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(dinero.formatear(parte.monto), style = EstilosDeDinero.enLista)
                    }
                }
            }
        PasoDelMes.COMPARAR ->
            Paso(
                titulo = stringResource(R.string.paso_comparar_titulo),
                texto = stringResource(R.string.paso_comparar_texto),
                boton = stringResource(R.string.paso_comparar_boton),
                alPulsar = accesos.alAbrirCierre,
            )
        PasoDelMes.CERRADO ->
            GrupoDeLista {
                FilaDeLista(
                    inicio = { IconoEnCirculo(Icons.Outlined.CheckCircle, MiPlataTheme.dinero.ingreso) },
                    titulo = stringResource(R.string.paso_cerrado_titulo),
                    detalle = stringResource(R.string.paso_cerrado_detalle),
                    alPulsar = accesos.alAbrirCierre,
                )
            }
        PasoDelMes.NINGUNO -> Unit
    }
}

@Composable
private fun Paso(
    titulo: String,
    texto: String,
    boton: String,
    alPulsar: () -> Unit,
    detalle: @Composable () -> Unit = {},
) {
    GrupoDeLista {
        Column(
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            modifier = Modifier.fillMaxWidth().padding(Espacio.m),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detalle()
            Button(onClick = alPulsar, modifier = Modifier.padding(top = Espacio.xs)) { Text(boton) }
        }
    }
}

/**
 * Cada cuenta en una fila: cuanto tiene hoy y cuanto le quedara al final si se
 * sigue el plan. Nada mas: el desglose esta en el Plan.
 */
@Composable
private fun TusCuentas(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
) {
    val cerrado = estado.cerrado == true
    GrupoDeLista(titulo = stringResource(R.string.mes_tus_cuentas)) {
        estado.porCuenta.forEachIndexed { i, cuenta ->
            FilaDeLista(
                titulo = cuenta.nombre,
                // El candado va donde las demas llevan su tipo: alineado, no
                // suelto en medio de la fila.
                inicio = {
                    IconoEnCirculo(
                        if (cuenta.intocable) Icons.Outlined.Lock else cuenta.tipo.icono(),
                        MaterialTheme.colorScheme.primary,
                    )
                },
                detalle =
                    stringResource(
                        if (cerrado) R.string.mes_esperabas else R.string.mes_al_final_te_queda,
                        dinero.formatear(cuenta.esperado),
                    ),
                final = {
                    Column(horizontalAlignment = Alignment.End) {
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
                        Text(
                            text = stringResource(if (cerrado) R.string.mes_en_el_banco else R.string.mes_hoy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                conSeparador = i > 0,
            )
        }
    }
}

/** Lo que se escapo del plan, en una sola fila: el detalle esta al tocarla. */
@Composable
private fun FueraDelPlanEnUnaLinea(
    estado: ResumenUiState,
    dinero: FormateadorDeDinero,
    alAbrir: () -> Unit,
) {
    val fuera = estado.fueraDelPlan
    val partes =
        listOfNotNull(
            stringResource(
                R.string.mes_anotado,
                dinero.formatear(fuera.imprevistos),
            ).takeIf { fuera.imprevistos.esPositivo },
            stringResource(
                R.string.mes_sin_anotar,
                dinero.formatear(fuera.sinDetalle),
            ).takeIf { fuera.sinDetalle.esPositivo },
        )
    GrupoDeLista {
        FilaDeLista(
            titulo = stringResource(R.string.mes_fuera_del_plan),
            detalle = partes.joinToString(" · "),
            final = {
                Text(dinero.formatear(fuera.total), style = EstilosDeDinero.enLista, color = MiPlataTheme.dinero.gasto)
            },
            alPulsar = alAbrir,
        )
    }
}

@Composable
private fun MasDelMes(
    estado: ResumenUiState,
    accesos: Accesos,
) {
    GrupoDeLista {
        FilaDeLista(titulo = stringResource(R.string.resumen_movimientos), alPulsar = accesos.alAbrirMovimientos)
        if (estado.hayMesesCerrados) {
            FilaDeLista(
                titulo = stringResource(R.string.mes_meses_anteriores),
                alPulsar = accesos.alAbrirMesesCerrados,
                conSeparador = true,
            )
        }
    }
}
