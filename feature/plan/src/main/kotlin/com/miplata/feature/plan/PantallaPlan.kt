package com.miplata.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.BotonDeAjustes
import com.miplata.core.designsystem.componentes.CifraPrincipal
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.componentes.SelectorDeMes
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea

@Composable
fun PantallaPlan(
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaPlan(estado, viewModel::alEvento, modifier, alAbrirAjustes)
}

/**
 * La pantalla como funcion pura de su estado.
 *
 * No conoce el ViewModel: recibe un estado y emite eventos. Asi se puede
 * previsualizar y testear sin montar medio grafo de dependencias (docs/01).
 */
@Composable
internal fun PantallaPlan(
    estado: PlanUiState,
    alEvento: (EventoDelPlan) -> Unit,
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)
    val avisos = remember { SnackbarHostState() }
    AvisoDeEliminacion(estado.eliminada, avisos, alEvento)

    PantallaConTituloGrande(
        titulo = stringResource(R.string.plan_titulo),
        modifier = modifier,
        acciones = { BotonDeAjustes(alAbrirAjustes) },
        // Un solo "+", en el mismo sitio que en las demas pestañas. Antes habia
        // uno por seccion: cuatro botones iguales compitiendo en la pantalla.
        botonFlotante = {
            FloatingActionButton(onClick = { alEvento(EventoDelPlan.NuevaLinea) }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plan_anadir))
            }
        },
        avisos = avisos,
    ) { relleno ->
        ContenidoDelPlan(estado, dinero, alEvento, relleno)
    }

    estado.editor?.let { EditorDeLineaUi(it, alEvento) }
}

/**
 * "«Comida» eliminada · Deshacer".
 *
 * Deshacer y no confirmar antes: borrar una linea es algo que se hace a
 * proposito casi siempre, y un dialogo de "¿seguro?" en cada una castiga al
 * que sabe lo que hace para proteger al que se equivoca. Aqui el que se
 * equivoca tiene unos segundos para arreglarlo, y el resto no espera.
 */
@Composable
private fun AvisoDeEliminacion(
    eliminada: LineaDePlan?,
    avisos: SnackbarHostState,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val mensaje =
        eliminada?.let {
            if (it.nombre.isBlank()) {
                stringResource(R.string.plan_eliminada_sin_nombre)
            } else {
                stringResource(R.string.plan_eliminada, it.nombre)
            }
        }
    val deshacer = stringResource(R.string.plan_deshacer)
    LaunchedEffect(eliminada) {
        if (eliminada == null || mensaje == null) return@LaunchedEffect
        val resultado = avisos.showSnackbar(mensaje, actionLabel = deshacer, duration = SnackbarDuration.Long)
        alEvento(
            if (resultado == SnackbarResult.ActionPerformed) {
                EventoDelPlan.DeshacerEliminacion
            } else {
                EventoDelPlan.OlvidarEliminacion
            },
        )
    }
}

@Composable
private fun ContenidoDelPlan(
    estado: PlanUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
    relleno: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = relleno,
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item { Cabecera(estado, dinero, alEvento) }

        if (estado.esBorrador && !estado.estaVacio) {
            item { Aviso(stringResource(R.string.plan_borrador)) }
        }

        if (estado.estaVacio && !estado.cargando) {
            item { Aviso(stringResource(R.string.plan_vacio)) }
        }

        // Solo los tipos que tienen lineas: una seccion vacia era una cabecera,
        // un total en cero y un boton, sin nada que decir.
        items(estado.secciones.filter { it.lineas.isNotEmpty() }, key = { it.tipo.name }) { seccion ->
            GrupoDeTipo(seccion, dinero, alEvento)
        }
    }
}

