package com.miplata.feature.backup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.backup.FraseDeRespaldo
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.FrecuenciaDeRecordatorio
import com.miplata.feature.backup.recordatorio.NotificadorEnAndroid
import kotlinx.datetime.TimeZone

@Composable
fun PantallaCopia(
    alVolver: () -> Unit,
    alAbrirRestaurar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CopiaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // La frase vive AQUI y en un `remember` a secas, nunca `rememberSaveable`:
    // lo que se guarda en el estado de la instancia se escribe en disco cuando el
    // sistema mata la app en segundo plano. Si la app muere a medias, se pierde
    // lo tecleado; es el precio correcto.
    var frase by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var riesgoAsumido by remember { mutableStateOf(false) }
    var nombrePendiente by remember { mutableStateOf("") }

    // Si se pueden enseñar notificaciones. Se vuelve a mirar cada vez que la
    // pantalla vuelve a primer plano: el usuario puede haber ido a los ajustes
    // del sistema a activarlas desde el boton de esta misma pantalla.
    val contexto = LocalContext.current
    var notificacionesActivas by remember { mutableStateOf(NotificadorEnAndroid.sePuedenMostrar(contexto)) }
    LifecycleResumeEffect(Unit) {
        notificacionesActivas = NotificadorEnAndroid.sePuedenMostrar(contexto)
        onPauseOrDispose {}
    }
    val pedirPermiso =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificacionesActivas = NotificadorEnAndroid.sePuedenMostrar(contexto)
        }

    // El selector de "Guardar como" del sistema. Drive y OneDrive aparecen en el
    // como cualquier otra carpeta porque sus apps se registran como proveedores
    // de documentos: la app no toca la red ni necesita permisos (docs/05).
    val selector =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_MIME)) { uri ->
            // Nulo si el usuario cancelo: se conserva lo tecleado para reintentar.
            if (uri != null) {
                viewModel.exportar(uri.toString(), frase.toCharArray(), nombrePendiente)
                frase = ""
                confirmacion = ""
                riesgoAsumido = false
            }
        }

    PantallaCopia(
        estado = estado,
        frase = frase,
        confirmacion = confirmacion,
        riesgoAsumido = riesgoAsumido,
        alCambiarFrase = { frase = it },
        alCambiarConfirmacion = { confirmacion = it },
        alCambiarRiesgo = { riesgoAsumido = it },
        alGuardar = {
            nombrePendiente = viewModel.nombreSugerido()
            selector.launch(nombrePendiente)
        },
        alDescartarAviso = viewModel::descartarAviso,
        alVolver = alVolver,
        alAbrirRestaurar = alAbrirRestaurar,
        notificacionesActivas = notificacionesActivas,
        alCambiarFrecuencia = { frecuencia ->
            viewModel.cambiarFrecuencia(frecuencia)
            // El permiso se pide aqui, al pedir un recordatorio: es cuando la
            // pregunta tiene sentido. Pedirlo al abrir la app, sin contexto, es la
            // forma mas segura de que se deniegue.
            if (frecuencia != FrecuenciaDeRecordatorio.NUNCA &&
                !notificacionesActivas &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        alActivarNotificaciones = {
            // Si el usuario ya las denego, el sistema no vuelve a enseñar la
            // pregunta: lo unico que funciona es llevarle a los ajustes.
            contexto.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
            )
        },
        modifier = modifier,
    )
}

/**
 * La pantalla como funcion pura de su estado.
 *
 * Recibe la frase desde fuera en vez de guardarla, para que la version con
 * ViewModel decida donde vive -y donde NO vive- y para poder probarla sin el
 * selector de archivos, que Robolectric no sabe abrir.
 */
