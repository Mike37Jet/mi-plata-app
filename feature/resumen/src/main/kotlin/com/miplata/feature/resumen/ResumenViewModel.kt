package com.miplata.feature.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.ResumenMensual
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * El estado de la pantalla del resumen.
 *
 * Aqui es donde [CalcularResumenMensualUseCase] -probado desde la Etapa 1 con
 * todos sus casos borde- se encuentra por fin con datos reales. El ViewModel no
 * calcula nada: junta las piezas, llama al dominio y traduce el resultado a lo
 * que la pantalla sabe dibujar.
 */
@HiltViewModel
class ResumenViewModel
    @Inject
    constructor(
        planes: PlanRepository,
        transacciones: TransaccionRepository,
        ajustes: AjustesRepository,
        private val calendario: Calendario,
        private val calcular: CalcularResumenMensualUseCase,
    ) : ViewModel() {
        private val mesSeleccionado = MutableStateFlow(calendario.mesActual())

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ResumenUiState> =
            combine(mesSeleccionado, ajustes.observar()) { mes, configuracion ->
                // El periodo sale de los ajustes: quien cobra el 25 tiene su mes
                // del 25 al 24, y el resumen tiene que respetarlo.
                configuracion.periodoDe(mes) to configuracion
            }.flatMapLatest { (periodo, configuracion) ->
                combine(
                    planes.observarDe(periodo.mes),
                    transacciones.observarDelPeriodo(periodo),
                ) { plan, movimientos ->
                    estadoDe(periodo, configuracion, plan, movimientos)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = ResumenUiState(mes = mesSeleccionado.value),
            )

        fun alEvento(evento: EventoDelResumen) {
            mesSeleccionado.value =
                when (evento) {
                    EventoDelResumen.MesAnterior -> mesSeleccionado.value.anterior()
                    EventoDelResumen.MesSiguiente -> mesSeleccionado.value.siguiente()
                }
        }

        private fun estadoDe(
            periodo: PeriodoMensual,
            configuracion: Ajustes,
            plan: PlanMensual?,
            movimientos: List<Transaccion>,
        ): ResumenUiState {
            val resumen = calcular(periodo, plan, movimientos)

            return ResumenUiState(
                mes = periodo.mes,
                moneda = configuracion.moneda,
                cargando = false,
                hayPlan = plan != null,
                ingresosPlanificados = resumen.ingresosPlanificados,
                ingresosReales = resumen.ingresosReales,
                gastosPlanificados = resumen.gastosPlanificados,
                gastosReales = resumen.gastosReales,
                disponiblePlanificado = resumen.disponiblePlanificado,
                disponibleReal = resumen.disponibleReal,
                enSobregiro = resumen.enSobregiro,
                sobregiroPorIngresosQueNoLlegaron = resumen.sobregiroPorIngresosQueNoLlegaron,
                progresoDelMes = periodo.progreso(calendario.hoy()),
                progresoDelGasto = progresoDelGasto(resumen),
                desviaciones = resumen.desviacionesDesfavorables,
            )
        }

        /** Nulo si no hay nada planificado: no se puede llevar un tanto por ciento de cero. */
        private fun progresoDelGasto(resumen: ResumenMensual): Double? =
            resumen.gastosReales
                .porcentajeDe(resumen.gastosPlanificados)
                ?.div(PORCENTAJE)

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
            const val PORCENTAJE = 100.0
        }
    }
