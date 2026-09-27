package com.miplata.feature.cuentas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.componentes.BotonDeAjustes
import com.miplata.core.designsystem.componentes.CifraPrincipal
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.componentes.IconoEnCirculo
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.usecase.CuentaConSaldo

@Composable
fun PantallaCuentas(
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
    viewModel: CuentasViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaCuentas(estado, viewModel::alEvento, modifier, alAbrirAjustes)
}

/**
 * La pantalla como funcion pura de su estado.
 *
 * Los saldos que muestra no estan guardados en ningun sitio: los deriva el
 * dominio de los movimientos (docs/03). Aqui solo se dibujan.
 */
@Composable
internal fun PantallaCuentas(
    estado: CuentasUiState,
    alEvento: (EventoDeCuentas) -> Unit,
    modifier: Modifier = Modifier,
    alAbrirAjustes: () -> Unit = {},
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    PantallaConTituloGrande(
        titulo = stringResource(R.string.cuentas_titulo),
        modifier = modifier,
        acciones = { BotonDeAjustes(alAbrirAjustes) },
        botonFlotante = {
            FloatingActionButton(onClick = { alEvento(EventoDeCuentas.CrearCuenta) }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.cuentas_anadir))
            }
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = relleno,
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            item { Total(estado, dinero) }

            if (estado.estaVacio && !estado.cargando) {
                item { Aviso(stringResource(R.string.cuentas_vacio)) }
            }

            val (archivadas, activas) = estado.cuentas.partition { it.cuenta.archivada }
            if (activas.isNotEmpty()) {
                item(key = "activas") {
                    GrupoDeCuentas(activas, dinero, alEvento, modifier = Modifier.padding(top = Espacio.m))
                }
            }
            if (archivadas.isNotEmpty()) {
                item(key = "archivadas") { Archivadas(archivadas, dinero, alEvento) }
            }
        }
    }

    estado.editor?.let { Editor(it, alEvento) }
}

@Composable
private fun Total(
    estado: CuentasUiState,
    dinero: FormateadorDeDinero,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.cuentas_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CifraPrincipal(
            texto = dinero.formatear(estado.total),
            color = colorDelSaldo(estado.total),
        )

        // Un total que se calla lo que deja fuera miente por omision.
        if (estado.cuentasEnOtraMoneda > 0) {
            Aviso(
                pluralStringResource(
                    R.plurals.cuentas_en_otra_moneda,
                    estado.cuentasEnOtraMoneda,
                    estado.cuentasEnOtraMoneda,
                ),
            )
        }
    }
}

@Composable
private fun GrupoDeCuentas(
    cuentas: List<CuentaConSaldo>,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDeCuentas) -> Unit,
    modifier: Modifier = Modifier,
    encabezado: (@Composable () -> Unit)? = null,
) {
    GrupoDeLista(modifier = modifier) {
        encabezado?.invoke()
        cuentas.forEachIndexed { i, conSaldo ->
            FilaDeCuenta(conSaldo, dinero, conSeparador = i > 0 || encabezado != null) {
                alEvento(EventoDeCuentas.EditarCuenta(conSaldo.cuenta))
            }
        }
    }
}

/**
 * Las cuentas archivadas, plegadas bajo una fila que dice cuantas hay.
 *
 * Una cuenta archivada es una que ya no se usa: ocupaba el mismo sitio que las
 * activas y competia con ellas por la atencion (docs/10). Plegada sigue a mano
 * -su historial importa-, pero no estorba.
 */
