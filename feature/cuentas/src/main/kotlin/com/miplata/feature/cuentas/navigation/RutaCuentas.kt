package com.miplata.feature.cuentas.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.cuentas.PantallaCuentas
import kotlinx.serialization.Serializable

/**
 * Ruta de la pantalla.
 *
 * Es un objeto `@Serializable`, no una cadena: renombrarla es un refactor del
 * IDE, y pasarle argumentos equivocados no compila. Con rutas de texto, las dos
 * cosas fallan en ejecucion y en el telefono del usuario.
 */
@Serializable
data object RutaCuentas

/**
 * El feature declara como se entra en el; `:app` solo lo ensambla.
 *
 * @param alAbrirCopiaDeSeguridad a donde se va lo decide `:app`: este feature no
 *   puede depender del de la copia (docs/04).
 */
fun NavGraphBuilder.pantallaCuentas(alAbrirCopiaDeSeguridad: () -> Unit) {
    composable<RutaCuentas> {
        PantallaCuentas(alAbrirCopiaDeSeguridad = alAbrirCopiaDeSeguridad)
    }
}
