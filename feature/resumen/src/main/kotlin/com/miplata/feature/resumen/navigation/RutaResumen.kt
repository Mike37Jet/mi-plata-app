package com.miplata.feature.resumen.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miplata.core.domain.model.Mes
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

/**
 * El feature declara como se entra en el; `:app` solo lo ensambla.
 *
 * Una salida por cada pantalla a la que se llega desde aqui; a donde va cada
 * una lo decide `:app` (docs/04). Agruparlas en una clase solo para acortar la
 * lista moveria el problema de sitio.
 */
@Suppress("LongParameterList")
fun NavGraphBuilder.pantallaResumen(
    alAbrirAjustes: () -> Unit = {},
    alAbrirCierre: (Mes) -> Unit = {},
    alAbrirMovimientos: (Mes) -> Unit = {},
    alAbrirMesesCerrados: () -> Unit = {},
    alAbrirFueraDelPlan: (Mes) -> Unit = {},
    alAbrirPlan: () -> Unit = {},
) {
    composable<RutaResumen> {
        PantallaResumen(
            alAbrirAjustes = alAbrirAjustes,
            alAbrirCierre = alAbrirCierre,
            alAbrirMovimientos = alAbrirMovimientos,
            alAbrirMesesCerrados = alAbrirMesesCerrados,
            alAbrirFueraDelPlan = alAbrirFueraDelPlan,
            alAbrirPlan = alAbrirPlan,
        )
    }
}
