package com.miplata.core.domain.model

import java.util.Currency

/**
 * Codigo de moneda ISO 4217: `USD`, `EUR`, `COP`.
 *
 * La v1 opera en una sola moneda y no convierte entre ellas, pero el campo
 * existe desde el primer dia porque anadirlo despues obligaria a migrar cada
 * cuenta y cada transaccion ya guardadas, adivinando a posteriori en que moneda
 * estaban (docs/03).
 */
@JvmInline
value class Moneda(
    val codigo: String,
) {
    init {
        require(codigo.length == LONGITUD_ISO_4217 && codigo.all { it.isLetter() && it.isUpperCase() }) {
            "Un codigo ISO 4217 son tres letras mayusculas, no '$codigo'"
        }
        // Comprobar solo la forma dejaba pasar 'ZZZ' o 'USE', que parecen
        // codigos pero no existen. Un erratazo al escribir la moneda de una
        // cuenta quedaria guardado para siempre sin que nada lo detectara.
        require(codigo in CODIGOS_EXISTENTES) {
            "'$codigo' tiene forma de codigo ISO 4217 pero no es ninguna moneda real"
        }
    }

    override fun toString(): String = codigo

    companion object {
        private const val LONGITUD_ISO_4217 = 3

        /** Monedas que el sistema reconoce. Se calcula una sola vez. */
        private val CODIGOS_EXISTENTES: Set<String> by lazy {
            Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode }
        }
    }
}
