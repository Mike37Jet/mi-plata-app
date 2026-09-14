package com.miplata.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.GeneradorDeIds
import com.miplata.core.domain.RelojDelMes
import com.miplata.core.domain.model.LineaDePlan
import com.miplata.core.domain.model.Money
import com.miplata.core.domain.model.PlanMensual
import com.miplata.core.domain.model.TipoDeLinea
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.PlanRepository
import com.miplata.core.domain.usecase.AbrirPlanDelMesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** El plan que se esta viendo, y si ya existe en la base o es una propuesta. */
private data class PlanEnPantalla(
    val plan: PlanMensual,
    val esBorrador: Boolean,
)

/**
 * El estado de la pantalla del plan.
 *
 * Expone un solo `StateFlow` y recibe eventos; nunca al reves (docs/01). La
 * pantalla no conoce repositorios ni use cases: solo dibuja un estado y avisa de
 * lo que el usuario hizo.
 */
@HiltViewModel
class PlanViewModel
    @Inject
    constructor(
        private val planes: PlanRepository,
        private val abrirPlan: AbrirPlanDelMesUseCase,
        private val ids: GeneradorDeIds,
        ajustes: AjustesRepository,
        reloj: RelojDelMes,
    ) : ViewModel() {
        private val mesSeleccionado = MutableStateFlow(reloj.mesActual())

        /**
         * El plan del mes en pantalla.
         *
         * El borrador se calcula **una vez por mes**. Hacerlo dentro del flujo
         * observado lo recalcularia en cada emision, generando identificadores
         * nuevos cada vez y con la pantalla saltando bajo el dedo del usuario.
         *
         * En cuanto el plan existe en la base, `observarDe` emite el guardado y
         * el borrador deja de usarse.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val planEnPantalla: StateFlow<PlanEnPantalla?> =
            mesSeleccionado
                .flatMapLatest { mes ->
                    flow {
                        val abierto = abrirPlan(mes)
                        emitAll(
                            planes.observarDe(mes).map { guardado ->
                                PlanEnPantalla(
                                    plan = guardado ?: abierto.plan,
                                    esBorrador = guardado == null,
                                )
                            },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(CINCO_SEGUNDOS), null)

        val uiState: StateFlow<PlanUiState> =
            combine(
                mesSeleccionado,
                planEnPantalla,
                ajustes.observar(),
            ) { mes, enPantalla, configuracion ->
                if (enPantalla == null) {
                    PlanUiState(mes = mes, moneda = configuracion.moneda)
                } else {
                    val plan = enPantalla.plan
                    PlanUiState(
                        mes = mes,
                        moneda = configuracion.moneda,
                        secciones = seccionesDe(plan),
                        ingresos = plan.totalPlanificadoDe(TipoDeLinea.INGRESO),
                        salidas = salidasDe(plan),
                        cargando = false,
                        esBorrador = enPantalla.esBorrador,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                // Sigue vivo 5s tras perder el ultimo observador, asi que una
                // rotacion no obliga a releer la base.
                started = SharingStarted.WhileSubscribed(CINCO_SEGUNDOS),
                initialValue = PlanUiState(mes = mesSeleccionado.value),
            )

        fun alEvento(evento: EventoDelPlan) {
            when (evento) {
                EventoDelPlan.MesAnterior -> mesSeleccionado.value = mesSeleccionado.value.anterior()
                EventoDelPlan.MesSiguiente ->
                    mesSeleccionado.value = mesSeleccionado.value.siguiente()

                is EventoDelPlan.AnadirLinea ->
                    editarPlan { plan -> plan.copy(lineas = plan.lineas + lineaNueva(evento.tipo)) }

                is EventoDelPlan.CambiarNombre ->
                    editarLinea(evento.linea) { it.copy(nombre = evento.nombre) }

                is EventoDelPlan.CambiarMonto ->
                    editarLinea(evento.linea) { it.copy(montoPlanificado = evento.monto) }

                is EventoDelPlan.CambiarActiva ->
                    editarLinea(evento.linea) { it.copy(activa = evento.activa) }

                is EventoDelPlan.EliminarLinea ->
                    editarPlan { plan ->
                        plan.copy(lineas = plan.lineas.filterNot { it.id == evento.linea.id })
                    }
            }
        }

        private fun lineaNueva(tipo: TipoDeLinea) =
            LineaDePlan(
                id = ids.nuevaLineaId(),
                // Sin nombre: el usuario lo escribe. Poner "Nueva linea" obligaria
                // a borrarlo antes de escribir lo que de verdad quiere.
                nombre = "",
                tipo = tipo,
                montoPlanificado = Money.ZERO,
            )

        private fun editarLinea(
            linea: LineaDePlan,
            cambio: (LineaDePlan) -> LineaDePlan,
        ) = editarPlan { plan ->
            plan.copy(lineas = plan.lineas.map { if (it.id == linea.id) cambio(it) else it })
        }

        /**
         * Aplica un cambio al plan en pantalla y lo guarda.
         *
         * Cualquier edicion lo persiste, incluido el borrador copiado del mes
         * anterior: desde el primer cambio deja de ser una propuesta y pasa a ser
         * el plan del usuario.
         */
        private fun editarPlan(cambio: (PlanMensual) -> PlanMensual) {
            val actual = planEnPantalla.value?.plan ?: return
            viewModelScope.launch { planes.guardar(cambio(actual)) }
        }

        private fun seccionesDe(plan: PlanMensual): List<SeccionDelPlan> =
            TipoDeLinea.entries.map { tipo ->
                SeccionDelPlan(
                    tipo = tipo,
                    lineas = plan.lineas.filter { it.tipo == tipo },
                    total = plan.totalPlanificadoDe(tipo),
                )
            }

        private fun salidasDe(plan: PlanMensual): Money =
            TipoDeLinea.entries
                .filter { it.restaDelDisponible }
                .fold(Money.ZERO) { suma, tipo -> suma + plan.totalPlanificadoDe(tipo) }

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }
