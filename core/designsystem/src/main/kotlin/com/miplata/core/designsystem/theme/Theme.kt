package com.miplata.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Colores con significado financiero.
 *
 * Material no los trae porque no son un concepto suyo, y esta app los necesita
 * en todas partes: una cifra tiene que decir de un vistazo si suma o resta.
 *
 * **No cambian con el color dinamico**, y es a proposito: son semanticos. Si el
 * fondo del sistema fuera morado y el "ingreso" se volviera morado con el, la
 * cifra dejaria de significar nada.
 */
@Immutable
data class ColoresDeDinero(
    val ingreso: Color,
    /** Un gasto normal no es rojo: gastar es lo que se hace con el dinero. */
    val gasto: Color,
    /** El rojo, reservado para cuando de verdad importa. */
    val sobregiro: Color,
    val ahorro: Color,
)

private val ColoresDeDineroClaros =
    ColoresDeDinero(
        ingreso = IngresoClaro,
        gasto = GastoClaro,
        sobregiro = SobregiroClaro,
        ahorro = AhorroClaro,
    )

private val ColoresDeDineroOscuros =
    ColoresDeDinero(
        ingreso = IngresoOscuro,
        gasto = GastoOscuro,
        sobregiro = SobregiroOscuro,
        ahorro = AhorroOscuro,
    )

private val LocalColoresDeDinero =
    staticCompositionLocalOf { ColoresDeDineroClaros }

/**
 * El tema de la app.
 *
 * @param temaOscuro por defecto sigue al sistema. La pantalla de ajustes lo
 *   fuerza a claro u oscuro cuando el usuario lo elige.
 * @param colorDinamico usa la paleta del fondo de pantalla en Android 12+. Se
 *   deja activo porque hace que la app se sienta parte del telefono; los colores
 *   de dinero no participan, por lo que se explica en [ColoresDeDinero].
 */
@Composable
fun MiPlataTheme(
    temaOscuro: Boolean = isSystemInDarkTheme(),
    colorDinamico: Boolean = true,
    content: @Composable () -> Unit,
) {
    val esquema = esquemaDeColor(temaOscuro, colorDinamico)
    val coloresDeDinero =
        if (temaOscuro) ColoresDeDineroOscuros else ColoresDeDineroClaros

    CompositionLocalProvider(LocalColoresDeDinero provides coloresDeDinero) {
        MaterialTheme(
            colorScheme = esquema,
            typography = TipografiaDeLaApp,
            content = content,
        )
    }
}

@Composable
private fun esquemaDeColor(
    temaOscuro: Boolean,
    colorDinamico: Boolean,
): ColorScheme {
    val hayColorDinamico = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        colorDinamico && hayColorDinamico -> {
            val context = LocalContext.current
            if (temaOscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        temaOscuro -> EsquemaOscuro
        else -> EsquemaClaro
    }
}

/** Acceso a los extras del tema: `MiPlataTheme.dinero.ingreso`. */
object MiPlataTheme {
    val dinero: ColoresDeDinero
        @Composable
        @ReadOnlyComposable
        get() = LocalColoresDeDinero.current
}
