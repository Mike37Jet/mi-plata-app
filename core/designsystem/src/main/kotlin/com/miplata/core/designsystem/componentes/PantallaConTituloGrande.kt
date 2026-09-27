package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.R
import com.miplata.core.designsystem.theme.Espacio

/**
 * Lo que tapa la barra inferior de cristal por abajo.
 *
 * El contenido pasa por **detras** de la barra -si no, no habria nada que
 * desenfocar-, asi que cada pantalla tiene que dejar ese hueco al final de su
 * lista y subir su boton flotante por encima. `:app` lo pone; las pantallas lo
 * leen a traves de [PantallaConTituloGrande].
 */
val LocalEspacioDeLaBarraInferior: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/**
 * El armazon de una pestaña: titulo grande arriba, que se encoge al hacer
 * scroll, como los de iOS.
 *
 * El titulo dice en que pantalla se esta. Antes no habia: Resumen y Plan
 * empezaban igual -el mes, una cifra verde grande y una etiqueta- y no se
 * distinguia en cual estabas (Nielsen, consistencia; docs/10).
 *
 * @param alVolver si la pantalla no es una pestaña sino algo a lo que se llega
 *   desde una, la flecha para volver. El gesto de "atras" del sistema tambien
 *   funciona; la flecha es la pista visible de que se puede.
 * @param avisos para los avisos que suben desde abajo -"Linea eliminada ·
 *   Deshacer"-, que salen por encima de la barra de cristal y no detras.
 * @param contenido recibe el relleno que tiene que aplicar a su lista: los
 *   margenes laterales de la escala aurea, el hueco del titulo arriba y el de
 *   la barra inferior y el boton flotante abajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConTituloGrande(
    titulo: String,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {},
    botonFlotante: @Composable () -> Unit = {},
    avisos: SnackbarHostState? = null,
    contenido: @Composable (relleno: PaddingValues) -> Unit,
) {
    val comportamiento = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val barraInferior = LocalEspacioDeLaBarraInferior.current
    val fondo = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = modifier.nestedScroll(comportamiento.nestedScrollConnection),
        containerColor = fondo,
        topBar = {
            LargeTopAppBar(
                title = { Text(titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.designsystem_volver),
                            )
                        }
                    }
                },
                actions = acciones,
                // Mas bajo que el de Material (152dp): el titulo grande de iOS
                // va pegado a la barra de estado, y el de Material dejaba casi un
                // quinto de pantalla vacio encima. Plegado mide un paso de la
                // escala aurea; desplegado, dos pasos seguidos (55 + 55).
                collapsedHeight = Espacio.xl,
                expandedHeight = Espacio.xl + Espacio.xl,
                scrollBehavior = comportamiento,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = fondo, scrolledContainerColor = fondo),
            )
        },
        floatingActionButton = { Box(modifier = Modifier.padding(bottom = barraInferior)) { botonFlotante() } },
        snackbarHost = {
            if (avisos != null) SnackbarHost(avisos, modifier = Modifier.padding(bottom = barraInferior))
        },
    ) { interior ->
        contenido(
            PaddingValues(
                start = Espacio.m,
                end = Espacio.m,
                top = interior.calculateTopPadding(),
                // La barra, mas sitio para que la ultima fila pueda subir por
                // encima del boton flotante en vez de quedarse debajo.
                bottom = barraInferior + Espacio.xxl,
            ),
        )
    }
}
