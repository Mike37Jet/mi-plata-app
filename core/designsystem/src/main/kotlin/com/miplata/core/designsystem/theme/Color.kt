package com.miplata.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paleta de la app.
//
// El color base es un verde azulado. Se evita el verde puro de "dinero" porque
// en esta app el verde tiene un significado concreto -un ingreso, un mes que
// cuadra- y si toda la interfaz fuera verde ese significado se perderia.

private val VerdeAzulado40 = Color(0xFF00695C)
private val VerdeAzulado80 = Color(0xFF4DB6AC)
private val VerdeAzulado90 = Color(0xFFB2DFDB)
private val VerdeAzulado10 = Color(0xFF00251F)

private val Arena40 = Color(0xFF6B5E4F)
private val Arena80 = Color(0xFFD7C7B4)
private val Arena10 = Color(0xFF241A10)

private val Rojo40 = Color(0xFFB3261E)
private val Rojo80 = Color(0xFFF2B8B5)
private val Rojo90 = Color(0xFFF9DEDC)
private val Rojo10 = Color(0xFF410E0B)

// Neutros al estilo de iOS: el fondo agrupado gris claro con celdas blancas en
// claro, y negro con celdas grafito en oscuro. Son los que hacen que una lista
// agrupada se lea como tal sin necesitar sombras ni bordes.
//
// Los grises del texto secundario NO son los de Apple. El `secondaryLabel` de
// iOS (#8A8A8E) se queda en 3.08:1 sobre el fondo agrupado y no llega al 4.5:1
// de WCAG AA (docs/09). Estos son los mas claros que pasan en todos los fondos.
private val FondoAgrupadoClaro = Color(0xFFF2F2F7)
private val CeldaClara = Color(0xFFFFFFFF)
private val CeldaAltaClara = Color(0xFFE5E5EA)
private val SeparadorClaro = Color(0xFFC6C6C8)
private val TextoClaro = Color(0xFF1C1C1E)
private val TextoSecundarioClaro = Color(0xFF636366)

private val FondoAgrupadoOscuro = Color(0xFF000000)
private val SeleccionOscura = Color(0xFF1E3A36)
private val CeldaOscura = Color(0xFF1C1C1E)
private val CeldaAltaOscura = Color(0xFF2C2C2E)
private val SeparadorOscuro = Color(0xFF38383A)
private val TextoOscuro = Color(0xFFF2F2F7)
private val TextoSecundarioOscuro = Color(0xFF98989F)

internal val EsquemaClaro =
    lightColorScheme(
        primary = VerdeAzulado40,
        onPrimary = Color.White,
        primaryContainer = VerdeAzulado90,
        onPrimaryContainer = VerdeAzulado10,
        secondary = Arena40,
        onSecondary = Color.White,
        // La seleccion -la pestaña activa, un chip elegido- es un tinte del
        // acento, como en iOS. Antes era arena y la pestaña activa se veia marron.
        secondaryContainer = VerdeAzulado90,
        onSecondaryContainer = VerdeAzulado10,
        error = Rojo40,
        onError = Color.White,
        errorContainer = Rojo90,
        onErrorContainer = Rojo10,
        background = FondoAgrupadoClaro,
        onBackground = TextoClaro,
        surface = FondoAgrupadoClaro,
        onSurface = TextoClaro,
        surfaceVariant = CeldaAltaClara,
        onSurfaceVariant = TextoSecundarioClaro,
        surfaceContainerLowest = CeldaClara,
        surfaceContainerLow = CeldaClara,
        surfaceContainer = CeldaClara,
        surfaceContainerHigh = CeldaAltaClara,
        surfaceContainerHighest = CeldaAltaClara,
        outline = TextoSecundarioClaro,
        outlineVariant = SeparadorClaro,
    )

internal val EsquemaOscuro =
    darkColorScheme(
        primary = VerdeAzulado80,
        onPrimary = VerdeAzulado10,
        primaryContainer = VerdeAzulado40,
        onPrimaryContainer = VerdeAzulado90,
        secondary = Arena80,
        onSecondary = Arena10,
        secondaryContainer = SeleccionOscura,
        onSecondaryContainer = VerdeAzulado90,
        error = Rojo80,
        onError = Rojo10,
        errorContainer = Rojo40,
        onErrorContainer = Rojo90,
        background = FondoAgrupadoOscuro,
        onBackground = TextoOscuro,
        surface = FondoAgrupadoOscuro,
        onSurface = TextoOscuro,
        surfaceVariant = CeldaAltaOscura,
        onSurfaceVariant = TextoSecundarioOscuro,
        surfaceContainerLowest = CeldaOscura,
        surfaceContainerLow = CeldaOscura,
        surfaceContainer = CeldaOscura,
        surfaceContainerHigh = CeldaAltaOscura,
        surfaceContainerHighest = CeldaAltaOscura,
        outline = TextoSecundarioOscuro,
        outlineVariant = SeparadorOscuro,
    )

// Colores con significado financiero.
//
// Material no los trae porque no son un concepto suyo, y esta app los necesita
// en todas partes: una cifra tiene que decir de un vistazo si suma o resta.

internal val IngresoClaro = Color(0xFF1B5E20)
internal val IngresoOscuro = Color(0xFF81C784)

/**
 * Un gasto normal **no es rojo**, y es deliberado.
 *
 * Gastar es lo que se hace con el dinero: pintar de rojo cada compra convierte
 * la app en un reproche permanente y, peor, deja el rojo sin fuerza para cuando
 * de verdad importa. El rojo se reserva para el sobregiro.
 */
internal val GastoClaro = Color(0xFF37474F)
internal val GastoOscuro = Color(0xFFB0BEC5)

internal val SobregiroClaro = Color(0xFFB3261E)
internal val SobregiroOscuro = Color(0xFFFF8A80)

internal val AhorroClaro = Color(0xFF0D47A1)
internal val AhorroOscuro = Color(0xFF82B1FF)
