package com.miplata.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
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
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaPlan(estado, viewModel::alEvento, modifier)
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
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Cabecera(estado, dinero, alEvento)
        }

        if (estado.esBorrador && !estado.estaVacio) {
            item { Aviso(stringResource(R.string.plan_borrador)) }
        }

        if (estado.estaVacio && !estado.cargando) {
            item { Aviso(stringResource(R.string.plan_vacio)) }
        }

        estado.secciones.forEach { seccion ->
            item(key = "cabecera-${seccion.tipo}") {
                CabeceraDeSeccion(seccion, dinero, alEvento)
            }

            items(seccion.lineas, key = { it.id.valor }) { linea ->
                FilaDeLinea(linea, alEvento)
            }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { alEvento(EventoDelPlan.MesAnterior) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.plan_mes_anterior),
                )
            }
            Text(
                text = estado.mes.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(120.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { alEvento(EventoDelPlan.MesSiguiente) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.plan_mes_siguiente),
                )
            }
        }

        Text(
            text =
                stringResource(
                    if (estado.enSobregiro) R.string.plan_sobregiro else R.string.plan_disponible,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = dinero.formatear(estado.disponible.valorAbsoluto()),
            style = EstilosDeDinero.destacado,
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun CabeceraDeSeccion(
    seccion: SeccionDelPlan,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(seccion.tipo.etiqueta()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = dinero.formatear(seccion.total),
                style = EstilosDeDinero.enLista,
                color = seccion.tipo.color(),
            )
            TextButton(onClick = { alEvento(EventoDelPlan.AnadirLinea(seccion.tipo)) }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.plan_anadir))
            }
        }
    }
}

/**
 * Una linea editable directamente, sin dialogos.
 *
 * El texto de cada campo vive en la propia fila mientras se escribe, y solo el
 * valor ya interpretado sube al ViewModel. Si el estado fuera la unica fuente,
 * escribir "12," se convertiria en "12" al instante y el usuario no podria
 * teclear el segundo decimal.
 */
@Composable
private fun FilaDeLinea(
    linea: LineaDePlan,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val analizador = recordarAnalizadorDeDinero()
    var montoTecleado by rememberSaveable(linea.id) {
        mutableStateOf(textoInicial(linea.montoPlanificado))
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = linea.nombre,
            onValueChange = { alEvento(EventoDelPlan.CambiarNombre(linea, it)) },
            placeholder = { Text(stringResource(R.string.plan_nombre_vacio)) },
            singleLine = true,
            colors = camposSinFondo(),
            modifier = Modifier.weight(1f),
        )
        TextField(
            value = montoTecleado,
            onValueChange = { texto ->
                montoTecleado = texto
                analizador.parsear(texto)?.let { alEvento(EventoDelPlan.CambiarMonto(linea, it)) }
            },
            // Un cero de marcador: sin el, el campo vacio no se distingue del de
            // al lado y no hay forma de saber donde va el importe.
            placeholder = { Text("0", style = EstilosDeDinero.enLista) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            // Alineado a la derecha como los totales de cada seccion: asi los
            // importes forman una columna y se comparan de un vistazo.
            textStyle = EstilosDeDinero.enLista.copy(textAlign = TextAlign.End),
            colors = camposSinFondo(),
            modifier = Modifier.width(130.dp),
        )
        // Sin descripcion, un lector de pantalla anuncia "interruptor" y no dice
        // de que. El nombre de la linea no basta: hay que decir que hace.
        val descripcionDelInterruptor = stringResource(R.string.plan_activa)
        Switch(
            checked = linea.activa,
            onCheckedChange = { alEvento(EventoDelPlan.CambiarActiva(linea, it)) },
            modifier = Modifier.semantics { contentDescription = descripcionDelInterruptor },
        )
        IconButton(onClick = { alEvento(EventoDelPlan.EliminarLinea(linea)) }) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.plan_eliminar),
            )
        }
    }
}

@Composable
private fun camposSinFondo() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

private fun textoInicial(monto: Money): String = if (monto.esCero) "" else monto.toString()

@Composable
private fun TipoDeLinea.color(): Color =
    when (this) {
        TipoDeLinea.INGRESO -> MiPlataTheme.dinero.ingreso
        TipoDeLinea.AHORRO -> MiPlataTheme.dinero.ahorro
        else -> MiPlataTheme.dinero.gasto
    }

private fun TipoDeLinea.etiqueta(): Int =
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
