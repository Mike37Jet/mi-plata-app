package com.miplata.feature.transacciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * El alta rapida.
 *
 * Toda la pantalla esta ordenada por lo que hace falta para guardar: primero el
 * importe -que es a lo que se viene-, luego el tipo y la cuenta, y al final lo
 * opcional. Una app de finanzas en la que anotar cuesta se queda sin datos, y
 * sin datos el Resumen no responde a nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorDeMovimientoUi(
    editor: EditorDeMovimiento,
    estado: TransaccionesUiState,
    alEvento: (EventoDeMovimientos) -> Unit,
) {
    val analizador = recordarAnalizadorDeDinero()
    // El texto del importe vive aqui mientras se escribe: si el unico origen
    // fuera el estado, teclear "12," se convertiria en "12" al instante y no
    // habria forma de llegar al segundo decimal.
    var montoTecleado by rememberSaveable { mutableStateOf(textoInicial(editor.monto)) }
    val foco = remember { FocusRequester() }

    // El teclado, puesto en el importe desde el primer instante: es el unico
    // campo que hay que escribir siempre, y ahorrarse ese toque es la mitad de
    // lo que hace que esto sea un "alta rapida".
    LaunchedEffect(editor.id) {
        if (editor.esNuevo) foco.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = { alEvento(EventoDeMovimientos.CerrarEditor) },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            // Las acciones arriba y fijas: el formulario es mas alto que la hoja
            // con el teclado abierto, y unos botones al pie se quedarian por
            // debajo del borde de la pantalla.
            Cabecera(editor, alEvento)

            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = montoTecleado,
                    onValueChange = { texto ->
                        montoTecleado = texto
                        analizador.parsear(texto)?.let {
                            alEvento(EventoDeMovimientos.CambioDeCampo.Monto(it))
                        }
                    },
                    label = { Text(stringResource(R.string.transacciones_monto)) },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = EstilosDeDinero.destacado,
                    modifier = Modifier.fillMaxWidth().focusRequester(foco),
                )

                SelectorDeTipo(editor.tipo) { alEvento(EventoDeMovimientos.CambioDeCampo.Tipo(it)) }

                SelectorDeFecha(editor.fecha) { alEvento(EventoDeMovimientos.CambioDeCampo.Fecha(it)) }

                Selector(
                    titulo =
                        stringResource(
                            if (editor.esTransferencia) {
                                R.string.transacciones_cuenta_origen
                            } else {
                                R.string.transacciones_cuenta
                            },
                        ),
                    opciones = estado.cuentas.map { it.id.valor to it.nombre },
                    seleccionada = editor.cuentaOrigenId?.valor,
                    conNinguna = false,
                ) { valor ->
                    estado.cuentas
                        .first { it.id.valor == valor }
                        .let { alEvento(EventoDeMovimientos.CambioDeCampo.CuentaOrigen(it.id)) }
                }

                if (editor.esTransferencia) {
                    Selector(
                        titulo = stringResource(R.string.transacciones_cuenta_destino),
                        // La cuenta de origen no se ofrece: una transferencia a
                        // si misma no significa nada y el modelo la rechaza.
                        opciones =
                            estado.cuentas
                                .filterNot { it.id == editor.cuentaOrigenId }
                                .map { it.id.valor to it.nombre },
                        seleccionada = editor.cuentaDestinoId?.valor,
                        conNinguna = false,
                    ) { valor ->
                        estado.cuentas
                            .first { it.id.valor == valor }
                            .let { alEvento(EventoDeMovimientos.CambioDeCampo.CuentaDestino(it.id)) }
                    }
                }

                // Una transferencia no lleva categoria ni linea de plan: no es un
                // gasto, es dinero cambiando de sitio.
                if (!editor.esTransferencia) {
                    Selector(
                        titulo = stringResource(R.string.transacciones_categoria),
                        opciones = estado.categorias.map { it.id.valor to it.nombre },
                        seleccionada = editor.categoriaId?.valor,
                    ) { valor ->
                        alEvento(EventoDeMovimientos.CambioDeCampo.Categoria(valor?.let(::CategoriaId)))
                    }

                    // El puente con el plan: engancharlo aqui es lo que hace que
                    // el Resumen pueda decir "planificaste 400 de comida, llevas
                    // gastados 520".
                    if (estado.lineasDelPlan.isNotEmpty()) {
                        Selector(
                            titulo = stringResource(R.string.transacciones_linea_de_plan),
                            opciones =
                                estado.lineasDelPlan
                                    .filter { it.nombre.isNotBlank() }
                                    .map { it.id.valor to it.nombre },
                            seleccionada = editor.lineaDePlanId?.valor,
                        ) { valor ->
                            alEvento(EventoDeMovimientos.CambioDeCampo.DeLineaDePlan(valor?.let(::LineaId)))
                        }
                    }
                }

                OutlinedTextField(
                    value = editor.nota,
                    onValueChange = { alEvento(EventoDeMovimientos.CambioDeCampo.Nota(it)) },
                    label = { Text(stringResource(R.string.transacciones_nota)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Borrar es destructivo y no comparte sitio con Guardar. Solo en
                // los movimientos que ya existen: descartar uno nuevo es cancelar.
                if (!editor.esNuevo) {
                    TextButton(onClick = { alEvento(EventoDeMovimientos.Eliminar) }) {
                        Text(
                            stringResource(R.string.transacciones_eliminar),
                            color = MiPlataTheme.dinero.sobregiro,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cabecera(
    editor: EditorDeMovimiento,
    alEvento: (EventoDeMovimientos) -> Unit,
) {
    val titulo =
        stringResource(
            if (editor.esNuevo) R.string.transacciones_nuevo else R.string.transacciones_editar,
        )
    val apilado = LocalDensity.current.fontScale >= ESCALA_QUE_NO_CABE

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (apilado) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { alEvento(EventoDeMovimientos.CerrarEditor) }) {
                Text(stringResource(R.string.transacciones_cancelar))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!apilado) {
                Text(text = titulo, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(
                onClick = { alEvento(EventoDeMovimientos.Guardar) },
                enabled = editor.puedeGuardar,
            ) {
                Text(stringResource(R.string.transacciones_guardar))
            }
        }
    }
}

@Composable
private fun SelectorDeTipo(
    seleccionado: TipoDeTransaccion,
    alElegir: (TipoDeTransaccion) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
        TipoDeTransaccion.entries.forEach { tipo ->
            FilterChip(
                selected = tipo == seleccionado,
                onClick = { alElegir(tipo) },
                label = { Text(stringResource(tipo.etiqueta())) },
            )
        }
    }
}

/**
 * La fecha, a un toque de distancia en los dos casos que importan.
 *
 * Casi todo se anota el mismo dia o el siguiente, asi que dos flechas resuelven
 * el 90% sin abrir un calendario. Un selector de fecha completo para retroceder
 * un dia es tres toques de mas en el camino de cada dia.
 */
@Composable
private fun SelectorDeFecha(
    fecha: LocalDate,
    alCambiar: (LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.transacciones_fecha),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { alCambiar(fecha.minus(UN_DIA, DateTimeUnit.DAY)) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.transacciones_dia_anterior),
            )
        }
        Text(text = fecha.toString(), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { alCambiar(fecha.plus(UN_DIA, DateTimeUnit.DAY)) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.transacciones_dia_siguiente),
            )
        }
    }
}

/**
 * Una lista de opciones en chips, con "Ninguna" cuando el campo es opcional.
 *
 * Chips y no un desplegable porque en un movil son mas faciles de acertar con el
 * pulgar y se ve todo de un vistazo; con muchas opciones envuelven en varias
 * filas en vez de esconderse tras un menu.
 */
@Composable
private fun Selector(
    titulo: String,
    opciones: List<Pair<String, String>>,
    seleccionada: String?,
    conNinguna: Boolean = true,
    alElegir: (String?) -> Unit,
) {
    if (opciones.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            if (conNinguna) {
                FilterChip(
                    selected = seleccionada == null,
                    onClick = { alElegir(null) },
                    label = { Text(stringResource(R.string.transacciones_ninguna)) },
                )
            }
            opciones.forEach { (valor, etiqueta) ->
                FilterChip(
                    selected = valor == seleccionada,
                    onClick = { alElegir(valor) },
                    label = { Text(etiqueta) },
                )
            }
        }
    }
}

private fun TipoDeTransaccion.etiqueta(): Int =
    when (this) {
        TipoDeTransaccion.GASTO -> R.string.transacciones_tipo_gasto
        TipoDeTransaccion.INGRESO -> R.string.transacciones_tipo_ingreso
        TipoDeTransaccion.TRANSFERENCIA -> R.string.transacciones_tipo_transferencia
    }

private fun textoInicial(monto: Money): String = if (monto.esCero) "" else monto.toString()

private const val ESCALA_QUE_NO_CABE = 1.5f
private const val UN_DIA = 1
