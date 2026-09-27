package com.miplata.core.designsystem.componentes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.miplata.core.designsystem.theme.Espacio
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Cuanto tapa el velo de una barra translucida: el 55%.
 *
 * **Sobre el cristal solo va texto principal** (`onSurface`). El secundario y
 * el acento van sobre superficies opacas: el icono de la pestaña activa, sobre
 * su pastilla.
 *
 * `ContrasteTest` lo comprueba con lo peor que la app puede pintar por detras:
 * cualquiera de sus colores solidos -acento, colores de dinero, celdas- y su
 * texto, que despues de [RADIO_DEL_DESENFOQUE] queda como una mancha mezclada
 * con el fondo. El caso mas exigente es el gris de gasto bajo el tema oscuro,
 * que pide el 49%.
 *
 * Antes se exigia contra blanco puro (o negro puro en claro), y eso pedia el
 * 70%: la barra apenas dejaba ver nada. Pero la app no pinta blanco puro en
 * grandes superficies, y lo que si pinta en blanco -texto- el desenfoque lo
 * convierte en un gris tenue.
 */
const val OPACIDAD_DEL_VELO = 0.55f

/**
 * Cuanto se difumina lo que pasa por detras: 34dp, un paso de la escala aurea.
 *
 * Con un radio grande, un texto que pasa por debajo se vuelve una mancha y no
 * se lee a traves de la barra, que es lo que permite un velo tan fino. Con
 * 21dp todavia se adivinaban las letras.
 */
private val RADIO_DEL_DESENFOQUE = Espacio.l

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