@Composable
private fun Cabecera(
    estado: PlanUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SelectorDeMes(
            mes = estado.mes,
            alAnterior = { alEvento(EventoDelPlan.MesAnterior) },
            alSiguiente = { alEvento(EventoDelPlan.MesSiguiente) },
        )

        Text(
            text =
                stringResource(
                    if (estado.enSobregiro) R.string.plan_sobregiro else R.string.plan_disponible,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CifraPrincipal(
            texto = dinero.formatear(estado.disponible.valorAbsoluto()),
            color =
                if (estado.enSobregiro) MiPlataTheme.dinero.sobregiro else MiPlataTheme.dinero.ingreso,
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.xs),
    )
}

/** Las lineas de un tipo en un grupo, con su total junto al titulo. */
@Composable
private fun GrupoDeTipo(
    seccion: SeccionDelPlan,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    GrupoDeLista(
        titulo = stringResource(seccion.tipo.etiqueta()),
        alLadoDelTitulo = {
            Text(
                text = dinero.formatear(seccion.total),
                style = EstilosDeDinero.secundario,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        seccion.lineas.forEachIndexed { i, linea ->
            // La clave ata el estado del deslizamiento a SU linea. Sin ella,
            // Compose lo reutilizaba por posicion: al borrar una linea, la
            // siguiente ocupaba su sitio, heredaba el estado de "deslizada" y se
            // quedaba mostrando el fondo rojo.
            key(linea.id.valor) {
                FilaDeLinea(linea, dinero, conSeparador = i > 0, alEvento)
            }
        }
    }
}

/**
 * Una linea del plan: el nombre a la izquierda y el importe, con su moneda, a
 * la derecha. Tocarla abre la hoja para editarla.
 *
 * Antes cada fila era un formulario -dos campos, un interruptor y una
 * papelera-: parecia texto con una raya debajo y no decia que se pudiera
 * tocar, y la papelera siempre a mano invitaba al toque accidental (docs/10).
 *
 * Se borra deslizando hacia la izquierda, con "Deshacer". Deslizar no se
 * descubre ni se puede hacer con un lector de pantalla, asi que tambien esta
 * como accion de accesibilidad y en la hoja.
 */
@Composable
private fun FilaDeLinea(
    linea: LineaDePlan,
    dinero: FormateadorDeDinero,
    conSeparador: Boolean,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val eliminar = stringResource(R.string.plan_eliminar)
    val deslizamiento =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { destino ->
                if (destino == SwipeToDismissBoxValue.EndToStart) {
                    alEvento(EventoDelPlan.EliminarLinea(linea.id))
                    true
                } else {
                    false
                }
            },
        )

    SwipeToDismissBox(
        state = deslizamiento,
        enableDismissFromStartToEnd = false,
        backgroundContent = { FondoDeEliminar() },
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(eliminar) {
                            alEvento(EventoDelPlan.EliminarLinea(linea.id))
                            true
                        },
                    )
            },
    ) {
        // El fondo de la fila tapa el rojo de detras mientras no se desliza.
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            FilaDeLista(
                titulo = linea.nombre.ifBlank { stringResource(R.string.plan_nombre_vacio) },
                // Una linea desactivada sigue en el plan pero no cuenta este mes:
                // se dice, en vez de un interruptor que habia que interpretar.
                detalle = if (linea.activa) null else stringResource(R.string.plan_este_mes_no),
                final = {
                    Text(
                        text = dinero.formatear(linea.montoPlanificado),
                        style = EstilosDeDinero.enLista,
                        color = if (linea.activa) linea.tipo.color() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                alPulsar = { alEvento(EventoDelPlan.EditarLinea(linea)) },
                conChevron = false,
                conSeparador = conSeparador,
            )
        }
    }
}

@Composable
private fun FondoDeEliminar() {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = Espacio.m),
    ) {
        Icon(
            Icons.Outlined.DeleteOutline,
            // Lo dice la accion de accesibilidad de la fila.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}

@Composable
internal fun TipoDeLinea.color(): Color =
    when (this) {
        TipoDeLinea.INGRESO -> MiPlataTheme.dinero.ingreso
        TipoDeLinea.AHORRO -> MiPlataTheme.dinero.ahorro
        else -> MiPlataTheme.dinero.gasto
    }

internal fun TipoDeLinea.etiqueta(): Int =
    when (this) {
        TipoDeLinea.INGRESO -> R.string.plan_tipo_ingreso
        TipoDeLinea.GASTO_FIJO -> R.string.plan_tipo_gasto_fijo
        TipoDeLinea.GASTO_VARIABLE -> R.string.plan_tipo_gasto_variable
        TipoDeLinea.AHORRO -> R.string.plan_tipo_ahorro
    }

@Preview(showBackground = true)
@Composable
private fun PantallaPlanPreview() {
    MiPlataTheme {
        PantallaPlan(
            estado =
                PlanUiState(
                    mes = Mes.de(2026, 3),
                    secciones =
                        listOf(
                            SeccionDelPlan(
                                TipoDeLinea.INGRESO,
                                listOf(ejemplo("sueldo", "Sueldo", TipoDeLinea.INGRESO, 2000)),
                                Money.deUnidades(2000),
                            ),
                            SeccionDelPlan(
                                TipoDeLinea.GASTO_FIJO,
                                listOf(ejemplo("arriendo", "Arriendo", TipoDeLinea.GASTO_FIJO, 450)),
                                Money.deUnidades(450),
                            ),
                        ),
                    ingresos = Money.deUnidades(2000),
                    salidas = Money.deUnidades(450),
                    cargando = false,
                ),
            alEvento = {},
        )
    }
}

private fun ejemplo(
    id: String,
    nombre: String,
    tipo: TipoDeLinea,
    monto: Long,
) = LineaDePlan(
    id = LineaId(id),
    nombre = nombre,
    tipo = tipo,
    montoPlanificado = Money.deUnidades(monto),
)
