package com.miplata.feature.bienvenida

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miplata.core.designsystem.formato.recordarAnalizadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import java.util.Currency
import java.util.Locale

// Cada paso de la bienvenida. La pantalla que los encadena esta en PantallaBienvenida.kt.

@Composable
internal fun PasoInicial(
    alEvento: (EventoDeBienvenida) -> Unit,
    alTenerCopia: () -> Unit,
) {
    Titulo(stringResource(R.string.bienvenida_titulo))
    Explicacion(stringResource(R.string.bienvenida_texto))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { alEvento(EventoDeBienvenida.Siguiente) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bienvenida_empezar))
        }
        // Al mismo nivel que empezar, y no escondido en un menu: en un movil
        // nuevo es lo primero que busca quien ya usaba la app.
        OutlinedButton(onClick = alTenerCopia, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.bienvenida_tengo_copia), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasoTuMes(
    estado: BienvenidaUiState,
    alEvento: (EventoDeBienvenida) -> Unit,
) {
    Titulo(stringResource(R.string.bienvenida_mes_titulo))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.bienvenida_moneda), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            estado.monedasSugeridas.forEach { moneda ->
                FilterChip(
                    selected = moneda == estado.moneda,
                    onClick = { alEvento(EventoDeBienvenida.ElegirMoneda(moneda)) },
                    label = { Text(moneda.codigo) },
                )
            }
        }
        // El codigo solo no basta: "PEN" o "CLP" no dicen nada a todo el mundo.
        Text(
            text = nombreDe(estado.moneda),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.bienvenida_primer_dia), style = MaterialTheme.typography.titleMedium)
        Explicacion(stringResource(R.string.bienvenida_primer_dia_detalle))
        SelectorDeDia(estado.primerDiaDelMes) { alEvento(EventoDeBienvenida.CambiarPrimerDia(it)) }
    }

    Navegacion(estado, alEvento)
}

/**
 * "Dólar estadounidense (US$)", en el idioma de la app y no en el del telefono.
 *
 * La app solo habla español: con el telefono en ingles, el nombre salia como
 * "US Dollar" en mitad de una pantalla en español. El pais si es el del
 * telefono, porque decide el simbolo que el usuario reconoce.
 */
@Composable
private fun nombreDe(moneda: Moneda): String {
    val pais = LocalConfiguration.current.locales[0].country
    val enEspanol =
        Locale
            .Builder()
            .setLanguage(IDIOMA_DE_LA_APP)
            .setRegion(pais)
            .build()
    val currency = Currency.getInstance(moneda.codigo)
    val nombre = currency.getDisplayName(enEspanol).replaceFirstChar { it.uppercase(enEspanol) }
    return "$nombre (${currency.getSymbol(enEspanol)})"
}

private const val IDIOMA_DE_LA_APP = "es"

@Composable
private fun SelectorDeDia(
    dia: Int,
    alCambiar: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { alCambiar(dia - 1) }, enabled = dia > 1) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.bienvenida_dia_anterior),
            )
        }
        Text(
            text = stringResource(R.string.bienvenida_dia, dia),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            // Un minimo y no un ancho fijo: con letra grande "Día 25" crece.
            modifier = Modifier.widthIn(min = 96.dp),
        )
        IconButton(onClick = { alCambiar(dia + 1) }, enabled = dia < BienvenidaViewModel.ULTIMO_DIA) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.bienvenida_dia_siguiente),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasoPrimeraCuenta(
    estado: BienvenidaUiState,
    alEvento: (EventoDeBienvenida) -> Unit,
) {
    // El texto de los campos vive aqui y solo el valor sube: "12," tiene que
    // poder escribirse aunque todavia no sea un importe.
    var nombre by rememberSaveable { mutableStateOf(estado.nombreDeLaCuenta) }
    var saldo by rememberSaveable { mutableStateOf(textoDe(estado.saldoActual)) }
    val analizador = recordarAnalizadorDeDinero()

    Titulo(stringResource(R.string.bienvenida_cuenta_titulo))
    Explicacion(stringResource(R.string.bienvenida_cuenta_texto))

    OutlinedTextField(
        value = nombre,
        onValueChange = {
            nombre = it
            alEvento(EventoDeBienvenida.CambiarNombreDeLaCuenta(it))
        },
        label = { Text(stringResource(R.string.bienvenida_cuenta_nombre)) },
        placeholder = { Text(stringResource(R.string.bienvenida_cuenta_nombre_ejemplo)) },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.bienvenida_cuenta_tipo), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            TipoDeCuenta.entries.forEach { tipo ->
                FilterChip(
                    selected = tipo == estado.tipoDeCuenta,
                    onClick = { alEvento(EventoDeBienvenida.ElegirTipoDeCuenta(tipo)) },
                    label = { Text(stringResource(tipo.etiqueta())) },
                )
            }
        }
    }

    OutlinedTextField(
        value = saldo,
        onValueChange = { texto ->
            saldo = texto
            importeDe(texto, analizador::parsear)?.let { alEvento(EventoDeBienvenida.CambiarSaldo(it)) }
        },
        label = { Text(stringResource(R.string.bienvenida_cuenta_saldo)) },
        placeholder = { Text("0", style = EstilosDeDinero.enLista) },
        textStyle = EstilosDeDinero.enLista,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    Navegacion(estado, alEvento)
}

@Composable
internal fun PasoPrimerIngreso(
    estado: BienvenidaUiState,
    alEvento: (EventoDeBienvenida) -> Unit,
    alTerminar: () -> Unit,
) {
    var ingreso by rememberSaveable { mutableStateOf(textoDe(estado.ingresoMensual)) }
    val analizador = recordarAnalizadorDeDinero()
    val nombreDelIngreso = stringResource(R.string.bienvenida_ingreso_nombre)

    fun terminar(conIngreso: Boolean) {
        alTerminar()
        alEvento(EventoDeBienvenida.Terminar(conIngreso, nombreDelIngreso))
    }

    Titulo(stringResource(R.string.bienvenida_ingreso_titulo))
    Explicacion(stringResource(R.string.bienvenida_ingreso_texto))

    OutlinedTextField(
        value = ingreso,
        onValueChange = { texto ->
            ingreso = texto
            importeDe(texto, analizador::parsear)?.let { alEvento(EventoDeBienvenida.CambiarIngreso(it)) }
        },
        label = { Text(stringResource(R.string.bienvenida_ingreso_monto)) },
        placeholder = { Text("0", style = EstilosDeDinero.enLista) },
        textStyle = EstilosDeDinero.enLista,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    estado.errorAlGuardar?.let { detalle ->
        Text(
            text =
                listOf(
                    stringResource(R.string.bienvenida_error),
                    detalle,
                ).filter { it.isNotBlank() }.joinToString("\n"),
            color = MaterialTheme.colorScheme.error,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { terminar(conIngreso = true) },
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bienvenida_terminar))
        }
        TextButton(
            onClick = { terminar(conIngreso = false) },
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bienvenida_despues))
        }
        TextButton(
            onClick = { alEvento(EventoDeBienvenida.Atras) },
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bienvenida_atras))
        }
    }
}

/**
 * El importe de lo tecleado. Un campo vacio es cero: el usuario lo ha borrado,
 * no ha escrito algo que no se entiende.
 */
internal fun importeDe(
    texto: String,
    parsear: (String) -> Money?,
): Money? = if (texto.isBlank()) Money.ZERO else parsear(texto)

private fun textoDe(monto: Money): String = if (monto.esCero) "" else monto.toString()

private fun TipoDeCuenta.etiqueta(): Int =
    when (this) {
        TipoDeCuenta.EFECTIVO -> R.string.bienvenida_tipo_efectivo
        TipoDeCuenta.BANCARIA -> R.string.bienvenida_tipo_bancaria
        TipoDeCuenta.TARJETA_CREDITO -> R.string.bienvenida_tipo_tarjeta
        TipoDeCuenta.AHORRO -> R.string.bienvenida_tipo_ahorro
        TipoDeCuenta.INVERSION -> R.string.bienvenida_tipo_inversion
    }
