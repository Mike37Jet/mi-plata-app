package com.miplata.feature.bienvenida.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.bienvenida.PantallaBienvenida
import kotlinx.serialization.Serializable

@Serializable
data object RutaBienvenida

/**
 * La bienvenida, como el resto de features: declara su pantalla y `:app` la
 * ensambla.
 *
 * @param alTenerCopia a donde se va para restaurar lo decide `:app` (docs/04).
 * @param alTerminar se avisa antes de guardar; ver [PantallaBienvenida].
 */
fun NavGraphBuilder.pantallaBienvenida(
    alTenerCopia: () -> Unit,
    alTerminar: () -> Unit,
) {
    composable<RutaBienvenida> {
        PantallaBienvenida(alTenerCopia = alTenerCopia, alTerminar = alTerminar)
    }
}
