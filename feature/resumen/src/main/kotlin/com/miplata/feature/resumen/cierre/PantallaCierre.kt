package com.miplata.feature.resumen.cierre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.nombreDelMes
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.feature.resumen.R

@Composable
fun PantallaCierre(
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CierreViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaCierre(estado, viewModel::alEvento, alVolver, modifier)
}

/**
 * El cierre de mes como funcion pura de su estado (docs/adr/0007).
 *
 * Abierto, pide el saldo del banco de cada cuenta. Cerrado, ensena lo esperado
 * frente a lo real y deja reabrir.
 */
@Composable
internal fun PantallaCierre(
    estado: CierreUiState,
    alEvento: (EventoDelCierre) -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)
    var confirmandoReapertura by rememberSaveable { mutableStateOf(false) }

    PantallaConTituloGrande(
        titulo = stringResource(R.string.cierre_titulo),
        modifier = modifier,
        alVolver = alVolver,
    ) { relleno ->
        ContenidoDelCierre(estado, dinero, alEvento, relleno) { confirmandoReapertura = true }
    }

    if (confirmandoReapertura) {
        DialogoDeReapertura(
            estado = estado,
            alConfirmar = {
                confirmandoReapertura = false
                alEvento(EventoDelCierre.Reabrir)
            },
            alCancelar = { confirmandoReapertura = false },
        )
    }
}

@Composable
private fun ContenidoDelCierre(
    estado: CierreUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelCierre) -> Unit,
    relleno: PaddingValues,
    alPedirReapertura: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = relleno,
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        when {
            estado.cargando -> Unit
            estado.sinMetodo -> item { Explicacion(stringResource(R.string.cierre_sin_metodo)) }
            estado.cerrado -> {
                item { Explicacion(stringResource(R.string.cierre_cerrado, nombreDelMes(estado.mes))) }
                items(estado.filas, key = { it.cuenta.id.valor }) { CuentaCerrada(it, dinero) }
                item {
                    Centrado {
                        TextButton(onClick = alPedirReapertura, enabled = !estado.trabajando) {
                            Text(stringResource(R.string.cierre_reabrir))
                        }
                    }
                }
            }
            else -> {
                item { Explicacion(stringResource(R.string.cierre_instrucciones, nombreDelMes(estado.mes))) }
                items(estado.filas, key = { it.cuenta.id.valor }) { fila ->
                    CuentaPorCerrar(fila, estado.principal?.nombre.orEmpty(), dinero, alEvento)
                }
                item {
                    Centrado {
                        Button(onClick = { alEvento(EventoDelCierre.Cerrar) }, enabled = estado.puedeCerrar) {
                            Text(stringResource(R.string.cierre_cerrar))
                        }
                    }
                }
            }
        }
    }
}

/** Una cuenta con el mes abierto: lo esperado, el campo del banco y, si toca, la cobertura. */
@Composable
private fun CuentaPorCerrar(
    fila: FilaDeCierre,
    principal: String,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelCierre) -> Unit,
) {
    val analizador = recordarAnalizadorDeDinero()
    // El texto vive aqui mientras se escribe, como en los editores: "12," tiene
    // que poder escribirse aunque todavia no sea un importe.
    var tecleado by rememberSaveable(fila.cuenta.id.valor) { mutableStateOf(fila.real?.toString().orEmpty()) }

    GrupoDeLista(titulo = fila.cuenta.nombre, pie = pieDe(fila, dinero)) {
        Cifra(stringResource(R.string.cierre_esperado), dinero.formatear(fila.esperado), conSeparador = false)
        OutlinedTextField(
            value = tecleado,
            onValueChange = { texto ->
                tecleado = texto
                alEvento(EventoDelCierre.CambiarReal(fila.cuenta.id, analizador.parsear(texto)))
            },
            label = { Text(stringResource(R.string.cierre_saldo_banco)) },
            isError = fila.real == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = EstilosDeDinero.enLista,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m, vertical = Espacio.xs),
        )
        if (fila.puedeCubrir) {
            Cobertura(fila, principal) { alEvento(EventoDelCierre.CambiarCobertura(fila.cuenta.id, it)) }
        }
    }
}

@Composable
private fun Cobertura(
    fila: FilaDeCierre,
    principal: String,
    alCambiar: (Boolean) -> Unit,
) {
    val titulo = stringResource(R.string.cierre_cubre, principal)
    val detalle = stringResource(R.string.cierre_cubre_detalle, principal)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m, vertical = Espacio.xs),
    ) {
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$titulo. $detalle" }) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = fila.cubre, onCheckedChange = alCambiar)
    }
}

/** Una cuenta con el mes ya cerrado: lo esperado, lo real y la diferencia. */
@Composable
private fun CuentaCerrada(
    fila: FilaDeCierre,
    dinero: FormateadorDeDinero,
) {
    GrupoDeLista(titulo = fila.cuenta.nombre, pie = pieDe(fila, dinero)) {
        Cifra(stringResource(R.string.cierre_esperado), dinero.formatear(fila.esperado), conSeparador = false)
        Cifra(stringResource(R.string.cierre_real), fila.real?.let(dinero::formatear).orEmpty())
    }
}

/** "Sobro 7,00", "Gastaste 43,30 de mas" y, si baja una intocable, el aviso. */
@Composable
private fun pieDe(
    fila: FilaDeCierre,
    dinero: FormateadorDeDinero,
): String? {
    val diferencia = fila.diferencia ?: return null
    val resultado =
        when {
            diferencia.esPositivo -> stringResource(R.string.cierre_sobro, dinero.formatear(diferencia))
            diferencia.esNegativo -> stringResource(R.string.cierre_falto, dinero.formatear(diferencia.valorAbsoluto()))
            else -> stringResource(R.string.cierre_cuadra)
        }
    val real = fila.real ?: return resultado
    return if (fila.intocableBajo) {
        resultado + " " + stringResource(R.string.cierre_intocable_bajo, dinero.formatear(fila.empiezaCon - real))
    } else {
        resultado
    }
}

@Composable
private fun Cifra(
    titulo: String,
    valor: String,
    conSeparador: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    FilaDeLista(
        titulo = titulo,
        conSeparador = conSeparador,
        final = { Text(text = valor, style = EstilosDeDinero.enLista, color = color) },
    )
}

@Composable
private fun Explicacion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m),
    )
}

@Composable
private fun Centrado(contenido: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { contenido() }
}

/**
 * Reabrir borra movimientos, asi que se confirma. El aviso dice cuantos meses
 * posteriores caen con el, que es lo que no se adivina.
 */
@Composable
private fun DialogoDeReapertura(
    estado: CierreUiState,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
) {
    val aviso =
        buildString {
            append(stringResource(R.string.cierre_reabrir_aviso))
            if (estado.posterioresCerrados > 0) {
                append(" ")
                append(
                    pluralStringResource(
                        R.plurals.cierre_reabrir_posteriores,
                        estado.posterioresCerrados,
                        estado.posterioresCerrados,
                    ),
                )
            }
        }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(R.string.cierre_reabrir_titulo, nombreDelMes(estado.mes))) },
        text = { Text(aviso) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(stringResource(R.string.cierre_reabrir), color = MiPlataTheme.dinero.sobregiro)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(R.string.cierre_cancelar)) } },
    )
}
