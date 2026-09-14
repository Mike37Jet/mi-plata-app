package com.miplata.feature.cuentas

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Editor(
    editor: EditorDeCuenta,
    alEvento: (EventoDeCuentas) -> Unit,
) {
    val analizador = recordarAnalizadorDeDinero()
    var confirmandoBorrado by rememberSaveable { mutableStateOf(false) }
    // El texto del importe vive aqui mientras se escribe: si el unico origen
    // fuera el estado, teclear "12," se convertiria en "12" al instante y no
    // habria forma de llegar al segundo decimal.
    var saldoTecleado by rememberSaveable { mutableStateOf(textoInicial(editor.saldoInicial)) }
    val estadoDeLaHoja = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { alEvento(EventoDeCuentas.CerrarEditor) },
        sheetState = estadoDeLaHoja,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            // Las acciones van ARRIBA, no al pie del formulario.
            //
            // En una hoja modal el contenido puede ser mas alto que el hueco
            // disponible -teclado abierto, fuente grande, cinco chips de tipo en
            // dos filas- y unos botones al final acaban por debajo del borde de
            // la pantalla: la accion principal deja de existir para quien no
            // adivine que hay que arrastrar. Arriba estan siempre a la vista.
            CabeceraDelEditor(editor, alEvento)

            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = editor.nombre,
                    onValueChange = { alEvento(EventoDeCuentas.CambiarNombre(it)) },
                    label = { Text(stringResource(R.string.cuentas_nombre)) },
                    placeholder = { Text(stringResource(R.string.cuentas_nombre_ejemplo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SelectorDeTipo(editor.tipo) { alEvento(EventoDeCuentas.CambiarTipo(it)) }

                OutlinedTextField(
                    value = saldoTecleado,
                    onValueChange = { texto ->
                        saldoTecleado = texto
                        analizador.parsear(texto)?.let {
                            alEvento(EventoDeCuentas.CambiarSaldoInicial(it))
                        }
                    },
                    label = { Text(stringResource(R.string.cuentas_saldo_inicial)) },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = EstilosDeDinero.enLista,
                    modifier = Modifier.fillMaxWidth(),
                )

                Interruptor(
                    titulo = stringResource(R.string.cuentas_incluir_en_total),
                    detalle = stringResource(R.string.cuentas_incluir_en_total_detalle),
                    activo = editor.incluirEnTotal,
                ) { alEvento(EventoDeCuentas.CambiarIncluirEnTotal(it)) }

                Interruptor(
                    titulo = stringResource(R.string.cuentas_archivada),
                    detalle = stringResource(R.string.cuentas_archivada_detalle),
                    activo = editor.archivada,
                ) { alEvento(EventoDeCuentas.CambiarArchivada(it)) }

                // Borrar es destructivo y no comparte sitio con Guardar: al final
                // del formulario y detras de una confirmacion, para que nadie lo
                // pulse de paso. Solo en las cuentas que ya existen; descartar una
                // nueva es cancelar.
                if (!editor.esNueva) {
                    TextButton(onClick = { confirmandoBorrado = true }) {
                        Text(
                            stringResource(R.string.cuentas_eliminar),
                            color = MiPlataTheme.dinero.sobregiro,
                        )
                    }
                }
            }
        }
    }

    if (confirmandoBorrado) {
        DialogoDeBorrado(
            nombre = editor.nombre,
            alConfirmar = {
                confirmandoBorrado = false
                alEvento(EventoDeCuentas.Eliminar)
            },
            alCancelar = { confirmandoBorrado = false },
        )
    }
}

/**
 * Borrar una cuenta es de las pocas cosas de la app que no se deshacen solas.
 *
 * El dialogo apunta a archivar, que es lo que el usuario suele querer cuando
 * deja de usar una cuenta: conservar el historial sin verla por delante.
 */
@Composable
private fun DialogoDeBorrado(
    nombre: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(stringResource(R.string.cuentas_eliminar_titulo, nombre)) },
        text = { Text(stringResource(R.string.cuentas_eliminar_aviso)) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(
                    stringResource(R.string.cuentas_eliminar),
                    color = MiPlataTheme.dinero.sobregiro,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text(stringResource(R.string.cuentas_cancelar)) }
        },
    )
}

@Composable
private fun SelectorDeTipo(
    seleccionado: TipoDeCuenta,
    alElegir: (TipoDeCuenta) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.cuentas_tipo),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Envuelve solo: con cinco tipos y la fuente al 200% una fila unica se
        // sale de la pantalla y los ultimos tipos quedan inalcanzables.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Un grupo de opciones excluyentes, no cinco interruptores sueltos.
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            TipoDeCuenta.entries.forEach { tipo ->
                FilterChip(
                    selected = tipo == seleccionado,
                    onClick = { alElegir(tipo) },
                    label = { Text(stringResource(tipo.etiqueta())) },
                )
            }
        }
    }
}

@Composable
private fun Interruptor(
    titulo: String,
    detalle: String,
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // El titulo y su explicacion son una sola cosa que leer; el interruptor
        // de al lado ya anuncia si esta activado.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = "$titulo. $detalle" },
        ) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = activo, onCheckedChange = alCambiar)
    }
}

internal fun TipoDeCuenta.etiqueta(): Int =
    when (this) {
        TipoDeCuenta.EFECTIVO -> R.string.cuentas_tipo_efectivo
        TipoDeCuenta.BANCARIA -> R.string.cuentas_tipo_bancaria
        TipoDeCuenta.TARJETA_CREDITO -> R.string.cuentas_tipo_tarjeta
        TipoDeCuenta.AHORRO -> R.string.cuentas_tipo_ahorro
        TipoDeCuenta.INVERSION -> R.string.cuentas_tipo_inversion
    }

private fun textoInicial(monto: Money): String = if (monto.esCero) "" else monto.toString()

/**
 * Titulo y acciones de la hoja, fijos en la parte de arriba.
 *
 * Guardar a la derecha, donde cae el pulgar, y desactivado mientras la cuenta
 * no tenga nombre: es la unica regla que impone el dominio (docs/03), y se
 * ensena antes de pulsar en vez de fallar despues.
 *
 * A partir de una escala de fuente de 1.5 el titulo baja a su propia linea. En
 * una sola fila no caben, y lo que se parte es la palabra "Guardar" -en "Guar"
 * y "dar"-, justo el boton que hay que encontrar.
 */
@Composable
private fun CabeceraDelEditor(
    editor: EditorDeCuenta,
    alEvento: (EventoDeCuentas) -> Unit,
) {
    val titulo =
        stringResource(if (editor.esNueva) R.string.cuentas_nueva else R.string.cuentas_editar)
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
            TextButton(onClick = { alEvento(EventoDeCuentas.CerrarEditor) }) {
                Text(stringResource(R.string.cuentas_cancelar))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!apilado) {
                Text(text = titulo, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(
                onClick = { alEvento(EventoDeCuentas.Guardar) },
                enabled = editor.puedeGuardar,
            ) {
                Text(stringResource(R.string.cuentas_guardar))
            }
        }
    }
}

private const val ESCALA_QUE_NO_CABE = 1.5f
