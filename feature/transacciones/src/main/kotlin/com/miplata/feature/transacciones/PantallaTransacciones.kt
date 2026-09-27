package com.miplata.feature.transacciones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.miplata.core.designsystem.componentes.BotonDeAjustes
import com.miplata.core.designsystem.componentes.PantallaConTituloGrande
import com.miplata.core.designsystem.componentes.SelectorDeMes
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.formato.recordarFormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
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
    alAbrirAjustes: () -> Unit = {},
    viewModel: TransaccionesViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    PantallaTransacciones(estado, viewModel::alEvento, modifier, alAbrirAjustes)
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
    alAbrirAjustes: () -> Unit = {},
) {
    val dinero = recordarFormateadorDeDinero(estado.moneda)

    PantallaConTituloGrande(
        titulo = stringResource(R.string.transacciones_titulo),
        modifier = modifier,
        acciones = { BotonDeAjustes(alAbrirAjustes) },
        botonFlotante = {
            // Sin cuentas no hay nada que anotar, asi que el boton no se ofrece:
            // un formulario que no se puede guardar es peor que no tener boton.
            if (!estado.faltanCuentas) {
                FloatingActionButton(onClick = { alEvento(EventoDeMovimientos.AnotarMovimiento) }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.transacciones_anotar),
                    )
                }
            }
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = relleno,
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            item { Cabecera(estado, dinero, alEvento) }

            if (estado.faltanCuentas) {
                item { Aviso(stringResource(R.string.transacciones_sin_cuentas)) }
            } else if (estado.estaVacio && !estado.cargando) {
                item { Aviso(stringResource(R.string.transacciones_vacio)) }
            }

            items(estado.dias, key = { "dia-${it.fecha}" }) { dia ->
                GrupoDelDia(dia, estado.hoy, dinero) { movimiento ->
                    alEvento(EventoDeMovimientos.EditarMovimiento(movimiento.transaccion))
                }
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
        SelectorDeMes(
            mes = estado.mes,
            alAnterior = { alEvento(EventoDeMovimientos.MesAnterior) },
            alSiguiente = { alEvento(EventoDeMovimientos.MesSiguiente) },
        )

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
