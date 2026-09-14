package com.miplata.feature.cuentas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.usecase.CuentaConSaldo

@Composable
fun PantallaCuentas(
    modifier: Modifier = Modifier,
    viewModel: CuentasViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaCuentas(estado, viewModel::alEvento, modifier)
}

/**
 * La pantalla como funcion pura de su estado.
 *
 * Los saldos que muestra no estan guardados en ningun sitio: los deriva el
 * dominio de los movimientos (docs/03). Aqui solo se dibujan.
 */
@Composable
internal fun PantallaCuentas(
    estado: CuentasUiState,
    alEvento: (EventoDeCuentas) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Total(estado, dinero) }

            if (estado.estaVacio && !estado.cargando) {
                item { Aviso(stringResource(R.string.cuentas_vacio)) }
            }

            items(estado.cuentas, key = { it.cuenta.id.valor }) { conSaldo ->
                FilaDeCuenta(conSaldo, dinero) { alEvento(EventoDeCuentas.EditarCuenta(conSaldo.cuenta)) }
            }
        }

        FloatingActionButton(
            onClick = { alEvento(EventoDeCuentas.CrearCuenta) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cuentas_anadir))
        }
    }

    estado.editor?.let { Editor(it, alEvento) }
}

@Composable
private fun Total(
    estado: CuentasUiState,
    dinero: FormateadorDeDinero,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.cuentas_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = dinero.formatear(estado.total),
            style = EstilosDeDinero.destacado,
            color = colorDelSaldo(estado.total),
        )

        // Un total que se calla lo que deja fuera miente por omision.
        if (estado.cuentasEnOtraMoneda > 0) {
            Aviso(
                pluralStringResource(
                    R.plurals.cuentas_en_otra_moneda,
                    estado.cuentasEnOtraMoneda,
                    estado.cuentasEnOtraMoneda,
                ),
            )
        }
    }
}

@Composable
private fun FilaDeCuenta(
    conSaldo: CuentaConSaldo,
    dinero: FormateadorDeDinero,
    alPulsar: () -> Unit,
) {
    val cuenta = conSaldo.cuenta

    Card(onClick = alPulsar, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cuenta.nombre, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = etiquetaDe(cuenta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = dinero.formatear(conSaldo.saldo),
                style = EstilosDeDinero.enLista,
                color = colorDelSaldo(conSaldo.saldo),
            )
        }
    }
}

/**
 * El tipo de cuenta y, si las hay, las salvedades.
 *
 * Van juntas en una linea porque son la misma pregunta -"¿que es esta cuenta?"-
 * y separarlas en insignias sueltas llenaria la fila de ruido.
 */
@Composable
private fun etiquetaDe(cuenta: Cuenta): String =
    buildList {
        add(stringResource(cuenta.tipo.etiqueta()))
        if (cuenta.archivada) add(stringResource(R.string.cuentas_etiqueta_archivada))
        if (!cuenta.incluirEnTotal) add(stringResource(R.string.cuentas_etiqueta_fuera_del_total))
    }.joinToString(" · ")

@Composable
private fun Aviso(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun colorDelSaldo(saldo: Money): Color =
    if (saldo.esNegativo) MiPlataTheme.dinero.sobregiro else MiPlataTheme.dinero.ingreso

@Preview(showBackground = true)
@Composable
private fun PantallaCuentasPreview() {
    MiPlataTheme {
        PantallaCuentas(
            estado =
                CuentasUiState(
                    cuentas =
                        listOf(
                            conSaldo("cartera", "Cartera", TipoDeCuenta.EFECTIVO, 120),
                            conSaldo("banco", "Cuenta del banco", TipoDeCuenta.BANCARIA, 2340),
                            conSaldo("visa", "Visa", TipoDeCuenta.TARJETA_CREDITO, -430),
                        ),
                    total = Money.deUnidades(2030),
                    cargando = false,
                ),
            alEvento = {},
        )
    }
}

private fun conSaldo(
    id: String,
    nombre: String,
    tipo: TipoDeCuenta,
    saldo: Long,
) = CuentaConSaldo(
    cuenta =
        Cuenta(
            id = CuentaId(id),
            nombre = nombre,
            tipo = tipo,
            saldoInicial = Money.deUnidades(saldo),
            moneda = Moneda("USD"),
        ),
    saldo = Money.deUnidades(saldo),
)
