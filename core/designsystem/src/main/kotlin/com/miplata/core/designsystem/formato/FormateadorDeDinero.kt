package com.miplata.core.designsystem.formato

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency

/**
 * Convierte una cantidad en el texto que ve el usuario.
 *
 * Vive en presentacion y no en el dominio a proposito (docs/adr/0002): dar
 * formato al dinero necesita saber el idioma del telefono y la moneda, y ninguna
 * de las dos cosas tiene que ver con calcular un saldo. `Money.toString()` existe
 * para depurar, no para ensenar.
 */
class FormateadorDeDinero(
    private val formato: NumberFormat,
) {
    fun formatear(cantidad: Money): String = formato.format(BigDecimal(cantidad.centavos).movePointLeft(DECIMALES))

    /** Con signo explicito delante, para cuando importa distinguir entrada de salida. */
    fun formatearConSigno(cantidad: Money): String =
        if (cantidad.esPositivo) "+${formatear(cantidad)}" else formatear(cantidad)

    private companion object {
        const val DECIMALES = 2
    }
}

/**
 * Un formateador para la moneda y el idioma actuales.
 *
 * Se recuerda por configuracion: si el usuario cambia el idioma del sistema, la
 * app se recompone y el formato cambia con ella.
 */
@Composable
fun recordarFormateadorDeDinero(moneda: Moneda): FormateadorDeDinero {
    val configuracion = LocalConfiguration.current

    return remember(moneda, configuracion) {
        val locale = configuracion.locales[0]
        val formato =
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance(moneda.codigo)
            }
        FormateadorDeDinero(formato)
    }
}
