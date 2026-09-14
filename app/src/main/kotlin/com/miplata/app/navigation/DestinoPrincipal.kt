package com.miplata.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.miplata.app.R
import com.miplata.feature.cuentas.navigation.RutaCuentas
import com.miplata.feature.plan.navigation.RutaPlan
import com.miplata.feature.resumen.navigation.RutaResumen
import com.miplata.feature.transacciones.navigation.RutaTransacciones
import kotlin.reflect.KClass

/**
 * Las cuatro secciones de la barra inferior.
 *
 * Viven en `:app` y no en cada feature porque la barra es una decision del
 * conjunto: que haya cuatro, en que orden, y cual es la primera. Un feature no
 * puede saber eso sin conocer a los demas, y conocerse entre ellos es
 * exactamente lo que docs/04 prohibe.
 *
 * **El orden es el del uso, no el de la construccion.** Resumen primero porque
 * es la pregunta que la app responde; Plan segundo porque es lo que se edita de
 * vez en cuando; Movimientos y Cuentas despues.
 */
enum class DestinoPrincipal(
    val ruta: Any,
    val claseDeRuta: KClass<*>,
    // Destino explicito: sin el, Kotlin avisa de que en el futuro la anotacion
    // pasara a aplicarse tambien al campo, y ese aviso es un error en CI.
    @param:StringRes val etiqueta: Int,
    val icono: ImageVector,
) {
    RESUMEN(RutaResumen, RutaResumen::class, R.string.destino_resumen, Icons.Outlined.PieChart),
    PLAN(RutaPlan, RutaPlan::class, R.string.destino_plan, Icons.Outlined.EditCalendar),
    MOVIMIENTOS(
        RutaTransacciones,
        RutaTransacciones::class,
        R.string.destino_movimientos,
        Icons.AutoMirrored.Outlined.ReceiptLong,
    ),
    CUENTAS(
        RutaCuentas,
        RutaCuentas::class,
        R.string.destino_cuentas,
        Icons.Outlined.AccountBalanceWallet,
    ),
}
