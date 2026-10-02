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
data class RutaTransacciones(
    /**
     * Si se entra desde una cuenta, solo sus movimientos: lo que entro y salio
     * de ella, transferencias incluidas. Nulo, todos.
     */
    val cuentaId: String? = null,
    /** Solo los gastos fuera del plan: imprevistos y "Sin detalle". */
    val fueraDelPlan: Boolean = false,
    /** El mes en que se abre, en ISO (`2026-09`): el que se miraba al entrar. Nulo, el actual. */
    val mes: String? = null,
)

/**
 * El feature declara como se entra en el; `:app` solo lo ensambla.
 *
 * Es una pantalla interna, no una pestaña: anotar es opcional desde el
 * presupuesto por cuentas (ADR 0007), y se llega desde el Resumen.
 *
 * @param alVolver lo decide `:app`, que conoce la pila (docs/04).
 */
fun NavGraphBuilder.pantallaTransacciones(alVolver: () -> Unit) {
    composable<RutaTransacciones> {
        PantallaTransacciones(alVolver = alVolver)
    }
}
