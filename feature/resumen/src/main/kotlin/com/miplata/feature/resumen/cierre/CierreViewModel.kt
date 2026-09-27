package com.miplata.feature.resumen.cierre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.CierreDeMes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.CuentaId
import com.miplata.core.domain.model.Mes
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.CerrarMesUseCase
import com.miplata.core.domain.usecase.PlanPorCuentas
import com.miplata.core.domain.usecase.ReabrirMesUseCase
import com.miplata.core.domain.usecase.SaldosDelBanco
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Todo lo que viene de la base para cerrar un mes. */
private data class DatosDelCierre(
    val plan: PlanMensual,
    val cuentas: List<Cuenta>,
    val movimientos: List<Transaccion>,
    val ajustes: Ajustes,
    val cierres: List<CierreDeMes>,
)

/** Los datos y lo que el dominio calcula con ellos: se calcula una vez por cambio. */
private data class Calculo(
    val datos: DatosDelCierre,
    val periodo: PeriodoMensual,
    /** Nulo sin cuenta principal. */
    val porCuentas: PlanPorCuentas?,
)

/**
 * El cierre de un mes: escribir el saldo real de cada cuenta y cerrar, o ver
 * como quedo y reabrir (docs/adr/0007).
 *
 * El mes llega por la ruta. Lo que se escribe vive aqui y no en la base hasta
 * que se pulsa "Cerrar": un cierre a medias no es un cierre.
 */
@HiltViewModel
class CierreViewModel
    @Inject
    constructor(
        estadoGuardado: SavedStateHandle,
        planes: PlanRepository,
        abrirPlan: AbrirPlanDelMesUseCase,
        cuentas: CuentaRepository,
        transacciones: TransaccionRepository,
        ajustes: AjustesRepository,
        cierres: CierreRepository,
        private val calendario: Calendario,
        private val calcularPlan: CalcularPlanPorCuentasUseCase,
        private val cerrarMes: CerrarMesUseCase,
        private val reabrirMes: ReabrirMesUseCase,
    ) : ViewModel() {
        private val mes: Mes = Mes.de(checkNotNull(estadoGuardado.get<String>(ARGUMENTO_MES)))

        /** Lo escrito por el usuario, por cuenta. Lo que no esta aqui toma lo calculado. */
        private val escritos = MutableStateFlow<Map<CuentaId, Money?>>(emptyMap())
        private val coberturas = MutableStateFlow<Set<CuentaId>>(emptySet())
        private val trabajando = MutableStateFlow(false)

        /** El plan del mes, o el que tendria copiado del anterior si nunca se guardo. */
        private val plan =
            flow {
                val abierto = abrirPlan(mes).plan
                emitAll(planes.observarDe(mes).map { it ?: abierto })
            }

        private val calculo: StateFlow<Calculo?> =
            combine(
                plan,
                cuentas.observarTodas(),
                transacciones.observarTodas(),
                ajustes.observar(),
                cierres.observarTodos(),
                ::DatosDelCierre,
            ).map { datos ->
                val periodo = datos.ajustes.periodoDe(mes)
                Calculo(
                    datos,
                    periodo,
                    calcularPlan(periodo, datos.cuentas, datos.plan, datos.movimientos, calendario.hoy()),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(CINCO_SEGUNDOS), null)

        val uiState: StateFlow<CierreUiState> =
            combine(calculo.filterNotNull(), escritos, coberturas, trabajando, ::estadoDe)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(CINCO_SEGUNDOS), CierreUiState(mes))

        fun alEvento(evento: EventoDelCierre) {
            when (evento) {
                is EventoDelCierre.CambiarReal -> escritos.update { it + (evento.cuentaId to evento.real) }
                is EventoDelCierre.CambiarCobertura ->
                    coberturas.update { if (evento.cubre) it + evento.cuentaId else it - evento.cuentaId }
                EventoDelCierre.Cerrar -> cerrar()
                EventoDelCierre.Reabrir -> reabrir()
            }
        }

        private fun estadoDe(
            calculo: Calculo,
            escritos: Map<CuentaId, Money?>,
            coberturas: Set<CuentaId>,
            trabajando: Boolean,
        ): CierreUiState {
            val datos = calculo.datos
            val base =
                CierreUiState(mes = mes, moneda = datos.ajustes.moneda, cargando = false, trabajando = trabajando)
            val porCuentas = calculo.porCuentas ?: return base.copy(sinMetodo = true)
            val cierre = datos.cierres.firstOrNull { it.mes == mes }

            val filas =
                porCuentas.cuentas.mapNotNull { deCuenta ->
                    val id = deCuenta.cuenta.id
                    if (cierre != null) {
                        // Cerrado: lo que se guardo, que no cambia aunque cambie el plan.
                        val saldo = cierre.saldoDe(id) ?: return@mapNotNull null
                        FilaDeCierre(deCuenta.cuenta, deCuenta.empiezaCon, saldo.esperado, saldo.real)
                    } else {
                        FilaDeCierre(
                            cuenta = deCuenta.cuenta,
                            empiezaCon = deCuenta.empiezaCon,
                            esperado = deCuenta.terminaCon,
                            real = if (escritos.containsKey(id)) escritos[id] else deCuenta.saldoActual,
                            cubre = id in coberturas,
                        )
                    }
                }

            return base.copy(
                cerrado = cierre != null,
                filas = filas,
                posterioresCerrados = datos.cierres.count { it.mes > mes },
            )
        }

        private fun cerrar() {
            val estado = uiState.value
            if (!estado.puedeCerrar) return
            val reales = estado.filas.associate { it.cuenta.id to checkNotNull(it.real) }
            val cubren =
                estado.filas
                    .filter { it.cubre && it.puedeCubrir }
                    .map { it.cuenta.id }
                    .toSet()
            val calculado = calculo.value ?: return
            val porCuentas = calculado.porCuentas ?: return
            trabajando.value = true
            viewModelScope.launch {
                try {
                    cerrarMes(calculado.periodo, porCuentas, SaldosDelBanco(reales, cubren))
                    escritos.value = emptyMap()
                    coberturas.value = emptySet()
                } finally {
                    trabajando.value = false
                }
            }
        }

        private fun reabrir() {
            trabajando.value = true
            viewModelScope.launch {
                try {
                    reabrirMes(mes)
                } finally {
                    trabajando.value = false
                }
            }
        }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }

/** Como se llama el mes en la ruta (`RutaCierre.mes`). */
internal const val ARGUMENTO_MES = "mes"
