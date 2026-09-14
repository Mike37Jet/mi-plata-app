package com.miplata.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia de la app.
 *
 * Se usa la fuente del sistema a proposito: es la que el usuario ya reconoce,
 * carga instantanea, y respeta los ajustes de accesibilidad que tenga puestos.
 * Empaquetar una fuente propia anadiria peso al APK y un riesgo de que no
 * soporte algun caracter, a cambio de personalidad que esta app no necesita.
 *
 * **Todos los tamanos van en `sp` y ninguno en `dp`.** Eso es lo que hace que la
 * app siga siendo legible cuando el usuario sube el tamano de letra del sistema.
 * Es casi gratis hacerlo bien desde el principio y carisimo corregirlo con diez
 * pantallas escritas.
 */
internal val TipografiaDeLaApp = Typography()

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

    /** El numero grande: el disponible del mes. */
    val destacado =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /** Importes dentro de una lista. */
    val enLista =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )

    /** Cifras secundarias: lo planificado frente a lo real. */
    val secundario =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = CIFRAS_TABULARES,
        )
}