@Suppress("LongParameterList")
@Composable
internal fun PantallaCopia(
    estado: CopiaUiState,
    frase: String,
    confirmacion: String,
    riesgoAsumido: Boolean,
    alCambiarFrase: (String) -> Unit,
    alCambiarConfirmacion: (String) -> Unit,
    alCambiarRiesgo: (Boolean) -> Unit,
    alGuardar: () -> Unit,
    alDescartarAviso: () -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
    alAbrirRestaurar: () -> Unit = {},
    notificacionesActivas: Boolean = true,
    alCambiarFrecuencia: (FrecuenciaDeRecordatorio) -> Unit = {},
    alActivarNotificaciones: () -> Unit = {},
) {
    val problema = problemaCon(frase, confirmacion, riesgoAsumido)
    val enCurso = estado.exportacion == Exportacion.EnCurso

    // Con el titulo y la flecha fijos arriba, como Ajustes: antes la flecha iba
    // dentro de lo que se desplaza, y al bajar por el formulario desaparecia sin
    // dejar ninguna forma visible de volver.
    PantallaConTituloGrande(
        titulo = stringResource(R.string.copia_titulo),
        modifier = modifier,
        alVolver = alVolver,
    ) { relleno ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Sin esto, con el teclado abierto el boton de guardar queda
                    // debajo de el y ni siquiera se puede alcanzar desplazando: la
                    // zona desplazable seguia llegando hasta el borde de la pantalla,
                    // por detras del teclado. Asi termina justo encima.
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    // Margenes de la escala aurea, y al final el hueco de la barra
                    // inferior de cristal.
                    .padding(relleno),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            UltimaCopia(estado.ultimaCopiaEnMillis)

            // El resultado va ARRIBA, junto a "Ultima copia", y no debajo del boton.
            // Al pie de un formulario largo quedaba fuera de la pantalla en cuanto la
            // letra del sistema es grande: se guardaba la copia y el usuario no veia
            // ni la confirmacion ni el error. Aqui ademas cambia a la vez que la fecha
            // de la ultima copia, que es lo que acaba de pasar.
            Resultado(estado.exportacion, alDescartarAviso)

            Text(stringResource(R.string.copia_explicacion), style = MaterialTheme.typography.bodyMedium)
            // Primera de las dos veces que se avisa (docs/05). La segunda es la
            // casilla, que hay que marcar a mano.
            Text(
                text = stringResource(R.string.copia_advertencia),
                style = MaterialTheme.typography.bodyMedium,
                color = MiPlataTheme.dinero.sobregiro,
            )

            CampoDeFrase(
                valor = frase,
                alCambiar = alCambiarFrase,
                etiqueta = stringResource(R.string.copia_frase),
                ayuda =
                    pluralStringResource(
                        R.plurals.copia_frase_ayuda,
                        FraseDeRespaldo.LONGITUD_MINIMA,
                        FraseDeRespaldo.LONGITUD_MINIMA,
                    ),
                activo = !enCurso,
            )
            CampoDeFrase(
                valor = confirmacion,
                alCambiar = alCambiarConfirmacion,
                etiqueta = stringResource(R.string.copia_confirmacion),
                ayuda = null,
                activo = !enCurso,
            )

            CasillaDeRiesgo(riesgoAsumido, alCambiarRiesgo, activa = !enCurso)

            // El problema solo se enseña cuando ya se ha empezado a escribir: gritar
            // "demasiado corta" sobre un campo vacio es regañar antes de tiempo.
            if (frase.isNotEmpty() && problema != null) {
                Text(
                    text = stringResource(problema.mensaje()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = alGuardar,
                enabled = problema == null && !enCurso,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.copia_guardar))
            }

            Recordatorio(
                frecuencia = estado.frecuencia,
                notificacionesActivas = notificacionesActivas,
                alCambiar = alCambiarFrecuencia,
                alActivarNotificaciones = alActivarNotificaciones,
            )

            // Restaurar va al final y con menos peso visual: es lo que se hace una
            // vez, al cambiar de movil, y no lo que se viene a hacer cada mes.
            OutlinedButton(onClick = alAbrirRestaurar, enabled = !enCurso, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restaurar_abrir))
            }
        }
    }
}

/**
 * Cada cuanto recordar la copia, y si ese recordatorio va a poder verse.
 *
 * El aviso de notificaciones desactivadas es importante: sin el, el usuario
 * elegiria "cada mes", creeria que esta cubierto, y no le llegaria nada nunca.
 */
@Composable
private fun Recordatorio(
    frecuencia: FrecuenciaDeRecordatorio,
    notificacionesActivas: Boolean,
    alCambiar: (FrecuenciaDeRecordatorio) -> Unit,
    alActivarNotificaciones: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recordatorio_frecuencia), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            FrecuenciaDeRecordatorio.entries.forEach { opcion ->
                FilterChip(
                    selected = opcion == frecuencia,
                    onClick = { alCambiar(opcion) },
                    label = { Text(stringResource(opcion.etiqueta())) },
                )
            }
        }
        if (frecuencia != FrecuenciaDeRecordatorio.NUNCA && !notificacionesActivas) {
            Text(
                text = stringResource(R.string.recordatorio_sin_permiso),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = alActivarNotificaciones) { Text(stringResource(R.string.recordatorio_activar)) }
        }
    }
}

