package com.miplata.feature.resumen.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.core.domain.model.Mes
import com.miplata.feature.resumen.cierre.PantallaMesesCerrados
import kotlinx.serialization.Serializable

/** El historial de meses cerrados. Se entra desde el resumen. */
@Serializable
data object RutaMesesCerrados

fun NavGraphBuilder.pantallaMesesCerrados(
    alVolver: () -> Unit,
    alAbrirMes: (Mes) -> Unit,
) {
    composable<RutaMesesCerrados> {
        PantallaMesesCerrados(alVolver = alVolver, alAbrirMes = alAbrirMes)
    }
}
