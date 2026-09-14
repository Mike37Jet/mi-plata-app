package com.miplata.core.designsystem.formato

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.miplata.core.domain.model.Money
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Convierte lo que el usuario teclea en una cantidad.
 *
 * Usa los separadores **del idioma del telefono**, no una convencion fija. En
 * espanol `1.234,56` son mil doscientos treinta y cuatro con cincuenta y seis;
 * en ingles ese mismo texto no significa nada parecido. Adivinar por la forma
 * del texto -"si hay un punto y dos digitos detras, sera decimal"- funciona
 * hasta que alguien escribe `1.234` y se le convierte en un euro con veintitres.
 *
 * Devuelve `null` si el texto no es una cantidad. No lanza: que el usuario
 * escriba algo raro mientras teclea es lo normal, no un error del programa.
 */
class AnalizadorDeDinero(
    locale: Locale,
) {
    private val simbolos = DecimalFormatSymbols.getInstance(locale)

    fun parsear(texto: String): Money? {
        val limpio =
            texto
                .trim()
                .replace(simbolos.groupingSeparator.toString(), "")
                .replace(simbolos.decimalSeparator, '.')
                // Espacios de cualquier tipo, incluido el no separable que usan
                // algunos idiomas para agrupar millares.
                .filterNot { it.isWhitespace() }

        if (limpio.isEmpty() || !limpio.matches(CANTIDAD)) return null

        return runCatching { Money.deDecimal(BigDecimal(limpio)) }.getOrNull()
    }

    private companion object {
        /** Signo opcional, digitos, y como mucho un separador decimal. */
        val CANTIDAD = Regex("""^-?\d*\.?\d*$""")
    }
}

@Composable
fun recordarAnalizadorDeDinero(): AnalizadorDeDinero {
    val configuracion = LocalConfiguration.current
    return remember(configuracion) { AnalizadorDeDinero(configuracion.locales[0]) }
}