private fun FrecuenciaDeRecordatorio.etiqueta(): Int =
    when (this) {
        FrecuenciaDeRecordatorio.SEMANAL -> R.string.recordatorio_semanal
        FrecuenciaDeRecordatorio.MENSUAL -> R.string.recordatorio_mensual
        FrecuenciaDeRecordatorio.NUNCA -> R.string.recordatorio_nunca_opcion
    }

@Composable
private fun UltimaCopia(millis: Long?) {
    Text(
        text =
            if (millis == null) {
                stringResource(R.string.copia_nunca)
            } else {
                stringResource(R.string.copia_ultima, fechaDeLaCopia(millis, TimeZone.currentSystemDefault()))
            },
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * Un campo para la frase, oculta por defecto.
 *
 * El tipo de teclado `Password` no es solo por los puntitos: le dice al teclado
 * que **no aprenda** lo que se escribe. Con un teclado normal, la frase acabaria
 * en su diccionario de sugerencias, y de ahi en la nube del fabricante del
 * teclado. Por eso ademas se apaga la autocorreccion.
 */
@Composable
private fun CampoDeFrase(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    ayuda: String?,
    activo: Boolean,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        supportingText = ayuda?.let { { Text(it) } },
        singleLine = true,
        enabled = activo,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription =
                        stringResource(if (visible) R.string.copia_ocultar else R.string.copia_mostrar),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CasillaDeRiesgo(
    marcada: Boolean,
    alCambiar: (Boolean) -> Unit,
    activa: Boolean,
) {
    // Toda la fila es pulsable, no solo el cuadradito: es un objetivo de 18dp
    // que cuesta acertar, y un lector de pantalla lee la frase entera como una
    // sola casilla.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = marcada, enabled = activa, role = Role.Checkbox, onValueChange = alCambiar),
    ) {
        Checkbox(checked = marcada, onCheckedChange = null, enabled = activa)
        Text(
            text = stringResource(R.string.copia_asumo_el_riesgo),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Resultado(
    exportacion: Exportacion,
    alDescartar: () -> Unit,
) {
    when (exportacion) {
        Exportacion.Inactiva -> Unit
        Exportacion.EnCurso ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.copia_en_curso), style = MaterialTheme.typography.bodySmall)
            }

        is Exportacion.Terminada ->
            Aviso(stringResource(R.string.copia_terminada, exportacion.nombre), alDescartar)

        is Exportacion.Fallida ->
            Aviso(stringResource(R.string.copia_fallida, exportacion.mensaje), alDescartar, esError = true)
    }
}

@Composable
private fun Aviso(
    texto: String,
    alDescartar: () -> Unit,
    esError: Boolean = false,
) {
    Column {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (esError) MaterialTheme.colorScheme.error else MiPlataTheme.dinero.ingreso,
        )
        TextButton(onClick = alDescartar) { Text(stringResource(R.string.copia_entendido)) }
    }
}

private fun ProblemaConLaFrase.mensaje(): Int =
    when (this) {
        ProblemaConLaFrase.DEMASIADO_CORTA -> R.string.copia_problema_corta
        ProblemaConLaFrase.NO_COINCIDEN -> R.string.copia_problema_no_coinciden
        ProblemaConLaFrase.SIN_ASUMIR_EL_RIESGO -> R.string.copia_problema_riesgo
    }

/**
 * Generico a proposito. Un tipo propio (`application/x-miplata`) no lo reconoce
 * ningun proveedor, y algunos se niegan a guardar lo que no entienden.
 */
private const val TIPO_MIME = "application/octet-stream"

@Preview(showBackground = true)
@Composable
private fun PantallaCopiaPreview() {
    MiPlataTheme {
        PantallaCopia(
            estado = CopiaUiState(ultimaCopiaEnMillis = 1_772_000_000_000L),
            frase = "correcto caballo",
            confirmacion = "",
            riesgoAsumido = false,
            alCambiarFrase = {},
            alCambiarConfirmacion = {},
            alCambiarRiesgo = {},
            alGuardar = {},
            alDescartarAviso = {},
            alVolver = {},
        )
    }
}
