package com.miplata.feature.plan

import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.LineaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.usecase.PlanPorCuentas

/**
 * Un bloque de la pantalla: todas las lineas de un tipo, con su total.
 */
data class SeccionDelPlan(
    val tipo: TipoDeLinea,
    val lineas: List<LineaDePlan>,
    val total: Money,
)

/**
 * Todo lo que la pantalla necesita para dibujarse.
 *
 * Un solo tipo, siempre renderizable, con banderas en vez de estados
 * mutuamente excluyentes (docs/01): mientras carga ya se puede pintar el mes y
 * las secciones vacias, y eso evita el parpadeo de una pantalla en blanco cada
 * vez que se cambia de mes.
 */
data class PlanUiState(
    val mes: Mes,
    val moneda: Moneda = Moneda("USD"),
    val secciones: List<SeccionDelPlan> = emptyList(),
    val ingresos: Money = Money.ZERO,
    val salidas: Money = Money.ZERO,
    val cargando: Boolean = true,
    /**
     * El plan viene copiado del mes anterior y todavia no se ha guardado.
     *
     * La pantalla lo dice en vez de dejar creer que ya esta guardado: el usuario
     * tiene que poder distinguir "esto es tuyo" de "esto es una propuesta".
     */
    val esBorrador: Boolean = false,
    /** La linea que se esta creando o editando, o nulo si la hoja esta cerrada. */
    val editor: EditorDeLinea? = null,
    /**
     * La ultima linea eliminada, mientras se puede deshacer.
     *
     * La pantalla la anuncia con "Deshacer" y la olvida cuando el aviso se va.
     */
    val eliminada: LineaDePlan? = null,
    /**
     * El plan visto por cuentas, si hay cuenta principal (docs/adr/0007).
     *
     * Con el, la pantalla agrupa por cuenta en vez de por tipo y la cifra de
     * arriba pasa a ser lo que queda sin repartir.
     */
    val porCuentas: PlanPorCuentas? = null,
    /** Lo que falta transferir a los sobres este mes, para "Ya transferi el reparto". */
    val repartoPendiente: Money = Money.ZERO,
    /** Las cuentas vigentes, para elegir de cual sale una linea. */
    val cuentas: List<Cuenta> = emptyList(),
) {
    /**
     * La cifra de arriba: lo disponible del plan de siempre o, con el metodo,
     * lo que queda del ingreso despues de repartir y de pagar lo de la
     * principal.
     */
    val disponible: Money get() = porCuentas?.sinRepartir ?: (ingresos - salidas)

    val enSobregiro: Boolean get() = disponible.esNegativo

    val estaVacio: Boolean get() = secciones.all { it.lineas.isEmpty() }
}

/**
 * La linea que se esta editando en la hoja.
 *
 * No es una [LineaDePlan] a medias: mientras se escribe, lo que hay en la hoja
 * no es de nadie hasta que se pulsa Guardar. Asi una edicion a medias no toca
 * el plan, y cancelar es de verdad cancelar.
 */
data class EditorDeLinea(
    /** Nulo mientras es una linea nueva que aun no se ha guardado. */
    val id: LineaId? = null,
    val nombre: String = "",
    val tipo: TipoDeLinea = TipoDeLinea.GASTO_VARIABLE,
    val monto: Money = Money.ZERO,
    val activa: Boolean = true,
    /** De que cuenta sale; nulo es la principal, o ninguna sin el metodo. */
    val cuentaId: CuentaId? = null,
) {
    val esNueva: Boolean get() = id == null
}

/** Lo que el usuario puede hacer en la pantalla. */
sealed interface EventoDelPlan {
    data object MesAnterior : EventoDelPlan

    data object MesSiguiente : EventoDelPlan

    /** Lo que abre, cierra o guarda la hoja de una linea. */
    sealed interface EventoDeLaHoja : EventoDelPlan

    /** El "+": abre la hoja con una linea en blanco. */
    data object NuevaLinea : EventoDeLaHoja

    data class EditarLinea(
        val linea: LineaDePlan,
    ) : EventoDeLaHoja

    data object CerrarEditor : EventoDeLaHoja

    data object GuardarLinea : EventoDeLaHoja

    /** Desde la hoja, deslizando la fila o con la accion de un lector de pantalla. */
    data class EliminarLinea(
        val id: LineaId,
    ) : EventoDelPlan

    data object DeshacerEliminacion : EventoDelPlan

    /** El aviso de "Deshacer" se fue sin que se pulsara. */
    data object OlvidarEliminacion : EventoDelPlan

    /** "Ya transferi el reparto": anota las transferencias que faltan a los sobres. */
    data object RegistrarReparto : EventoDelPlan

    /**
     * Cambiar un campo de la hoja. Un subtipo aparte para que el ViewModel los
     * trate todos de golpe sin un `else` que se trague eventos nuevos.
     */
    sealed interface CambioEnEditor : EventoDeLaHoja {
        data class Nombre(
            val nombre: String,
        ) : CambioEnEditor

        data class Monto(
            val monto: Money,
        ) : CambioEnEditor

        data class Tipo(
            val tipo: TipoDeLinea,
        ) : CambioEnEditor

        data class Activa(
            val activa: Boolean,
        ) : CambioEnEditor

        data class Cuenta(
            val cuentaId: CuentaId?,
        ) : CambioEnEditor
    }
}
