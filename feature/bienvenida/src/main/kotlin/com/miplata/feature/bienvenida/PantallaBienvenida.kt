package com.miplata.feature.bienvenida

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.theme.MiPlataTheme

/**
 * La primera vez que se abre la app.
 *
 * @param alTenerCopia quien ya tiene una copia de seguridad no tiene que
 *   rellenar nada: restaura y entra con sus datos. A donde se va lo decide
 *   `:app`, porque este feature no puede depender del de la copia (docs/04).
 * @param alTerminar el usuario pulso el ultimo boton. Se avisa **antes** de
 *   guardar, para que `:app` sepa a que pantalla llevarle cuando la bienvenida
 *   se cierre sola al existir la cuenta.
 */
@Composable
fun PantallaBienvenida(
    alTenerCopia: () -> Unit,
    alTerminar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BienvenidaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaBienvenida(estado, viewModel::alEvento, alTenerCopia, alTerminar, modifier)
}

/** La pantalla como funcion pura de su estado (docs/01). */
@Composable
internal fun PantallaBienvenida(
    estado: BienvenidaUiState,
    alEvento: (EventoDeBienvenida) -> Unit,
    alTenerCopia: () -> Unit,
    alTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // "Atras" del sistema retrocede un paso en vez de cerrar la app con todo lo
    // que se lleva escrito.
    BackHandler(enabled = estado.paso != PasoDeBienvenida.BIENVENIDA) {
        alEvento(EventoDeBienvenida.Atras)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        if (estado.paso != PasoDeBienvenida.BIENVENIDA) {
            Text(
                text = stringResource(R.string.bienvenida_paso, estado.paso.ordinal, PASOS_CON_PREGUNTAS),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (estado.paso) {
            PasoDeBienvenida.BIENVENIDA -> PasoInicial(alEvento, alTenerCopia)
            PasoDeBienvenida.TU_MES -> PasoTuMes(estado, alEvento)
            PasoDeBienvenida.PRIMERA_CUENTA -> PasoPrimeraCuenta(estado, alEvento)
            PasoDeBienvenida.SOBRES -> PasoSobres(estado, alEvento)
            PasoDeBienvenida.PRIMER_INGRESO -> PasoPrimerIngreso(estado, alEvento, alTerminar)
        }
    }
}

/** Los pasos que preguntan algo; el inicial no cuenta. */
private val PASOS_CON_PREGUNTAS = PasoDeBienvenida.entries.size - 1

@Composable
internal fun Titulo(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineSmall,
        // Un lector de pantalla salta de titulo en titulo: asi se sabe en que
        // paso se esta sin leer la pantalla entera.
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun Explicacion(texto: String) {
    Text(text = texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Atras y Siguiente, al pie de los pasos intermedios. */
@Composable
internal fun Navegacion(
    estado: BienvenidaUiState,
    alEvento: (EventoDeBienvenida) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { alEvento(EventoDeBienvenida.Atras) }) {
            Text(stringResource(R.string.bienvenida_atras))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { alEvento(EventoDeBienvenida.Siguiente) }, enabled = estado.puedeSeguir) {
            Text(stringResource(R.string.bienvenida_siguiente))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasoTuMesPreview() {
    MiPlataTheme(colorDinamico = false) {
        PantallaBienvenida(estadoDeEjemplo(PasoDeBienvenida.TU_MES), {}, {}, {})
    }
}
