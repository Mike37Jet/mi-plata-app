package com.miplata.feature.transacciones.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.feature.transacciones.PantallaTransacciones
import kotlinx.serialization.Serializable

/**
 * Ruta de la pantalla.
 *
 * Es un objeto `@Serializable`, no una cadena: renombrarla es un refactor del
 * IDE, y pasarle argumentos equivocados no compila. Con rutas de texto, las dos
 * cosas fallan en ejecucion y en el telefono del usuario.
 */
@Serializable
data object RutaTransacciones

/** El feature declara como se entra en el; `:app` solo lo ensambla. */
fun NavGraphBuilder.pantallaTransacciones() {
    composable<RutaTransacciones> {
        PantallaTransacciones()
    }
}
