package com.miplata.feature.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.FilaDeOpcion
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.formato.fechaLarga
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Tema

/**
 * Los ajustes de la app.
 *
 * @param alAbrirCopia la copia de seguridad vive en otro feature; a donde se va
 *   lo decide `:app` (docs/04).
 */
@Composable
fun PantallaAjustes(
    alVolver: () -> Unit,
    alAbrirCopia: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaAjustes(estado, viewModel::alEvento, alVolver, alAbrirCopia, modifier)
}

@Composable
internal fun PantallaAjustes(
    estado: AjustesUiState,
    alEvento: (EventoDeAjustes) -> Unit,
    alVolver: () -> Unit,
    alAbrirCopia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PantallaConTituloGrande(
        titulo = stringResource(R.string.ajustes_titulo),
        modifier = modifier,
        alVolver = alVolver,
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = relleno,
            verticalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            item { Apariencia(estado.tema, alEvento) }
            item { TusDatos(estado, alAbrirCopia) }
        }
    }
}

/**
 * Segun el telefono, claro u oscuro. El primero es el de por defecto: la
 * mayoria quiere que la app haga lo mismo que el resto del telefono, y quien no
 * lo quiere lo cambia aqui.
 */
@Composable
private fun Apariencia(
    elegido: Tema,
    alEvento: (EventoDeAjustes) -> Unit,
) {
    GrupoDeLista(
        titulo = stringResource(R.string.ajustes_apariencia),
        pie = stringResource(R.string.ajustes_apariencia_pie),
        modifier = Modifier.selectableGroup(),
    ) {
        OPCIONES_DE_TEMA.forEachIndexed { i, (tema, nombre) ->
            FilaDeOpcion(
                titulo = stringResource(nombre),
                elegida = tema == elegido,
                alElegir = { alEvento(EventoDeAjustes.ElegirTema(tema)) },
                conSeparador = i > 0,
            )
        }
    }
}

private val OPCIONES_DE_TEMA =
    listOf(
        Tema.SEGUN_EL_SISTEMA to R.string.ajustes_tema_sistema,
        Tema.CLARO to R.string.ajustes_tema_claro,
        Tema.OSCURO to R.string.ajustes_tema_oscuro,
    )

/**
 * La copia de seguridad, con cuando se hizo la ultima.
 *
 * Antes estaba como texto en una esquina de Cuentas: una funcion critica dentro
 * de una pestaña que no tiene nada que ver con ella (docs/10). Aqui esta donde
 * se busca, y dice de un vistazo si hace falta hacer una.
 */
@Composable
private fun TusDatos(
    estado: AjustesUiState,
    alAbrirCopia: () -> Unit,
) {
    GrupoDeLista(titulo = stringResource(R.string.ajustes_datos)) {
        FilaDeLista(
            titulo = stringResource(R.string.ajustes_copia),
            detalle =
                estado.ultimaCopia?.let { stringResource(R.string.ajustes_copia_ultima, fechaLarga(it)) }
                    ?: stringResource(R.string.ajustes_copia_nunca),
            alPulsar = alAbrirCopia,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PantallaAjustesPreview() {
    MiPlataTheme {
        PantallaAjustes(AjustesUiState(tema = Tema.OSCURO), {}, {}, {})
    }
}
