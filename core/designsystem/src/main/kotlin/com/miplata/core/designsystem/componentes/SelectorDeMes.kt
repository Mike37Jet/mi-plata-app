package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.miplata.core.designsystem.R
import com.miplata.core.designsystem.formato.nombreDelMes
import com.miplata.core.designsystem.theme.Espacio
import com.miplata.core.domain.model.Mes

/**
 * "‹ Marzo 2026 ›": el mes que se esta viendo y como cambiarlo.
 *
 * Resumen, Plan y Movimientos tenian cada uno su copia de esta fila, con el mes
 * en formato de base de datos (`2026-03`). Ahora es una sola pieza, y el mes se
 * dice como lo dice una persona.
 */
@Composable
fun SelectorDeMes(
    mes: Mes,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = alAnterior) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.designsystem_mes_anterior),
            )
        }
        Text(
            text = nombreDelMes(mes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Un minimo, no un ancho fijo: "Septiembre 2026" es mas largo que
            // "Mayo 2026", y con letra grande los dos crecen (docs/09).
            modifier = Modifier.widthIn(min = Espacio.xxl + Espacio.l),
        )
        IconButton(onClick = alSiguiente) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.designsystem_mes_siguiente),
            )
        }
    }
}
