package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.miplata.core.designsystem.theme.Espacio

/**
 * Un icono dentro de un circulo tintado de su color, al principio de una fila.
 *
 * El circulo mide 34dp y el icono 21dp: dos pasos seguidos de la escala aurea.
 * Es **decorativo**: lo que significa la fila lo dicen sus textos, asi que el
 * icono no se anuncia a un lector de pantalla.
 */
@Composable
fun IconoEnCirculo(
    icono: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(Espacio.l)
                .background(color.copy(alpha = TINTE_DEL_FONDO), CircleShape),
    ) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(Espacio.m))
    }
}

private const val TINTE_DEL_FONDO = 0.15f
