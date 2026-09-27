package com.miplata.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.backup.Recuento
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.MiPlataTheme
import kotlinx.datetime.TimeZone

@Composable
fun PantallaRestaurar(
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestaurarViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // Igual que al exportar: la frase en un `remember` a secas, nunca guardada
    // en el estado de la instancia.
    var frase by remember { mutableStateOf("") }

    // `OpenDocument` y no `GetContent`: da acceso al documento a traves del
    // proveedor (Drive, OneDrive...) sin pedir permisos de almacenamiento.
    val selector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.elegirArchivo(uri.toString())
        }

    PantallaRestaurar(
        estado = estado,
        frase = frase,
        alCambiarFrase = { frase = it },
        alElegirArchivo = { selector.launch(arrayOf(TIPO_CUALQUIERA)) },
        alRestaurar = {
            viewModel.restaurar(frase.toCharArray())
            frase = ""
        },
        alDeshacer = viewModel::deshacer,
        alVolverAlInicio = viewModel::volverAlInicio,
        alVolver = alVolver,
        modifier = modifier,
    )
}

/**
 * La pantalla como funcion pura de su estado.
 *
 * Cada paso dibuja solo lo suyo. La confirmacion vive aqui y no en el ViewModel:
 * es un "¿seguro?" de la interfaz, y el ViewModel solo recibe la orden cuando el
 * usuario ya dijo que si.
 */
@Suppress("LongParameterList")
@Composable
internal fun PantallaRestaurar(
    estado: RestaurarUiState,
    frase: String,
    alCambiarFrase: (String) -> Unit,
    alElegirArchivo: () -> Unit,
    alRestaurar: () -> Unit,
    alDeshacer: () -> Unit,
    alVolverAlInicio: () -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmando by remember { mutableStateOf(false) }

    // Con el titulo y la flecha fijos arriba, como Ajustes: antes la flecha iba
    // dentro de lo que se desplaza, y al bajar por el formulario desaparecia sin
    // dejar ninguna forma visible de volver.
    PantallaConTituloGrande(
        titulo = stringResource(R.string.restaurar_titulo),
        modifier = modifier,
        alVolver = alVolver,
    ) { relleno ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    // Margenes de la escala aurea, y al final el hueco de la barra
                    // inferior de cristal.
                    .padding(relleno),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            when (val paso = estado.paso) {
                PasoDeRestauracion.Inicio -> Inicio(estado.copiaPreviaEnMillis, alElegirArchivo, alDeshacer)
                PasoDeRestauracion.LeyendoArchivo -> EnCurso(stringResource(R.string.restaurar_leyendo))
                is PasoDeRestauracion.Resumen ->
                    Resumen(
                        paso = paso,
                        frase = frase,
                        alCambiarFrase = alCambiarFrase,
                        alRestaurar = { confirmando = true },
                        alElegirOtro = alVolverAlInicio,
                    )

                PasoDeRestauracion.Restaurando -> EnCurso(stringResource(R.string.restaurar_restaurando))
                is PasoDeRestauracion.Restaurada ->
                    Restaurada(paso.aviso, estado.copiaPreviaEnMillis != null, alDeshacer)
                PasoDeRestauracion.Deshaciendo -> EnCurso(stringResource(R.string.restaurar_deshaciendo))
                PasoDeRestauracion.Deshecha -> Mensaje(stringResource(R.string.restaurar_deshecha), alVolverAlInicio)
                is PasoDeRestauracion.Fallida -> Mensaje(paso.mensaje, alVolverAlInicio, esError = true)
            }
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text(stringResource(R.string.restaurar_confirmar_titulo)) },
            text = { Text(stringResource(R.string.restaurar_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    alRestaurar()
                }) {
                    Text(stringResource(R.string.restaurar_confirmar_si), color = MiPlataTheme.dinero.sobregiro)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmando = false }) { Text(stringResource(R.string.restaurar_cancelar)) }
            },
        )
    }
}

