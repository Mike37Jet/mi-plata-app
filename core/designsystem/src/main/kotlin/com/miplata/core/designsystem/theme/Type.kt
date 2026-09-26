package com.miplata.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.miplata.core.designsystem.R

/**
 * Inter, en los tres pesos que usa la app.
 *
 * Es la tipografia libre mas cercana a SF Pro, que no se puede usar: su
 * licencia la limita a plataformas de Apple (docs/10). Va empaquetada y no
 * descargada con Google Fonts porque la app no tiene permiso de internet ni
 * depende de nada de fuera, y porque asi no hay un primer arranque sin ella.
 *
 * Tres pesos y no mas: el mas grueso es SemiBold. Un `Bold` sin su archivo lo
 * sintetizaria Android engordando el SemiBold, y se nota.
 *
 * Sigue respetando el tamaño de letra del sistema: eso depende de los `sp`, no
 * de la familia.
 */
internal val FuenteDeLaApp: FontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

private fun estilo(
    tamano: Int,
    interlineado: Int,
    peso: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = FuenteDeLaApp,
    fontWeight = peso,
    fontSize = tamano.sp,
    lineHeight = interlineado.sp,
)

/**
 * Tipografia de la app, en una escala aurea.
 *
 * Cada tamaño es el anterior por √φ (1.272): 13 · 17 · 21 · 27 · 34 · 44 · 55.
 * Dos pasos seguidos dan φ exacto: 13 → 21 → 34 → 55, que son los tamaños que
 * marcan jerarquia (secundario, seccion, titulo grande, cifra principal). Los
 * intermedios son para lo que va entre medias. Sale casi igual que la escala de
 * iOS (13, 17, 22, 28, 34), que es la que se buscaba.
 *
 * **Todos los tamaños van en `sp`.** Asi la app sigue siendo legible cuando el
 * usuario sube la letra del sistema (docs/09).
 */
internal val TipografiaDeLaApp =
    Typography(
        displayLarge = estilo(55, 64, FontWeight.SemiBold),
        displayMedium = estilo(44, 52, FontWeight.SemiBold),
        displaySmall = estilo(34, 41, FontWeight.SemiBold),
        // El titulo grande de cada pantalla, como el de iOS.
        headlineLarge = estilo(34, 41, FontWeight.SemiBold),
        headlineMedium = estilo(27, 34, FontWeight.SemiBold),
        headlineSmall = estilo(21, 26, FontWeight.SemiBold),
        titleLarge = estilo(21, 26, FontWeight.SemiBold),
        titleMedium = estilo(17, 22, FontWeight.SemiBold),
        titleSmall = estilo(15, 20, FontWeight.SemiBold),
        bodyLarge = estilo(17, 22),
        bodyMedium = estilo(15, 20),
        bodySmall = estilo(13, 18),
        labelLarge = estilo(17, 22, FontWeight.Medium),
        labelMedium = estilo(13, 18, FontWeight.Medium),
        labelSmall = estilo(11, 13, FontWeight.Medium),
    )

/**
 * Estilos para las cifras.
 *
 * `tnum` activa las **cifras tabulares**: todos los digitos ocupan lo mismo, asi
 * que una columna de importes queda alineada y un saldo que cambia de 999 a 1000
 * no descoloca lo que tiene al lado. Sin esto, cualquier lista de movimientos
 * baila al desplazarse.
 */
object EstilosDeDinero {
    private const val CIFRAS_TABULARES = "tnum"

    /** El numero grande: el disponible del mes. El tope de la escala aurea. */
    val destacado =
        TextStyle(
            fontFamily = FuenteDeLaApp,
            fontWeight = FontWeight.SemiBold,
            fontSize = 55.sp,
            lineHeight = 64.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /**
     * El importe que se esta escribiendo en un editor: un paso de φ por debajo
     * de la cifra principal. Es un campo, no un titular, y tiene que caber junto
     * al cursor y la etiqueta.
     */
    val entrada =
        TextStyle(
            fontFamily = FuenteDeLaApp,
            fontWeight = FontWeight.Medium,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /** Importes dentro de una lista. */
    val enLista =
        TextStyle(
            fontFamily = FuenteDeLaApp,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /** Cifras secundarias: lo planificado frente a lo real. */
    val secundario =
        TextStyle(
            fontFamily = FuenteDeLaApp,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )
}
