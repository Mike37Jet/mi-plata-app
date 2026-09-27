package com.miplata.core.designsystem.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.miplata.core.designsystem.R

/**
 * El engranaje que lleva a los ajustes.
 *
 * Va en la barra de titulo de las cuatro pestañas, siempre en el mismo sitio
 * -arriba a la derecha-, como en iOS. Vive aqui y no en el feature de ajustes
 * porque un feature no puede depender de otro (docs/04): cada pestaña pone el
 * boton y avisa de que se pulso, y `:app` decide a donde se va.
 */
@Composable
fun BotonDeAjustes(alPulsar: () -> Unit) {
    IconButton(onClick = alPulsar) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.designsystem_ajustes))
    }
}
