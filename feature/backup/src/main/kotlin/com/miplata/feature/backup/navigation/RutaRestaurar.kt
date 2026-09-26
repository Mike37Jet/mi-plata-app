package com.miplata.feature.backup.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.backup.PantallaRestaurar
import kotlinx.serialization.Serializable

/** Ruta de la pantalla de restaurar una copia. */
@Serializable
data object RutaRestaurar

fun NavGraphBuilder.pantallaRestaurar(alVolver: () -> Unit) {
    composable<RutaRestaurar> {
        PantallaRestaurar(alVolver = alVolver)
    }
}
