package com.miplata.core.designsystem.componentes

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.miplata.core.designsystem.theme.EstilosDeDinero
import com.miplata.core.designsystem.theme.TipografiaDeLaApp

/**
 * La cifra grande de una pantalla: lo que queda del mes, lo que hay en total.
 *
 * Mide 55sp, el tope de la escala aurea, y **se encoge si no cabe**, hasta
 * 34sp (un paso de φ por debajo). Una cifra de dinero no se puede partir en
 * dos lineas ni cortar con puntos suspensivos: "€123,4…" no dice cuanto. Con
 * seis cifras, o con la letra del sistema al maximo, a 55sp no cabria en un
 * movil de 360dp.
 */
@Composable
fun CifraPrincipal(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = texto,
        modifier = modifier,
        style = EstilosDeDinero.destacado.copy(color = color),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = TipografiaDeLaApp.headlineLarge.fontSize,
                maxFontSize = EstilosDeDinero.destacado.fontSize,
            ),
    )
}
