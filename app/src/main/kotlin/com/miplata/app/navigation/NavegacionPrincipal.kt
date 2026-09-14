package com.miplata.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miplata.feature.cuentas.navigation.pantallaCuentas
import com.miplata.feature.plan.navigation.pantallaPlan
import com.miplata.feature.resumen.navigation.RutaResumen
import com.miplata.feature.resumen.navigation.pantallaResumen
import com.miplata.feature.transacciones.navigation.pantallaTransacciones

/**
 * El esqueleto de navegacion de la app.
 *
 * `:app` es el unico sitio que conoce a todos los features, y por eso es el
 * unico que puede ensamblarlos. Cada feature aporta su ruta y su pantalla sin
 * saber que existen los demas (docs/04).
 */
@Composable
fun NavegacionPrincipal(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val entradaActual by navController.currentBackStackEntryAsState()
    val destinoActual = entradaActual?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BarraInferior(destinoActual, navController::irA) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RutaResumen,
            modifier = Modifier.padding(innerPadding),
        ) {
            pantallaResumen()
            pantallaPlan()
            pantallaTransacciones()
            pantallaCuentas()
        }
    }
}

@Composable
private fun BarraInferior(
    destinoActual: NavDestination?,
    irA: (DestinoPrincipal) -> Unit,
) {
    NavigationBar {
        DestinoPrincipal.entries.forEach { destino ->
            val seleccionado = destinoActual.estaEn(destino)

            NavigationBarItem(
                selected = seleccionado,
                onClick = { if (!seleccionado) irA(destino) },
                icon = {
                    // El icono no lleva descripcion porque la etiqueta de al lado
                    // ya dice lo mismo: repetirlo haria que un lector de pantalla
                    // leyera cada pestana dos veces.
                    Icon(imageVector = destino.icono, contentDescription = null)
                },
                label = { Text(stringResource(destino.etiqueta)) },
            )
        }
    }
}

private fun NavDestination?.estaEn(destino: DestinoPrincipal): Boolean =
    this?.hierarchy?.any { it.hasRoute(destino.claseDeRuta) } == true

/**
 * Cambia de seccion sin apilar historial.
 *
 * `launchSingleTop` evita que tocar dos veces la misma pestana apile dos copias,
 * y `popUpTo(startDestination)` con `saveState` hace que volver a una seccion la
 * encuentre donde se dejo -el desplazamiento, el filtro- en vez de reiniciada.
 * Sin esto, el boton de atras acaba recorriendo cada pestana que se haya tocado.
 */
private fun NavHostController.irA(destino: DestinoPrincipal) {
    navigate(destino.ruta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
