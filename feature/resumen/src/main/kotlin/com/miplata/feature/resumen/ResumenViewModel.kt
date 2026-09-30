package com.miplata.feature.resumen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.Calendario
import com.miplata.core.domain.model.Ajustes
import com.miplata.core.domain.model.CierreDeMes
import com.miplata.core.domain.model.Cuenta
import com.miplata.core.domain.model.PeriodoMensual
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.ResumenMensual
import com.miplata.core.domain.model.Transaccion
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.CalcularPlanPorCuentasUseCase
import com.miplata.core.domain.usecase.CalcularResumenMensualUseCase
import com.miplata.core.domain.usecase.esHoraDeCerrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private data class DatosDeCuentas(
    val cuentas: List<Cuenta>,
    val movimientos: List<Transaccion>,
    val cierres: List<CierreDeMes>,
)

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
        cuentas: CuentaRepository,
        cierres: CierreRepository,
        private val calendario: Calendario,
        private val calcular: CalcularResumenMensualUseCase,
        private val calcularPorCuentas: CalcularPlanPorCuentasUseCase,
    ) : ViewModel() {
        /** Lo que hace falta para ver el mes por cuentas: todo el historial, no solo el mes. */
        private val datosDeCuentas =
            combine(cuentas.observarTodas(), transacciones.observarTodas(), cierres.observarTodos(), ::DatosDeCuentas)

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
                    datosDeCuentas,
                ) { plan, movimientos, deCuentas ->
                    estadoDe(periodo, configuracion, plan, movimientos).conCuentas(periodo, plan, deCuentas)
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

        /**
         * Anade la vista por cuentas. En un mes cerrado, lo real es lo que se
         * guardo al cerrar, no lo que se calcula hoy: un movimiento anotado
         * despues no reescribe como termino el mes.
         */
        private fun ResumenUiState.conCuentas(
            periodo: PeriodoMensual,
            plan: PlanMensual?,
            datos: DatosDeCuentas,
        ): ResumenUiState {
            val porCuentas =
                calcularPorCuentas(periodo, datos.cuentas, plan, datos.movimientos, calendario.hoy()) ?: return this
            val cierre = datos.cierres.firstOrNull { it.mes == periodo.mes }
            return copy(
                cerrado = cierre != null,
                ofrecerCierre = cierre == null && esHoraDeCerrar(periodo, calendario.hoy()),
                hayMesesCerrados = datos.cierres.isNotEmpty(),
                porCuenta =
                    porCuentas.cuentas.map { deCuenta ->
                        val guardado = cierre?.saldoDe(deCuenta.cuenta.id)
                        CuentaDelResumen(
                            nombre = deCuenta.cuenta.nombre,
                            esperado = guardado?.esperado ?: deCuenta.terminaCon,
                            actual = guardado?.real ?: deCuenta.saldoActual,
                        )
                    },
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
