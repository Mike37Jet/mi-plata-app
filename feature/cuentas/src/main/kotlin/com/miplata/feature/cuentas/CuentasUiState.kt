package com.miplata.feature.cuentas

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.Reparto
import com.miplata.core.domain.model.RolDeCuenta
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
    val rol: PapelDeLaCuenta = PapelDeLaCuenta.APARTE,
    /** Si el reparto del sobre es un porcentaje; si no, un monto fijo. */
    val repartoEnPorcentaje: Boolean = true,
    /** Nulo mientras el campo esta vacio o no es un numero. */
    val porcentaje: Int? = PORCENTAJE_POR_DEFECTO,
    val montoFijo: Money = Money.ZERO,
    val intocable: Boolean = false,
) {
    val esNueva: Boolean get() = id == null

    /**
     * Sin nombre no hay cuenta que guardar. Un sobre, ademas, necesita un
     * reparto valido: de 1 a 100 por ciento, o un monto mayor que cero.
     */
    val puedeGuardar: Boolean get() = nombre.isNotBlank() && (rol != PapelDeLaCuenta.SOBRE || reparto() != null)

    /** El reparto tal como esta escrito, o nulo si aun no es valido. */
    fun reparto(): Reparto? =
        if (repartoEnPorcentaje) {
            porcentaje?.takeIf { it in 1..PORCENTAJE_MAXIMO }?.let { Reparto.Porcentaje(it) }
        } else {
            montoFijo.takeIf { it.esPositivo }?.let { Reparto.Monto(it) }
        }

    /** El rol del dominio. Solo se llama con [puedeGuardar]. */
    fun rolDeCuenta(): RolDeCuenta =
        when (rol) {
            PapelDeLaCuenta.APARTE -> RolDeCuenta.Independiente
            PapelDeLaCuenta.PRINCIPAL -> RolDeCuenta.Principal
            PapelDeLaCuenta.SOBRE -> RolDeCuenta.Sobre(checkNotNull(reparto()), intocable)
        }

    companion object {
        const val PORCENTAJE_POR_DEFECTO = 10
        private const val PORCENTAJE_MAXIMO = 100
    }
}

/**
 * El rol de la cuenta tal como se elige en el editor.
 *
 * Existe aparte de `RolDeCuenta` porque el formulario tiene que poder decir
 * "sobre" antes de que su reparto sea valido.
 */
enum class PapelDeLaCuenta {
    APARTE,
    PRINCIPAL,
    SOBRE,
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
    /**
     * Cambiar un campo del editor. Un subtipo aparte para que el ViewModel los
     * trate todos de golpe sin un `else` que se trague eventos nuevos.
     */
    sealed interface CambioEnEditor : EventoDeCuentas

    data object CrearCuenta : EventoDeCuentas

    data class EditarCuenta(
        val cuenta: Cuenta,
    ) : EventoDeCuentas

    data object CerrarEditor : EventoDeCuentas

    data class CambiarNombre(
        val nombre: String,
    ) : CambioEnEditor

    data class CambiarTipo(
        val tipo: TipoDeCuenta,
    ) : CambioEnEditor

    data class CambiarSaldoInicial(
        val saldo: Money,
    ) : CambioEnEditor

    data class CambiarIncluirEnTotal(
        val incluir: Boolean,
    ) : CambioEnEditor

    data class CambiarArchivada(
        val archivada: Boolean,
    ) : CambioEnEditor

    data class CambiarRol(
        val rol: PapelDeLaCuenta,
    ) : CambioEnEditor

    data class CambiarModoDeReparto(
        val enPorcentaje: Boolean,
    ) : CambioEnEditor

    data class CambiarPorcentaje(
        val porcentaje: Int?,
    ) : CambioEnEditor

    data class CambiarMontoFijo(
        val monto: Money,
    ) : CambioEnEditor

    data class CambiarIntocable(
        val intocable: Boolean,
    ) : CambioEnEditor

    data object Guardar : EventoDeCuentas

    data object Eliminar : EventoDeCuentas
}
