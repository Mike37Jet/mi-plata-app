package com.miplata.core.domain.model

enum class TipoDeCuenta {
    EFECTIVO,
    BANCARIA,
    TARJETA_CREDITO,
    AHORRO,
    INVERSION,
}

/**
 * Un sitio donde hay dinero: la cartera, la cuenta del banco, una tarjeta.
 *
 * **No tiene campo `saldo`, y es deliberado** (docs/03). El saldo se deriva
 * siempre de `saldoInicial` mas los movimientos. Un campo de saldo actualizado a
 * mano se desincroniza en cuanto una operacion falle a medias, y a partir de ahi
 * la app miente sin que nada lo detecte. Recalcular es barato con los datos de
 * una persona; tener dos fuentes de verdad para el dinero no sale barato nunca.
 */
data class Cuenta(
    val id: CuentaId,
    val nombre: String,
    val tipo: TipoDeCuenta,
    val saldoInicial: Money,
    val moneda: Moneda,
    /**
     * Si cuenta para el patrimonio total.
     *
     * Permite excluir, por ejemplo, una cuenta de inversion a largo plazo del
     * "cuanto tengo disponible" sin tener que borrarla ni archivarla.
     */
    val incluirEnTotal: Boolean = true,
    /**
     * Una cuenta que ya no se usa pero cuyo historial hay que conservar.
     *
     * Archivar no es borrar: las transacciones pasadas siguen ahi y los
     * resumenes de meses anteriores siguen cuadrando.
     */
    val archivada: Boolean = false,
) {
    init {
        require(nombre.isNotBlank()) { "Una cuenta necesita un nombre" }
    }
}
