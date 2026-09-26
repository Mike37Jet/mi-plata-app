package com.miplata.feature.backup.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.backup.PantallaCopia
import kotlinx.serialization.Serializable

/** Ruta de la pantalla de copia de seguridad. No esta en la barra inferior. */
@Serializable
data object RutaCopia

/**
 * El feature declara como se entra en el; `:app` solo lo ensambla.
 *
 * @param alVolver lo decide `:app`, que es quien conoce la pila de navegacion.
 */
fun NavGraphBuilder.pantallaCopia(
    alVolver: () -> Unit,
    alAbrirRestaurar: () -> Unit,
) {
    composable<RutaCopia> {
        PantallaCopia(alVolver = alVolver, alAbrirRestaurar = alAbrirRestaurar)
    }
}
