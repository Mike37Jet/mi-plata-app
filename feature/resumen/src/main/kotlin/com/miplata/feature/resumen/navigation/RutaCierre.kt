package com.miplata.feature.resumen.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.core.domain.model.Mes
import com.miplata.feature.resumen.cierre.PantallaCierre
import kotlinx.serialization.Serializable

/**
 * El cierre de un mes. No esta en la barra inferior: se entra desde el resumen.
 *
 * El mes viaja como texto ISO (`2026-03`), que es como lo escribe y lo lee `Mes`.
 */
@Serializable
data class RutaCierre(
    val mes: String,
) {
    constructor(mes: Mes) : this(mes.toString())
}

fun NavGraphBuilder.pantallaCierre(alVolver: () -> Unit) {
    composable<RutaCierre> {
        PantallaCierre(alVolver = alVolver)
    }
}
