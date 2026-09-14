package com.miplata.feature.transacciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.datetime.LocalDate

@Composable
fun PantallaTransacciones(
    modifier: Modifier = Modifier,
    viewModel: TransaccionesViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaTransacciones(estado, viewModel::alEvento, modifier)
}

/**
 * La mitad "realidad" del modelo (docs/00).
 *
 * Lo que se anota aqui alimenta a la vez el Resumen y los saldos de Cuentas:
 * las tres pantallas leen los mismos movimientos, asi que un gasto apuntado
 * aparece en las tres sin que nadie refresque nada.
 */
@Composable
internal fun PantallaTransacciones(
    estado: TransaccionesUiState,
    alEvento: (EventoDeMovimientos) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Cabecera(estado, dinero, alEvento) }

            if (estado.faltanCuentas) {
                item { Aviso(stringResource(R.string.transacciones_sin_cuentas)) }
            } else if (estado.estaVacio && !estado.cargando) {
                item { Aviso(stringResource(R.string.transacciones_vacio)) }
            }

            estado.dias.forEach { dia ->
                item(key = "dia-${dia.fecha}") { CabeceraDelDia(dia, dinero) }

                items(dia.movimientos, key = { it.id.valor }) { movimiento ->
                    FilaDeMovimiento(movimiento, dinero) {
                        alEvento(EventoDeMovimientos.EditarMovimiento(movimiento.transaccion))
                    }
                }
            }
        }

        // Sin cuentas no hay nada que anotar, asi que el boton no se ofrece: un
        // formulario que no se puede guardar es peor que no tener boton.
        if (!estado.faltanCuentas) {
            androidx.compose.material3.FloatingActionButton(
                onClick = { alEvento(EventoDeMovimientos.AnotarMovimiento) },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.transacciones_anotar),
                )
            }
        }
    }

    estado.editor?.let { EditorDeMovimientoUi(it, estado, alEvento) }
}

@Composable
private fun Cabecera(
    estado: TransaccionesUiState,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDeMovimientos) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { alEvento(EventoDeMovimientos.MesAnterior) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.transacciones_mes_anterior),
                )
            }
            Text(
                text = estado.mes.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(120.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { alEvento(EventoDeMovimientos.MesSiguiente) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.transacciones_mes_siguiente),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TotalDelMes(
                etiqueta = stringResource(R.string.transacciones_entro),
                monto = dinero.formatear(estado.ingresos),
                color = MiPlataTheme.dinero.ingreso,
                modifier = Modifier.weight(1f),
            )
            TotalDelMes(
                etiqueta = stringResource(R.string.transacciones_salio),
                monto = dinero.formatear(estado.gastos),
                color = MiPlataTheme.dinero.gasto,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TotalDelMes(
    etiqueta: String,
    monto: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(monto, style = EstilosDeDinero.enLista, color = color)
    }
}

/**
 * La cabecera de cada dia, con lo que sumo entre todo.
 *
 * Un dia es la unidad en la que la gente recuerda lo que gasto -"el sabado se me
 * fue la mano"-, y el total responde a "¿que tal fue?" sin sumar la columna.
 */
@Composable
private fun CabeceraDelDia(
    dia: DiaEnLista,
    dinero: FormateadorDeDinero,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                text = dia.fecha.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = dinero.formatearConSigno(dia.neto),
                style = EstilosDeDinero.secundario,
                color =
                    if (dia.neto.esNegativo) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MiPlataTheme.dinero.ingreso
                    },
            )
        }
    }
}

@Composable
private fun FilaDeMovimiento(
    movimiento: MovimientoEnLista,
    dinero: FormateadorDeDinero,
    alPulsar: () -> Unit,
) {
    Card(onClick = alPulsar, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tituloDe(movimiento), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = detalleDe(movimiento),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Con signo: en una lista de movimientos el signo ES la informacion.
            Text(
                text = dinero.formatearConSigno(conSigno(movimiento)),
                style = EstilosDeDinero.enLista,
                color = colorDe(movimiento.tipo),
            )
        }
    }
}

