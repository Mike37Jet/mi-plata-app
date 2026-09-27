package com.miplata.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.miplata.core.designsystem.componentes.OPACIDAD_DEL_VELO
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import org.junit.Test
import kotlin.math.pow

/** WCAG AA para texto normal. Los importes son texto normal, no titulares. */
private const val CONTRASTE_MINIMO = 4.5

/** Relacion de contraste WCAG 2.x entre dos colores: de 1 (igual) a 21. */
private fun contraste(
    a: Color,
    b: Color,
): Double {
    val claro = maxOf(a.luminance(), b.luminance())
    val oscuro = minOf(a.luminance(), b.luminance())
    return (claro + 0.05) / (oscuro + 0.05)
}

/**
 * El gris de un tono de Material (la L* de CIELAB, de 0 a 100).
 *
 * El color dinamico genera las superficies a partir del fondo de pantalla, asi
 * que no se pueden probar todas. Pero Material solo las saca de una franja de
 * tonos fija, y lo que decide el contraste es la luminancia, que depende del
 * tono y no del matiz. Probar el tono mas desfavorable de la franja cubre
 * cualquier fondo de pantalla.
 */
private fun grisDeTono(tono: Double): Color {
    // La inversa de L*: cubica por encima de 8 y lineal por debajo (CIE 1976).
    val luminancia = if (tono > 8) ((tono + 16) / 116).pow(3) else tono / 903.3
    val canal = if (luminancia <= 0.0031308) 12.92 * luminancia else 1.055 * luminancia.pow(1 / 2.4) - 0.055
    return Color(canal.toFloat(), canal.toFloat(), canal.toFloat())
}

/**
 * Las superficies mas desfavorables del color dinamico.
 *
 * En claro, las superficies van del tono 87 (`surfaceDim`) al 100, y los colores
 * de dinero son oscuros: el peor fondo es el mas oscuro. En oscuro, del 4 al 24
 * (`surfaceBright`), y los colores son claros: el peor es el mas claro.
 */
private val PEOR_SUPERFICIE_DINAMICA_CLARA = grisDeTono(87.0)
private val PEOR_SUPERFICIE_DINAMICA_OSCURA = grisDeTono(24.0)

/** [arriba] con opacidad [alfa] pintado sobre [abajo], como hace un velo. */
private fun mezcla(
    arriba: Color,
    abajo: Color,
    alfa: Float,
): Color =
    Color(
        red = arriba.red * alfa + abajo.red * (1 - alfa),
        green = arriba.green * alfa + abajo.green * (1 - alfa),
        blue = arriba.blue * alfa + abajo.blue * (1 - alfa),
    )

/**
 * El texto de una barra de cristal sobre lo peor que la app puede pintar por
 * detras de ella.
 *
 * - Cualquiera de sus colores solidos: acento, contenedores, celdas y colores
 *   de dinero. Estos ultimos son texto, pero se tratan como solidos porque la
 *   cifra principal es gruesa: es el caso mas exigente.
 * - Su texto, que el desenfoque mezcla con el fondo: como mucho la mitad de
 *   tinta en el radio de difuminado.
 *
 * No se prueba contra blanco puro: la app no lo pinta en superficies grandes,
 * y exigirlo pedia un velo del 70% con el que la barra no dejaba ver nada.
 *
 * Solo el texto principal: es el unico que puede ir sobre el cristal (ver
 * OPACIDAD_DEL_VELO). El secundario y el acento van sobre superficies opacas.
 */
private fun textoSobreCristal(
    esquema: ColorScheme,
    dinero: ColoresDeDinero,
): List<Par> {
    val detras =
        mapOf(
            "primary" to esquema.primary,
            "primaryContainer" to esquema.primaryContainer,
            "secondaryContainer" to esquema.secondaryContainer,
            "surfaceContainerHighest" to esquema.surfaceContainerHighest,
            "dinero.ingreso" to dinero.ingreso,
            "dinero.gasto" to dinero.gasto,
            "dinero.sobregiro" to dinero.sobregiro,
            "dinero.ahorro" to dinero.ahorro,
            "texto desenfocado" to mezcla(esquema.onSurface, esquema.background, TINTA_DEL_TEXTO_DESENFOCADO),
        )
    return detras.map { (nombre, color) ->
        Par(
            "onSurface sobre cristal con $nombre detras",
            esquema.onSurface,
            mezcla(esquema.surfaceContainer, color, OPACIDAD_DEL_VELO),
        )
    }
}

/** Cuanto del color de un texto queda despues de desenfocarlo: como mucho la mitad. */
private const val TINTA_DEL_TEXTO_DESENFOCADO = 0.5f

