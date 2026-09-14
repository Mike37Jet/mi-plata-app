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
private val Arena90 = Color(0xFFEFE3D6)
private val Arena10 = Color(0xFF241A10)

private val Rojo40 = Color(0xFFB3261E)
private val Rojo80 = Color(0xFFF2B8B5)
private val Rojo90 = Color(0xFFF9DEDC)
private val Rojo10 = Color(0xFF410E0B)

private val Gris10 = Color(0xFF1A1C1B)
private val Gris20 = Color(0xFF2F3130)
private val Gris90 = Color(0xFFE1E3E1)
private val Gris99 = Color(0xFFFBFDFA)

internal val EsquemaClaro =
    lightColorScheme(
        primary = VerdeAzulado40,
        onPrimary = Color.White,
        primaryContainer = VerdeAzulado90,
        onPrimaryContainer = VerdeAzulado10,
        secondary = Arena40,
        onSecondary = Color.White,
        secondaryContainer = Arena90,
        onSecondaryContainer = Arena10,
        error = Rojo40,
        onError = Color.White,
        errorContainer = Rojo90,
        onErrorContainer = Rojo10,
        background = Gris99,
        onBackground = Gris10,
        surface = Gris99,
        onSurface = Gris10,
    )

internal val EsquemaOscuro =
    darkColorScheme(
        primary = VerdeAzulado80,
        onPrimary = VerdeAzulado10,
        primaryContainer = VerdeAzulado40,
        onPrimaryContainer = VerdeAzulado90,
        secondary = Arena80,
        onSecondary = Arena10,
        secondaryContainer = Arena40,
        onSecondaryContainer = Arena90,
        error = Rojo80,
        onError = Rojo10,
        errorContainer = Rojo40,
        onErrorContainer = Rojo90,
        background = Gris10,
        onBackground = Gris90,
        surface = Gris10,
        onSurface = Gris90,
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

internal val FondoClaro = Gris99
internal val FondoOscuro = Gris10
internal val SuperficieElevadaClara = Gris90
internal val SuperficieElevadaOscura = Gris20
