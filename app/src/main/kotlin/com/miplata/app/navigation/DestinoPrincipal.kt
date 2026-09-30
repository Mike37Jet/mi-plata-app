package com.miplata.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.miplata.app.R
import com.miplata.feature.cuentas.navigation.RutaCuentas
import com.miplata.feature.plan.navigation.RutaPlan
import com.miplata.feature.resumen.navigation.RutaResumen
import kotlin.reflect.KClass

/**
 * Las secciones de la barra inferior.
 *
 * Viven en `:app` y no en cada feature porque la barra es una decision del
 * conjunto: cuantas hay, en que orden, y cual es la primera. Un feature no
 * puede saber eso sin conocer a los demas, y conocerse entre ellos es
 * exactamente lo que docs/04 prohibe.
 *
 * **Movimientos no es una pestaña** desde el presupuesto por cuentas (ADR
 * 0007): anotar es opcional, y lo que se usa cada mes es planear y cerrar. Se
 * llega desde el Resumen, como a los ajustes.
 *
 * **El orden es el del uso, no el de la construccion.** Resumen primero porque
 * es la pregunta que la app responde; Plan segundo porque es lo que se edita de
 * vez en cuando; Cuentas despues.
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
    CUENTAS(
        RutaCuentas,
        RutaCuentas::class,
        R.string.destino_cuentas,
        Icons.Outlined.AccountBalanceWallet,
    ),
}
