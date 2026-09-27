package com.miplata.feature.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.usecase.PlanDeCuenta

/**
 * "Ya transferi el reparto": anota de una vez lo que falta pasar a los sobres.
 *
 * La app no toca el banco; el usuario transfiere alli y aqui lo confirma. Si no
 * lo hace, el cierre de mes lo da por hecho igualmente (docs/adr/0007).
 */
@Composable
internal fun BotonDelReparto(
    pendiente: Money,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.m),
    ) {
        FilledTonalButton(onClick = { alEvento(EventoDelPlan.RegistrarReparto) }) {
            Text(stringResource(R.string.plan_registrar_reparto, dinero.formatear(pendiente)))
        }
        Text(
            text = stringResource(R.string.plan_registrar_reparto_detalle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * El mes de una cuenta: con cuanto empieza, que le entra, que sale de ella y con
 * cuanto deberia terminar.
 *
 * Es la hoja de calculo de antes, una columna por cuenta, con el resultado
 * junto al nombre para leerlo sin bajar.
 */
@Composable
internal fun GrupoDeCuenta(
    deCuenta: PlanDeCuenta,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    val pie =
        when {
            deCuenta.quedaCorta ->
                stringResource(R.string.plan_queda_corta, dinero.formatear(deCuenta.terminaCon))
            deCuenta.cuenta.esIntocable && deCuenta.gastos.esPositivo ->
                stringResource(R.string.plan_intocable_con_gastos)
            else -> stringResource(R.string.plan_hoy_tiene, dinero.formatear(deCuenta.saldoActual))
        }

    GrupoDeLista(
        titulo = deCuenta.cuenta.nombre,
        pie = pie,
        alLadoDelTitulo = {
            Text(
                text = dinero.formatear(deCuenta.terminaCon),
                style = EstilosDeDinero.secundario,
                color = colorDelSaldo(deCuenta.terminaCon),
            )
        },
    ) {
        val (ingresos, salidas) = deCuenta.lineas.partition { it.tipo == TipoDeLinea.INGRESO }
        Cifra(stringResource(R.string.plan_empiezas_con), dinero.formatear(deCuenta.empiezaCon), conSeparador = false)
        // Los ingresos son lineas del plan: se tocan para editarlas, como los gastos.
        ingresos.forEach { linea ->
            key(linea.id.valor) { FilaDeLinea(linea, dinero, conSeparador = true, alEvento) }
        }
        if (deCuenta.recibe.esPositivo) {
            Cifra(
                stringResource(R.string.plan_recibe),
                dinero.formatear(deCuenta.recibe),
                color = MiPlataTheme.dinero.ingreso,
            )
        }
        if (deCuenta.reparte.esPositivo) {
            Cifra(stringResource(R.string.plan_reparte), dinero.formatear(-deCuenta.reparte))
        }
        salidas.forEach { linea ->
            key(linea.id.valor) { FilaDeLinea(linea, dinero, conSeparador = true, alEvento) }
        }
        Cifra(
            stringResource(R.string.plan_termina_con),
            dinero.formatear(deCuenta.terminaCon),
            color = colorDelSaldo(deCuenta.terminaCon),
            destacada = true,
        )
    }
}

@Composable
private fun Cifra(
    titulo: String,
    valor: String,
    conSeparador: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    destacada: Boolean = false,
) {
    FilaDeLista(
        titulo = titulo,
        conSeparador = conSeparador,
        final = {
            Text(
                text = valor,
                style =
                    if (destacada) {
                        EstilosDeDinero.enLista.copy(
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        EstilosDeDinero.enLista
                    },
                color = color,
            )
        },
    )
}

@Composable
private fun colorDelSaldo(saldo: Money): Color =
    if (saldo.esNegativo) MiPlataTheme.dinero.sobregiro else MaterialTheme.colorScheme.onSurface
