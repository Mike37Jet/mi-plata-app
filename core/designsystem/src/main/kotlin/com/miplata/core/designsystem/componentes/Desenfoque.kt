package com.miplata.core.designsystem.componentes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
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
    val oscuro = MaterialTheme.colorScheme.surface.luminance() < MITAD
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
        ).drawBehind { brilloDeCristal(oscuro) }
    }
}

/**
 * Lo que hace que el cristal parezca cristal y no una capa gris: la luz.
 *
 * Es lo que se toma del Liquid Glass de Apple sin su refraccion, que en Android
 * necesitaria shaders de Android 13 y una libreria que exige un Compose mas
 * nuevo (docs/10):
 *
 * - **Una linea de brillo** en el borde de arriba, mas intensa en el centro, como
 *   la luz que coge el canto de un vidrio. No va detras de ningun texto, asi que
 *   puede ser intensa.
 * - **Un reflejo** que baja desde ese borde y se desvanece antes de la mitad. Va
 *   por detras de las etiquetas y aclara el fondo, asi que es tenue:
 *   [REFLEJO_MAXIMO], que `ContrasteTest` incluye en sus cuentas.
 *
 * Se dibuja entre el desenfoque y el contenido de la barra: por encima del
 * cristal y por debajo de iconos y etiquetas.
 */
private fun DrawScope.brilloDeCristal(oscuro: Boolean) {
    val luz = Color.White
    drawRect(
        brush =
            Brush.verticalGradient(
                0f to luz.copy(alpha = REFLEJO_MAXIMO),
                FIN_DEL_REFLEJO to Color.Transparent,
            ),
    )
    drawLine(
        brush =
            Brush.horizontalGradient(
                0f to Color.Transparent,
                CENTRO to luz.copy(alpha = if (oscuro) INTENSIDAD_DEL_BRILLO_OSCURO else INTENSIDAD_DEL_BRILLO_CLARO),
                1f to Color.Transparent,
            ),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = GROSOR_DEL_BRILLO.toPx(),
    )
}

/**
 * El blanco que aclara el cristal en su borde de arriba: el 4%.
 *
 * Es lo mas que se puede sin que el texto claro del tema oscuro baje de 4.5:1
 * en el peor caso. Con el 8% bajaba a 4.4:1.
 */
const val REFLEJO_MAXIMO = 0.04f
private const val FIN_DEL_REFLEJO = 0.5f
private const val INTENSIDAD_DEL_BRILLO_OSCURO = 0.5f

// Sobre un cristal claro, el blanco se ve menos: el canto necesita mas luz.
private const val INTENSIDAD_DEL_BRILLO_CLARO = 0.9f
private val GROSOR_DEL_BRILLO = 1.dp
private const val MITAD = 0.5f

// La linea brilla mas en el centro y se apaga hacia los lados, como el canto de
// un vidrio iluminado desde arriba.
private const val CENTRO = 0.5f

/** El color de una barra de cristal cuando no hay nada que desenfocar. */
@Composable
@ReadOnlyComposable
fun colorDeCristal(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = OPACIDAD_DEL_VELO)
