package com.miplata.core.designsystem.componentes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Cuanto tapa el velo de una barra translucida: el 92%.
 *
 * Es lo minimo que deja leer el texto secundario de la barra -las pestañas no
 * seleccionadas- por encima de cualquier cosa que pase por debajo.
 * `ContrasteTest` lo comprueba con el peor caso: contenido blanco puro detras
 * del tema oscuro, o negro puro detras del claro. Con el 80%, que es lo que se
 * probo primero, ese texto bajaba a 3.1:1 en oscuro y 3.7:1 en claro.
 *
 * El 8% que queda deja ver el color y el movimiento de lo que pasa por
 * detras, desenfocado. Es sutil a proposito: el cristal es un detalle, y la
 * barra existe para leerse.
 */
const val OPACIDAD_DEL_VELO = 0.92f

private val RADIO_DEL_DESENFOQUE = 21.dp

/**
 * Lo que se ve borroso detras de las barras. Lo pone `:app` alrededor del
 * contenido; las barras lo leen para saber que desenfocar.
 */
val LocalFondoDesenfocable: ProvidableCompositionLocal<HazeState?> = staticCompositionLocalOf { null }

/** El contenido que las barras translucidas dejan ver, desenfocado, por detras. */
fun Modifier.fondoDesenfocable(estado: HazeState): Modifier = hazeSource(estado)

/**
 * Una superficie de cristal: desenfoca lo que pasa por detras y lo cubre con un
 * velo del color de la superficie.
 *
 * El desenfoque de verdad necesita Android 12. Por debajo, Haze deja solo el
 * velo, que ya es lo que hace legible la barra; lo que se pierde es el efecto.
 */
@Composable
@ReadOnlyComposable
fun Modifier.cristal(estado: HazeState?): Modifier {
    val velo = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = OPACIDAD_DEL_VELO)
    return if (estado == null) {
        this
    } else {
        hazeEffect(
            state = estado,
            style =
                HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(velo),
                    blurRadius = RADIO_DEL_DESENFOQUE,
                    noiseFactor = 0f,
                ),
        )
    }
}

/** El color de una barra de cristal cuando no hay nada que desenfocar. */
@Composable
@ReadOnlyComposable
fun colorDeCristal(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = OPACIDAD_DEL_VELO)
