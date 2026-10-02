package com.miplata.feature.plan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.miplata.core.designsystem.componentes.FilaDeLista
import com.miplata.core.designsystem.componentes.GrupoDeLista
import com.miplata.core.designsystem.formato.FormateadorDeDinero
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.MiPlataTheme
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.usecase.PlanDeCuenta

/**
 * Una cuenta en el plan: su nombre, cuanto le queda al final y sus lineas.
 *
 * El como se llega a esa cifra va en una frase al pie, en palabras: antes eran
 * cinco filas contables (empieza con, recibe, reparte, gastos, termina con) que
 * habia que sumar de cabeza.
 */
@Composable
internal fun GrupoDeCuenta(
    deCuenta: PlanDeCuenta,
    dinero: FormateadorDeDinero,
    alEvento: (EventoDelPlan) -> Unit,
) {
    GrupoDeLista(
        titulo = deCuenta.cuenta.nombre,
        iconoDelTitulo = if (deCuenta.cuenta.esIntocable) Icons.Outlined.Lock else null,
        pie = pieDe(deCuenta, dinero),
        alLadoDelTitulo = {
            Text(
                text = stringResource(R.string.plan_le_queda, dinero.formatear(deCuenta.terminaCon)),
                style = EstilosDeDinero.secundario,
                color = colorDelSaldo(deCuenta.terminaCon),
            )
        },
    ) {
        if (deCuenta.lineas.isEmpty()) {
            FilaDeLista(titulo = stringResource(R.string.plan_sin_gastos_en_la_cuenta))
        }
        // Primero lo que entra, despues lo que sale: como se piensa el mes.
        val (ingresos, salidas) = deCuenta.lineas.partition { it.tipo == TipoDeLinea.INGRESO }
        (ingresos + salidas).forEachIndexed { i, linea ->
            key(linea.id.valor) { FilaDeLinea(linea, dinero, conSeparador = i > 0, alEvento) }
        }
    }
}

/** Como se llega a lo que le queda a la cuenta, en una frase; o el aviso, si lo hay. */
@Composable
private fun pieDe(
    deCuenta: PlanDeCuenta,
    dinero: FormateadorDeDinero,
): String {
    if (deCuenta.quedaCorta) {
        return stringResource(R.string.plan_queda_corta, dinero.formatear(deCuenta.terminaCon.valorAbsoluto()))
    }
    if (deCuenta.cuenta.esIntocable && deCuenta.gastos.esPositivo) {
        return stringResource(R.string.plan_intocable_con_gastos)
    }
    val tenia = dinero.formatear(deCuenta.empiezaCon)
    return when {
        deCuenta.reparte.esPositivo ->
            stringResource(R.string.plan_pie_principal, tenia, dinero.formatear(deCuenta.reparte))
        deCuenta.recibe.esPositivo ->
            stringResource(R.string.plan_pie_sobre, tenia, dinero.formatear(deCuenta.recibe))
        else -> stringResource(R.string.plan_pie_aparte, tenia)
    }
}

@Composable
private fun colorDelSaldo(saldo: Money): Color =
    if (saldo.esNegativo) MiPlataTheme.dinero.sobregiro else MaterialTheme.colorScheme.onSurface
