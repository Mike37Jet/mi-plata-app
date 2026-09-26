package com.miplata.app.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.miplata.core.designsystem.accesibilidad.TextoQueCabe
import com.miplata.feature.backup.navigation.RutaCopia
import com.miplata.feature.backup.navigation.RutaRestaurar
import com.miplata.feature.backup.navigation.pantallaCopia
import com.miplata.feature.backup.navigation.pantallaRestaurar
import com.miplata.feature.bienvenida.navigation.RutaBienvenida
import com.miplata.feature.bienvenida.navigation.pantallaBienvenida
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
    abrirCopiaAlEmpezar: Boolean = false,
    empezarEnElPlan: Boolean = false,
    navController: NavHostController = rememberNavController(),
) {
    val entradaActual by navController.currentBackStackEntryAsState()
    val destinoActual = entradaActual?.destination

    // Al tocar el recordatorio: a Cuentas y, encima, la copia. Asi "atras" lleva
    // a Cuentas, que es de donde se llega a la copia normalmente, y no fuera de
    // la app.
    LaunchedEffect(abrirCopiaAlEmpezar) {
        if (abrirCopiaAlEmpezar) {
            navController.irA(DestinoPrincipal.CUENTAS)
            navController.navigate(RutaCopia)
        }
    }

    LaunchedEffect(empezarEnElPlan) {
        if (empezarEnElPlan) navController.irA(DestinoPrincipal.PLAN)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { BarraInferior(destinoActual, navController::irA) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RutaResumen,
            // `consumeWindowInsets` avisa a las pantallas de que la barra
            // inferior ya se desconto. Sin esto, una pantalla que se aparta del
            // teclado con `imePadding()` restaba el teclado ENTERO, sin saber que
            // parte de ese hueco ya lo ocupaba la barra: quedaba una franja en
            // blanco encima del teclado del alto de la barra.
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            pantallaResumen()
            pantallaPlan()
            pantallaTransacciones()
            pantallaCuentas(alAbrirCopiaDeSeguridad = { navController.navigate(RutaCopia) })
            pantallaCopia(
                alVolver = navController::popBackStack,
                alAbrirRestaurar = { navController.navigate(RutaRestaurar) },
            )
            pantallaRestaurar(alVolver = navController::popBackStack)
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
                // Cada pestaña tiene un cuarto del ancho y no se puede apilar. Con
                // letra grande, un Text normal partia "Movimientos" en dos lineas
                // a mitad de palabra; este se reduce lo justo para caber entero.
                label = { TextoQueCabe(stringResource(destino.etiqueta)) },
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

/**
 * La bienvenida, fuera del esqueleto de pestañas: sin barra inferior, porque
 * todavia no hay nada a lo que ir.
 *
 * Lleva su propio grafo para poder restaurar una copia desde aqui. Al terminar
 * no navega a ningun sitio: en cuanto existe una cuenta, la app cambia sola a
 * [NavegacionPrincipal] (ver `ArranqueViewModel`).
 */
@Composable
fun FlujoDeBienvenida(
    alTerminar: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // El mismo Scaffold que el de las pestañas, sin barras: aparta las pantallas
    // de la barra de estado y de la de navegacion. La de restaurar se escribio
    // contando con el y, sin el, quedaba debajo de la hora.
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RutaBienvenida,
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            pantallaBienvenida(
                alTenerCopia = { navController.navigate(RutaRestaurar) },
                alTerminar = alTerminar,
            )
            pantallaRestaurar(alVolver = navController::popBackStack)
        }
    }
}
