package com.miplata.feature.transacciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.theme.MiPlataTheme

/**
 * Marcador de posicion.
 *
 * La navegacion se monta antes que las pantallas a proposito: asi el esqueleto
 * -rutas, barra inferior, estado al rotar- se prueba una vez y en vacio, en vez
 * de depurarlo mezclado con la primera pantalla que tenga logica de verdad.
 */
@Composable
fun PantallaTransacciones(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.transacciones_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.transacciones_pendiente),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaTransaccionesPreview() {
    MiPlataTheme {
        PantallaTransacciones()
    }
}