private data class Par(
    val nombre: String,
    val texto: Color,
    val fondo: Color,
) {
    val relacion get() = contraste(texto, fondo)
}

/** Cada texto de la app sobre cada fondo en el que puede aparecer. */
private fun paresDe(
    esquema: ColorScheme,
    dinero: ColoresDeDinero,
    peorSuperficieDinamica: Color,
): List<Par> =
    sobreSuperficies(esquema, dinero, peorSuperficieDinamica) + sobreSuColor(esquema) +
        textoSobreCristal(esquema, dinero)

private fun sobreSuperficies(
    esquema: ColorScheme,
    dinero: ColoresDeDinero,
    peorSuperficieDinamica: Color,
): List<Par> {
    val fondos =
        mapOf(
            "surface" to esquema.surface,
            "background" to esquema.background,
            // El fondo de las Card.
            "surfaceContainerHighest" to esquema.surfaceContainerHighest,
            "surfaceContainer" to esquema.surfaceContainer,
            "color dinamico" to peorSuperficieDinamica,
        )
    val textos =
        mapOf(
            "onSurface" to esquema.onSurface,
            "onSurfaceVariant" to esquema.onSurfaceVariant,
            "primary" to esquema.primary,
            "error" to esquema.error,
            "dinero.ingreso" to dinero.ingreso,
            "dinero.gasto" to dinero.gasto,
            "dinero.sobregiro" to dinero.sobregiro,
            "dinero.ahorro" to dinero.ahorro,
        )
    return textos.flatMap { (texto, color) ->
        fondos
            // El color dinamico cambia fondo y texto a la vez, salvo los colores
            // de dinero, que no participan (ver ColoresDeDinero).
            .filterKeys { it != "color dinamico" || texto.startsWith("dinero.") }
            .map { (fondo, colorDeFondo) -> Par("$texto sobre $fondo", color, colorDeFondo) }
    }
}

private fun sobreSuColor(esquema: ColorScheme): List<Par> =
    listOf(
        Par("onPrimary sobre primary", esquema.onPrimary, esquema.primary),
        Par("onPrimaryContainer sobre primaryContainer", esquema.onPrimaryContainer, esquema.primaryContainer),
        Par("onSecondary sobre secondary", esquema.onSecondary, esquema.secondary),
        Par(
            "onSecondaryContainer sobre secondaryContainer",
            esquema.onSecondaryContainer,
            esquema.secondaryContainer,
        ),
        Par("onError sobre error", esquema.onError, esquema.error),
        Par("onErrorContainer sobre errorContainer", esquema.onErrorContainer, esquema.errorContainer),
        Par("onBackground sobre background", esquema.onBackground, esquema.background),
    )

/**
 * Contraste suficiente en los dos temas (docs/07, 7.1).
 *
 * Se comprueba con numeros y no a ojo: el tema oscuro se ve bien en la pantalla
 * de quien lo diseña y mal al sol, y un cambio de paleta que baja un par de
 * pares por debajo del minimo no se nota en una captura. Aqui si.
 */
class ContrasteTest {
    @Test
    fun `todo el texto del tema claro se lee`() {
        paresDe(EsquemaClaro, ColoresDeDineroClaros, PEOR_SUPERFICIE_DINAMICA_CLARA)
            .filter { it.relacion < CONTRASTE_MINIMO }
            .map { "${it.nombre}: %.2f".format(it.relacion) }
            .shouldBeEmpty()
    }

    @Test
    fun `todo el texto del tema oscuro se lee`() {
        paresDe(EsquemaOscuro, ColoresDeDineroOscuros, PEOR_SUPERFICIE_DINAMICA_OSCURA)
            .filter { it.relacion < CONTRASTE_MINIMO }
            .map { "${it.nombre}: %.2f".format(it.relacion) }
            .shouldBeEmpty()
    }

    /** Sin esto, un error en la formula podria dar todo por bueno. */
    @Test
    fun `la formula da los valores de referencia de WCAG`() {
        contraste(Color.Black, Color.White) shouldBeGreaterThan 20.99
        contraste(Color.White, Color.White) shouldBeLessThan 1.01
        // #767676 sobre blanco es el gris mas claro que pasa AA: 4.54.
        contraste(Color(0xFF767676), Color.White) shouldBeGreaterThan 4.5
        contraste(Color(0xFF777777), Color.White) shouldBeLessThan 4.5
    }

    /** Y sin esto, una conversion de tono mal hecha podria probar fondos que no existen. */
    @Test
    fun `los tonos extremos son negro y blanco`() {
        contraste(grisDeTono(0.0), Color.Black) shouldBeLessThan 1.01
        contraste(grisDeTono(100.0), Color.White) shouldBeLessThan 1.01
    }
}