@Composable
private fun Archivadas(
    cuentas: List<CuentaConSaldo>,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDeCuentas) -> Unit,
) {
    var desplegadas by rememberSaveable { mutableStateOf(false) }
    val estadoParaLectores =
        stringResource(if (desplegadas) R.string.cuentas_desplegadas else R.string.cuentas_plegadas)
    val cabecera: @Composable () -> Unit = {
        FilaDeLista(
            titulo = stringResource(R.string.cuentas_archivadas),
            final = {
                Text(
                    text = cuentas.size.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (desplegadas) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            alPulsar = { desplegadas = !desplegadas },
            conChevron = false,
            modifier = Modifier.semantics { stateDescription = estadoParaLectores },
        )
    }
    if (desplegadas) {
        GrupoDeCuentas(cuentas, dinero, alEvento, encabezado = cabecera)
    } else {
        GrupoDeLista { cabecera() }
    }
}

@Composable
private fun FilaDeCuenta(
    conSaldo: CuentaConSaldo,
    dinero: FormateadorDeDinero,
    conSeparador: Boolean,
    alPulsar: () -> Unit,
) {
    val cuenta = conSaldo.cuenta
    FilaDeLista(
        titulo = cuenta.nombre,
        detalle = etiquetaDe(cuenta, dinero),
        inicio = { IconoEnCirculo(cuenta.tipo.icono(), MaterialTheme.colorScheme.primary) },
        final = {
            Text(
                text = dinero.formatear(conSaldo.saldo),
                style = EstilosDeDinero.enLista,
                color = colorDelSaldo(conSaldo.saldo),
            )
        },
        alPulsar = alPulsar,
        // Todas las filas abren el mismo editor: un chevron en cada una seria
        // ruido, igual que en Movimientos.
        conChevron = false,
        conSeparador = conSeparador,
    )
}

private fun TipoDeCuenta.icono(): ImageVector =
    when (this) {
        TipoDeCuenta.EFECTIVO -> Icons.Outlined.Payments
        TipoDeCuenta.BANCARIA -> Icons.Outlined.AccountBalance
        TipoDeCuenta.TARJETA_CREDITO -> Icons.Outlined.CreditCard
        TipoDeCuenta.AHORRO -> Icons.Outlined.Savings
        TipoDeCuenta.INVERSION -> Icons.AutoMirrored.Outlined.ShowChart
    }

/**
 * El tipo de cuenta y, si las hay, las salvedades.
 *
 * Van juntas en una linea porque son la misma pregunta -"¿que es esta cuenta?"-
 * y separarlas en insignias sueltas llenaria la fila de ruido.
 */
@Composable
private fun etiquetaDe(
    cuenta: Cuenta,
    dinero: FormateadorDeDinero,
): String =
    buildList {
        add(stringResource(cuenta.tipo.etiqueta()))
        when (val rol = cuenta.rol) {
            RolDeCuenta.Independiente -> Unit
            RolDeCuenta.Principal -> add(stringResource(R.string.cuentas_etiqueta_principal))
            is RolDeCuenta.Sobre -> {
                add(
                    when (val reparto = rol.reparto) {
                        is Reparto.Porcentaje ->
                            stringResource(
                                R.string.cuentas_etiqueta_sobre_porcentaje,
                                reparto.valor,
                            )
                        is Reparto.Monto ->
                            stringResource(R.string.cuentas_etiqueta_sobre_monto, dinero.formatear(reparto.monto))
                    },
                )
                if (rol.intocable) add(stringResource(R.string.cuentas_etiqueta_intocable))
            }
        }
        if (!cuenta.incluirEnTotal) add(stringResource(R.string.cuentas_etiqueta_fuera_del_total))
    }.joinToString(" · ")

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

/**
 * Rojo solo lo negativo; lo demas, del color del texto.
 *
 * Antes lo positivo iba en verde, pero un saldo no es un ingreso: en esta app el
 * verde dice "entro dinero", y pintar de verde todo lo que no es deuda le
 * quitaba el significado (docs/10). El rojo si significa algo aqui: deber.
 */
@Composable
private fun colorDelSaldo(saldo: Money): Color =
    if (saldo.esNegativo) MiPlataTheme.dinero.sobregiro else MaterialTheme.colorScheme.onSurface

@Preview(showBackground = true)
@Composable
private fun PantallaCuentasPreview() {
    MiPlataTheme {
        PantallaCuentas(
            estado =
                CuentasUiState(
                    cuentas =
                        listOf(
                            conSaldo("cartera", "Cartera", TipoDeCuenta.EFECTIVO, 120),
                            conSaldo("banco", "Cuenta del banco", TipoDeCuenta.BANCARIA, 2340),
                            conSaldo("visa", "Visa", TipoDeCuenta.TARJETA_CREDITO, -430),
                        ),
                    total = Money.deUnidades(2030),
                    cargando = false,
                ),
            alEvento = {},
        )
    }
}

private fun conSaldo(
    id: String,
    nombre: String,
    tipo: TipoDeCuenta,
    saldo: Long,
) = CuentaConSaldo(
    cuenta =
        Cuenta(
            id = CuentaId(id),
            nombre = nombre,
            tipo = tipo,
            saldoInicial = Money.deUnidades(saldo),
            moneda = Moneda("USD"),
        ),
    saldo = Money.deUnidades(saldo),
)
