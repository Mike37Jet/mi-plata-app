package com.miplata.core.domain.model

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
    }

    override fun toString(): String = codigo

    companion object {
        private const val LONGITUD_ISO_4217 = 3
    }
}
