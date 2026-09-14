package com.miplata.feature.cuentas

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeCuenta
import com.miplata.core.domain.usecase.CuentaConSaldo

/**
 * La cuenta que se esta editando, mientras se edita.
 *
 * No es una [Cuenta] a medias, y es a proposito: `Cuenta` exige nombre no vacio
 * (docs/03), asi que una cuenta nueva no puede existir como `Cuenta` hasta que
 * el usuario haya escrito algo. Con el modelo del dominio como estado del
 * formulario, el primer caracter borrado reventaria la pantalla.
 *
 * Aqui los campos son texto y valores sueltos; la [Cuenta] se construye al
 * guardar, cuando ya cumple sus reglas.
 */
data class EditorDeCuenta(
    /** Nulo mientras es una cuenta nueva que aun no se ha guardado. */
    val id: CuentaId? = null,
    val nombre: String = "",
    val tipo: TipoDeCuenta = TipoDeCuenta.EFECTIVO,
    val saldoInicial: Money = Money.ZERO,
    val incluirEnTotal: Boolean = true,
    val archivada: Boolean = false,
) {
    val esNueva: Boolean get() = id == null

    /** Sin nombre no hay cuenta que guardar; es la unica regla que impone el dominio. */
    val puedeGuardar: Boolean get() = nombre.isNotBlank()
}

/** Todo lo que la pantalla de cuentas necesita para dibujarse. */
data class CuentasUiState(
    val cuentas: List<CuentaConSaldo> = emptyList(),
    val total: Money = Money.ZERO,
    val moneda: Moneda = Moneda("USD"),
    /**
     * Cuantas cuentas quedaron fuera del total por estar en otra moneda.
     *
     * Si no es cero, el total no es todo lo que tiene el usuario y hay que
     * decirlo: un total que miente por omision es peor que no ensenar total.
     */
    val cuentasEnOtraMoneda: Int = 0,
    val cargando: Boolean = true,
    /** Nulo cuando no hay nada abierto. */
    val editor: EditorDeCuenta? = null,
) {
    val estaVacio: Boolean get() = cuentas.isEmpty()
}

sealed interface EventoDeCuentas {
    data object CrearCuenta : EventoDeCuentas

    data class EditarCuenta(
        val cuenta: Cuenta,
    ) : EventoDeCuentas

    data object CerrarEditor : EventoDeCuentas

    data class CambiarNombre(
        val nombre: String,
    ) : EventoDeCuentas

    data class CambiarTipo(
        val tipo: TipoDeCuenta,
    ) : EventoDeCuentas

    data class CambiarSaldoInicial(
        val saldo: Money,
    ) : EventoDeCuentas

    data class CambiarIncluirEnTotal(
        val incluir: Boolean,
    ) : EventoDeCuentas

    data class CambiarArchivada(
        val archivada: Boolean,
    ) : EventoDeCuentas

    data object Guardar : EventoDeCuentas

    data object Eliminar : EventoDeCuentas
}
