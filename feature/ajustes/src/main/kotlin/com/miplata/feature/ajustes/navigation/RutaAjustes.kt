package com.miplata.feature.ajustes.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.ajustes.PantallaAjustes
import kotlinx.serialization.Serializable

@Serializable
data object RutaAjustes

fun NavGraphBuilder.pantallaAjustes(
    alVolver: () -> Unit,
    alAbrirCopia: () -> Unit,
) {
    composable<RutaAjustes> {
        PantallaAjustes(alVolver = alVolver, alAbrirCopia = alAbrirCopia)
    }
}
