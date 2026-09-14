package com.miplata.feature.transacciones

import com.miplata.core.domain.model.Categoria
import com.miplata.core.domain.model.CategoriaId
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeTransaccion
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.model.TransaccionId
import kotlinx.datetime.LocalDate

/**
 * Un movimiento tal y como se ensena en la lista.
 *
 * Trae ya resueltos los nombres de la cuenta y la categoria. La pantalla no
 * puede consultar repositorios -no los conoce- y hacerlo desde el composable
 * dispararia una consulta por fila segun se desplaza la lista.
 */
data class MovimientoEnLista(
    val transaccion: Transaccion,
    val cuenta: String,
    /** Solo en una transferencia: a donde fue el dinero. */
    val cuentaDestino: String?,
    val categoria: String?,
    val lineaDePlan: String?,
) {
    val id: TransaccionId get() = transaccion.id
    val monto: Money get() = transaccion.monto
    val tipo: TipoDeTransaccion get() = transaccion.tipo
    val nota: String? get() = transaccion.nota
}

/** Los movimientos de un dia, ya listos para dibujar, y lo que sumaron. */
data class DiaEnLista(
    val fecha: LocalDate,
    val movimientos: List<MovimientoEnLista>,
    val neto: Money,
)

/**
 * El movimiento que se esta anotando, mientras se anota.
 *
 * No es una [Transaccion] a medias, igual que el editor de cuentas no es una
 * `Cuenta` a medias: `Transaccion` exige monto positivo y, si es transferencia,
 * dos cuentas distintas. Un formulario recien abierto no cumple nada de eso, y
 * con el modelo del dominio como estado la pantalla reventaria al primer
 * caracter.
 */
data class EditorDeMovimiento(
    /** Nulo mientras es un movimiento nuevo que aun no se ha guardado. */
    val id: TransaccionId? = null,
    val monto: Money = Money.ZERO,
    val tipo: TipoDeTransaccion = TipoDeTransaccion.GASTO,
    val fecha: LocalDate,
    val cuentaOrigenId: CuentaId? = null,
    val cuentaDestinoId: CuentaId? = null,
    val categoriaId: CategoriaId? = null,
    val lineaDePlanId: LineaId? = null,
    val nota: String = "",
) {
    val esNuevo: Boolean get() = id == null

    val esTransferencia: Boolean get() = tipo == TipoDeTransaccion.TRANSFERENCIA

    /**
     * Si se puede guardar ya.
     *
     * Son las mismas reglas que impone [Transaccion], comprobadas antes de
     * construirla: asi el boton se ve apagado en lugar de fallar al pulsarlo.
     */
    val puedeGuardar: Boolean
        get() =
            monto.esPositivo &&
                cuentaOrigenId != null &&
                if (esTransferencia) {
                    cuentaDestinoId != null && cuentaDestinoId != cuentaOrigenId
                } else {
                    true
                }
}

/** Todo lo que la pantalla de movimientos necesita para dibujarse. */
data class TransaccionesUiState(
    val mes: Mes,
    val moneda: Moneda = Moneda("USD"),
    val cargando: Boolean = true,
    val dias: List<DiaEnLista> = emptyList(),
    val ingresos: Money = Money.ZERO,
    val gastos: Money = Money.ZERO,
    /** Para elegir cuenta en el editor, y para saber si se puede anotar algo. */
    val cuentas: List<Cuenta> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    /** Las lineas del plan de este mes, para enganchar el gasto con lo previsto. */
    val lineasDelPlan: List<LineaDePlan> = emptyList(),
    val editor: EditorDeMovimiento? = null,
) {
    val estaVacio: Boolean get() = dias.isEmpty()

    /**
     * Sin cuentas no se puede anotar nada: toda transaccion sale de alguna.
     *
     * La pantalla lo dice y manda a Cuentas, en vez de ofrecer un boton que
     * abre un formulario que no se puede guardar.
     */
    val faltanCuentas: Boolean get() = cuentas.isEmpty()
}

sealed interface EventoDeMovimientos {
    data object MesAnterior : EventoDeMovimientos

    data object MesSiguiente : EventoDeMovimientos

    data object AnotarMovimiento : EventoDeMovimientos

    data class EditarMovimiento(
        val transaccion: Transaccion,
    ) : EventoDeMovimientos

    data object CerrarEditor : EventoDeMovimientos

    data object Guardar : EventoDeMovimientos

    data object Eliminar : EventoDeMovimientos

    /**
     * Cambiar un campo del formulario.
     *
     * Son un subtipo aparte para que el ViewModel pueda tratarlos todos de
     * golpe -"aplica el cambio al editor abierto"- sin un `else` que se tragaria
     * en silencio cualquier evento nuevo que se anadiera despues.
     */
    sealed interface CambioDeCampo : EventoDeMovimientos {
        data class Monto(
            val monto: Money,
        ) : CambioDeCampo

        data class Tipo(
            val tipo: TipoDeTransaccion,
        ) : CambioDeCampo

        data class Fecha(
            val fecha: LocalDate,
        ) : CambioDeCampo

        data class CuentaOrigen(
            val cuentaId: CuentaId,
        ) : CambioDeCampo

        data class CuentaDestino(
            val cuentaId: CuentaId,
        ) : CambioDeCampo

        data class Categoria(
            val categoriaId: CategoriaId?,
        ) : CambioDeCampo

        data class DeLineaDePlan(
            val lineaId: LineaId?,
        ) : CambioDeCampo

        data class Nota(
            val nota: String,
        ) : CambioDeCampo
    }
}
