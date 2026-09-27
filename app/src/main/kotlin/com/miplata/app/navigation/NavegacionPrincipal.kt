package com.miplata.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miplata.core.designsystem.accesibilidad.TextoQueCabe
import com.miplata.core.designsystem.componentes.LocalEspacioDeLaBarraInferior
import com.miplata.core.designsystem.componentes.cristal
import com.miplata.core.designsystem.componentes.fondoDesenfocable
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
import dev.chrisbanes.haze.HazeState

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

    // Lo que pasa por detras de la barra inferior, y se ve desenfocado a traves
    // de ella (docs/10).
    val fondoDesenfocable = remember { HazeState() }

    // La altura de la barra inferior, medida. Es el hueco que las pantallas
    // dejan al final de su lista para que lo ultimo pueda subir por encima.
    val densidad = LocalDensity.current
    var alturaDeLaBarra by remember { mutableStateOf(0.dp) }

    // El contenido ocupa toda la pantalla y la barra flota encima, en un Box:
    // tiene que pasar por DETRAS de ella, o no habria nada que desenfocar. La
    // altura de la barra se mide en vez de suponerla, porque cambia con la letra
    // del sistema y con la barra de navegacion de cada movil.
    Box(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalEspacioDeLaBarraInferior provides alturaDeLaBarra) {
            NavHost(
                navController = navController,
                startDestination = RutaResumen,
                // `consumeWindowInsets` avisa a las pantallas de que las barras del
                // sistema ya estan descontadas: la de estado con el padding de
                // aqui, y la de navegacion dentro del hueco de la barra inferior.
                // Sin esto, una pantalla que se aparta del teclado con
                // `imePadding()` restaria el teclado ENTERO y quedaria una franja
                // en blanco encima de el.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .fondoDesenfocable(fondoDesenfocable)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .consumeWindowInsets(WindowInsets.navigationBars),
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

        BarraInferior(
            destinoActual = destinoActual,
            irA = navController::irA,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { alturaDeLaBarra = with(densidad) { it.height.toDp() } }
                    .cristal(fondoDesenfocable),
        )
    }
}

@Composable
private fun BarraInferior(
    destinoActual: NavDestination?,
    irA: (DestinoPrincipal) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Transparente: el color lo pone el cristal, que es velo mas desenfoque.
    NavigationBar(modifier = modifier, containerColor = Color.Transparent, tonalElevation = 0.dp) {
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
