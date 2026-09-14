package com.miplata.feature.resumen.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.resumen.PantallaResumen
import kotlinx.serialization.Serializable

/**
 * Ruta de la pantalla.
 *
 * Es un objeto `@Serializable`, no una cadena: renombrarla es un refactor del
 * IDE, y pasarle argumentos equivocados no compila. Con rutas de texto, las dos
 * cosas fallan en ejecucion y en el telefono del usuario.
 */
@Serializable
data object RutaResumen

/** El feature declara como se entra en el; `:app` solo lo ensambla. */
fun NavGraphBuilder.pantallaResumen() {
    composable<RutaResumen> {
        PantallaResumen()
    }
}
