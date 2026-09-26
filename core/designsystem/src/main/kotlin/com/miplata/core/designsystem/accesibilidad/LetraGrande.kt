package com.miplata.core.designsystem.accesibilidad

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * A partir de esta escala, dos piezas que van lado a lado dejan de caber.
 *
 * 1.5 es el primer paso de "letra grande" en los ajustes de Android. Hasta 1.3
 * -el maximo antes de Android 14- las filas de la app caben en un movil de
 * 360dp. Por encima, una fila con nombre, importe y controles ya no.
 */
const val ESCALA_DE_LETRA_GRANDE = 1.5f

/**
 * Si el usuario ha subido la letra lo bastante como para que haya que apilar
 * en vez de poner en fila.
 *
 * La respuesta a la letra grande no es encoger el texto: es darle sitio. Una
 * fila que a tamaño normal lleva nombre e importe lado a lado pasa a llevarlos
 * uno encima del otro.
 */
@Composable
@ReadOnlyComposable
fun conLetraGrande(): Boolean = LocalDensity.current.fontScale >= ESCALA_DE_LETRA_GRANDE

/**
 * Un texto que tiene que caber en una linea y no puede partirse.
 *
 * Es la excepcion a la regla de [conLetraGrande]: cuando el sitio es fijo y no
 * se puede apilar -una etiqueta de la barra de navegacion-, partir la palabra
 * ("Movimi / entos") es peor que reducirla un poco. El texto empieza en el
 * tamaño del estilo, que ya respeta la escala del usuario, y solo se reduce lo
 * que haga falta para caber.
 *
 * Nunca baja de [TAMANO_MINIMO], que va en `dp` y no en `sp` a proposito: es
 * lo que mide ese texto a tamaño normal. En `sp` el minimo creceria con la
 * escala y, al 200%, "Movimientos" ya no cabria ni en el minimo. Por debajo de
 * ese suelo se trunca con puntos suspensivos antes que volverse ilegible.
 */
@Composable
fun TextoQueCabe(
    texto: String,
    modifier: Modifier = Modifier,
) {
    val estilo = LocalTextStyle.current
    val minimo = with(LocalDensity.current) { TAMANO_MINIMO.toSp() }
    BasicText(
        text = texto,
        modifier = modifier,
        style = estilo.copy(color = LocalContentColor.current),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = minimo,
                maxFontSize = estilo.fontSize.takeIf { it.isSpecified } ?: TAMANO_SIN_TEMA,
            ),
    )
}

private val TAMANO_MINIMO = 11.dp

/** El de un `Text` sin estilo, por si se usa fuera de `MaterialTheme`. */
private val TAMANO_SIN_TEMA = 14.sp