/**
 * Lo que mejor identifica el movimiento, de lo mas concreto a lo mas generico.
 *
 * La nota gana porque es lo que el usuario escribio a mano: si se molesto en
 * poner "cena con Ana", eso es lo que quiere leer, no "Comida".
 */
@Composable
private fun tituloDe(movimiento: MovimientoEnLista): String =
    movimiento.nota
        ?: movimiento.lineaDePlan
        ?: movimiento.categoria
        ?: stringResource(R.string.transacciones_sin_categoria)

@Composable
private fun detalleDe(movimiento: MovimientoEnLista): String =
    if (movimiento.tipo == TipoDeTransaccion.TRANSFERENCIA) {
        // "Banco · Hacia Cartera". Sin el destino, una transferencia no cuenta
        // lo unico que importa de ella: a donde fue el dinero.
        listOfNotNull(
            movimiento.cuenta.takeIf { it.isNotBlank() },
            movimiento.cuentaDestino?.let { stringResource(R.string.transacciones_hacia, it) },
        ).joinToString(" · ")
    } else {
        buildList {
            add(movimiento.cuenta)
            movimiento.categoria?.takeIf { it != tituloDe(movimiento) }?.let { add(it) }
        }.filter { it.isNotBlank() }
            .joinToString(" · ")
    }

/** El monto con el signo que le toca; el modelo lo guarda siempre positivo. */
private fun conSigno(movimiento: MovimientoEnLista): Money =
    when (movimiento.tipo) {
        TipoDeTransaccion.INGRESO -> movimiento.monto
        else -> -movimiento.monto
    }

@Composable
private fun colorDe(tipo: TipoDeTransaccion): Color =
    when (tipo) {
        TipoDeTransaccion.INGRESO -> MiPlataTheme.dinero.ingreso
        // Una transferencia no es un gasto: no deberia leerse como dinero
        // perdido, solo como dinero movido.
        TipoDeTransaccion.TRANSFERENCIA -> MaterialTheme.colorScheme.onSurfaceVariant
        TipoDeTransaccion.GASTO -> MiPlataTheme.dinero.gasto
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

private val DIA_DE_EJEMPLO = LocalDate(2026, 3, 12)
private val DIA_ANTERIOR_DE_EJEMPLO = LocalDate(2026, 3, 10)

@Preview(showBackground = true)
@Composable
private fun PantallaTransaccionesPreview() {
    MiPlataTheme {
        PantallaTransacciones(
            estado =
                TransaccionesUiState(
                    mes = Mes.de(2026, 3),
                    cargando = false,
                    ingresos = Money.deUnidades(2000),
                    gastos = Money.deUnidades(184),
                    dias =
                        listOf(
                            DiaEnLista(
                                fecha = DIA_DE_EJEMPLO,
                                neto = Money.deUnidades(-64),
                                movimientos =
                                    listOf(
                                        ejemplo("a", 42, TipoDeTransaccion.GASTO, "Cena con Ana", "Comida"),
                                        ejemplo("b", 22, TipoDeTransaccion.GASTO, null, "Transporte"),
                                    ),
                            ),
                            DiaEnLista(
                                fecha = DIA_ANTERIOR_DE_EJEMPLO,
                                neto = Money.deUnidades(2000),
                                movimientos =
                                    listOf(ejemplo("c", 2000, TipoDeTransaccion.INGRESO, null, "Sueldo")),
                            ),
                        ),
                ),
            alEvento = {},
        )
    }
}

private fun ejemplo(
    id: String,
    monto: Long,
    tipo: TipoDeTransaccion,
    nota: String?,
    categoria: String?,
) = MovimientoEnLista(
    transaccion =
        Transaccion(
            id = TransaccionId(id),
            fecha = DIA_DE_EJEMPLO,
            monto = Money.deUnidades(monto),
            tipo = tipo,
            cuentaOrigenId = CuentaId("c1"),
            nota = nota,
        ),
    cuenta = "Cuenta del banco",
    cuentaDestino = null,
    categoria = categoria,
    lineaDePlan = null,
)