@Composable
private fun Inicio(
    copiaPreviaEnMillis: Long?,
    alElegirArchivo: () -> Unit,
    alDeshacer: () -> Unit,
) {
    Text(stringResource(R.string.restaurar_explicacion), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = alElegirArchivo, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.restaurar_elegir))
    }
    // Volver atras no desaparece al salir de la pantalla: se puede aunque la
    // restauracion fuera hace una semana. Y no pide confirmacion porque no pierde
    // nada: lo de ahora se guarda antes, asi que tambien se puede volver a ello.
    if (copiaPreviaEnMillis != null) {
        OutlinedButton(onClick = alDeshacer, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    R.string.restaurar_deshacer_ultima,
                    fechaDeLaCopia(copiaPreviaEnMillis, TimeZone.currentSystemDefault()),
                ),
            )
        }
        Text(
            text = stringResource(R.string.restaurar_deshacer_explicacion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Que trae la copia y que hay ahora, uno al lado del otro.
 *
 * Es lo que evita el error mas facil de cometer: restaurar una copia vieja por
 * equivocacion. "Trae 2 cuentas y 40 movimientos / Ahora tienes 5 cuentas y 900"
 * se lee de un vistazo, y avisa antes de que haga falta deshacer nada.
 */
@Composable
private fun Resumen(
    paso: PasoDeRestauracion.Resumen,
    frase: String,
    alCambiarFrase: (String) -> Unit,
    alRestaurar: () -> Unit,
    alElegirOtro: () -> Unit,
) {
    Text(
        text =
            stringResource(
                R.string.restaurar_copia_de,
                fechaDeLaCopia(paso.copia.creadoEnMillis, TimeZone.currentSystemDefault()),
                paso.copia.dispositivo,
            ),
        style = MaterialTheme.typography.titleSmall,
    )
    Recuentos(stringResource(R.string.restaurar_trae), paso.copia.contenido)
    Recuentos(stringResource(R.string.restaurar_tienes), paso.actual)
    Text(
        text = stringResource(R.string.restaurar_sustituira),
        style = MaterialTheme.typography.bodyMedium,
        color = MiPlataTheme.dinero.sobregiro,
    )

    OutlinedTextField(
        value = frase,
        onValueChange = alCambiarFrase,
        label = { Text(stringResource(R.string.restaurar_frase)) },
        singleLine = true,
        isError = paso.fraseIncorrecta,
        supportingText =
            if (paso.fraseIncorrecta) {
                { Text(stringResource(R.string.restaurar_frase_incorrecta)) }
            } else {
                null
            },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(onClick = alRestaurar, enabled = frase.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.restaurar_boton))
    }
    TextButton(onClick = alElegirOtro) { Text(stringResource(R.string.restaurar_otro_archivo)) }
}

@Composable
private fun Recuentos(
    titulo: String,
    recuento: Recuento,
) {
    val partes =
        listOf(
            pluralStringResource(R.plurals.restaurar_cuentas, recuento.cuentas, recuento.cuentas),
            pluralStringResource(R.plurals.restaurar_movimientos, recuento.transacciones, recuento.transacciones),
            pluralStringResource(R.plurals.restaurar_planes, recuento.planes, recuento.planes),
            pluralStringResource(R.plurals.restaurar_categorias, recuento.categorias, recuento.categorias),
        )
    Column {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Text(partes.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Restaurada(
    aviso: String?,
    sePuedeDeshacer: Boolean,
    alDeshacer: () -> Unit,
) {
    Text(
        text = stringResource(R.string.restaurar_hecho),
        style = MaterialTheme.typography.titleMedium,
        color = MiPlataTheme.dinero.ingreso,
    )
    aviso?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    if (sePuedeDeshacer) {
        OutlinedButton(onClick = alDeshacer, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.restaurar_deshacer))
        }
    }
}

@Composable
private fun EnCurso(texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Mensaje(
    texto: String,
    alSeguir: () -> Unit,
    esError: Boolean = false,
) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = if (esError) MaterialTheme.colorScheme.error else MiPlataTheme.dinero.ingreso,
    )
    TextButton(onClick = alSeguir) { Text(stringResource(R.string.copia_entendido)) }
}

/**
 * Cualquier tipo de archivo en el selector. La extension `.mpb` no la conoce
 * ningun proveedor, y filtrar por un tipo MIME concreto haria que la copia no
 * apareciera en Drive, que la guarda como "binario desconocido".
 */
private const val TIPO_CUALQUIERA = "*/*"
