package com.miplata.core.designsystem.componentes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.TipoDeCuenta

/**
 * El icono de una cuenta, el mismo en todas las pantallas.
 *
 * Una cuenta intocable lleva el candado en vez de su tipo: es lo que mas
 * importa saber de ella, y en el mismo sitio que el icono de las demas queda
 * alineado, no suelto en medio de la fila.
 */
fun Cuenta.icono(): ImageVector = if (esIntocable) Icons.Outlined.Lock else tipo.icono()

fun TipoDeCuenta.icono(): ImageVector =
    when (this) {
        TipoDeCuenta.EFECTIVO -> Icons.Outlined.Payments
        TipoDeCuenta.BANCARIA -> Icons.Outlined.AccountBalance
        TipoDeCuenta.TARJETA_CREDITO -> Icons.Outlined.CreditCard
        TipoDeCuenta.AHORRO -> Icons.Outlined.Savings
        TipoDeCuenta.INVERSION -> Icons.AutoMirrored.Outlined.ShowChart
    }
