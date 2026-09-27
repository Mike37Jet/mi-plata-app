package com.miplata.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.miplata.core.designsystem.accesibilidad.conLetraGrande
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea

/**
 * La hoja para crear o editar una linea del plan.
 *
 * Nada se guarda hasta pulsar Guardar: cancelar deja el plan como estaba. Antes
 * se editaba en la propia fila y cada tecla se guardaba en la base, lo que
 * obligaba a un cuidado especial para no perder letras (ver el historial de
 * `PlanViewModel.editarPlan`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditorDeLineaUi(
    editor: EditorDeLinea,
    cuentas: List<Cuenta>,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val analizador = recordarAnalizadorDeDinero()
    // El texto de los campos vive aqui mientras se escribe y solo el valor sube
    // al ViewModel: "12," tiene que poder escribirse aunque aun no sea importe.
    var nombre by rememberSaveable(editor.id) { mutableStateOf(editor.nombre) }
    var monto by rememberSaveable(editor.id) { mutableStateOf(textoDe(editor.monto)) }

    ModalBottomSheet(
        onDismissRequest = { alEvento(EventoDelPlan.CerrarEditor) },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            // Las acciones arriba, siempre a la vista: con el teclado abierto o
            // la letra grande, al pie del formulario quedaban fuera de la hoja.
            CabeceraDelEditor(editor, alEvento)

            Column(
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Espacio.m)
                        .padding(bottom = Espacio.l),
            ) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        alEvento(EventoDelPlan.CambioEnEditor.Nombre(it))
                    },
                    label = { Text(stringResource(R.string.plan_nombre)) },
                    placeholder = { Text(stringResource(R.string.plan_nombre_ejemplo)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = monto,
                    onValueChange = { texto ->
                        monto = texto
                        val importe = if (texto.isBlank()) Money.ZERO else analizador.parsear(texto)
                        importe?.let { alEvento(EventoDelPlan.CambioEnEditor.Monto(it)) }
                    },
                    label = { Text(stringResource(R.string.plan_importe)) },
                    placeholder = { Text("0", style = EstilosDeDinero.entrada) },
                    textStyle = EstilosDeDinero.entrada,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    Text(
                        text = stringResource(R.string.plan_tipo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Envuelve solo: con la letra al 200% los cuatro tipos no
                    // caben en una fila.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                    ) {
                        TipoDeLinea.entries.forEach { tipo ->
                            FilterChip(
                                selected = tipo == editor.tipo,
                                onClick = { alEvento(EventoDelPlan.CambioEnEditor.Tipo(tipo)) },
                                label = { Text(stringResource(tipo.etiquetaEnSingular())) },
                            )
                        }
                    }
                }

                if (cuentas.isNotEmpty()) {
                    SelectorDeCuenta(editor.cuentaId, cuentas) { alEvento(EventoDelPlan.CambioEnEditor.Cuenta(it)) }
                }

                CuentaEsteMes(editor.activa) { alEvento(EventoDelPlan.CambioEnEditor.Activa(it)) }
            }
        }
    }
}

/**
 * Titulo, Cancelar y Guardar.
 *
 * Con letra grande el titulo baja a su propia linea: en una sola fila no
 * caben, y lo que se partia era "Guardar", justo el boton que hay que encontrar.
 * Para una linea existente, Eliminar va junto a Cancelar.
 */
@Composable
private fun CabeceraDelEditor(
    editor: EditorDeLinea,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val titulo = stringResource(if (editor.esNueva) R.string.plan_nueva_linea else R.string.plan_editar_linea)
    val apilado = conLetraGrande()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.xs)) {
        if (apilado) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Espacio.m),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { alEvento(EventoDelPlan.CerrarEditor) }) {
                Text(stringResource(R.string.plan_cancelar))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!apilado) {
                Text(text = titulo, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { alEvento(EventoDelPlan.GuardarLinea) }) {
                Text(stringResource(R.string.plan_guardar))
            }
        }
        if (!editor.esNueva) {
            val id = editor.id
            TextButton(
                onClick = { id?.let { alEvento(EventoDelPlan.EliminarLinea(it)) } },
                modifier = Modifier.padding(horizontal = Espacio.xs),
            ) {
                Text(stringResource(R.string.plan_eliminar), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * De que cuenta sale la linea: de ahi sale su dinero en el plan por cuentas
 * (docs/adr/0007).
 *
 * Una linea sin cuenta sale de la principal, asi que con principal no hay
 * opcion "ninguna": se marca la principal. Sin principal si la hay, porque
 * entonces "ninguna" es un estado distinto de cualquier cuenta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorDeCuenta(
    elegida: CuentaId?,
    cuentas: List<Cuenta>,
    alElegir: (CuentaId?) -> Unit,
) {
    val principal = cuentas.firstOrNull { it.esPrincipal }
    val marcada = elegida?.takeIf { id -> cuentas.any { it.id == id } } ?: principal?.id
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text = stringResource(R.string.plan_cuenta),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            if (principal == null) {
                FilterChip(
                    selected = marcada == null,
                    onClick = { alElegir(null) },
                    label = { Text(stringResource(R.string.plan_ninguna_cuenta)) },
                )
            }
            cuentas.forEach { cuenta ->
                FilterChip(
                    selected = cuenta.id == marcada,
                    onClick = { alElegir(cuenta.id) },
                    label = { Text(cuenta.nombre) },
                )
            }
        }
    }
}

/**
 * "Cuenta este mes", con su explicacion.
 *
 * Desactivar no es borrar: la linea sigue en el plan y se copia al mes
 * siguiente, pero este mes no suma. Es el seguro trimestral o la matricula de
 * septiembre (docs/03).
 */
@Composable
private fun CuentaEsteMes(
    activa: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    val titulo = stringResource(R.string.plan_activa)
    val detalle = stringResource(R.string.plan_activa_detalle)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Titulo y explicacion son una sola cosa que leer; el interruptor de al
        // lado ya anuncia si esta activado.
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$titulo. $detalle" }) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = activa, onCheckedChange = alCambiar)
    }
}

private fun TipoDeLinea.etiquetaEnSingular(): Int =
    when (this) {
        TipoDeLinea.INGRESO -> R.string.plan_tipo_ingreso_uno
        TipoDeLinea.GASTO_FIJO -> R.string.plan_tipo_gasto_fijo_uno
        TipoDeLinea.GASTO_VARIABLE -> R.string.plan_tipo_gasto_variable_uno
        TipoDeLinea.AHORRO -> R.string.plan_tipo_ahorro_uno
    }

private fun textoDe(monto: Money): String = if (monto.esCero) "" else monto.toString()
