package com.miplata.feature.resumen.cierre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.model.Moneda
import com.miplata.core.domain.repository.AjustesRepository
import com.miplata.core.domain.repository.CierreRepository
import com.miplata.core.domain.repository.CuentaRepository
import com.miplata.core.domain.repository.TransaccionRepository
import com.miplata.core.domain.usecase.MesCerrado
import com.miplata.core.domain.usecase.ResumirCierresUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MesesCerradosUiState(
    val moneda: Moneda = Moneda("USD"),
    val cargando: Boolean = true,
    val meses: List<MesCerrado> = emptyList(),
)

/** El historial de cierres: cada mes cerrado, en una linea (docs/adr/0007). */
@HiltViewModel
class MesesCerradosViewModel
    @Inject
    constructor(
        cierres: CierreRepository,
        cuentas: CuentaRepository,
        transacciones: TransaccionRepository,
        ajustes: AjustesRepository,
        resumir: ResumirCierresUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<MesesCerradosUiState> =
            combine(
                cierres.observarTodos(),
                cuentas.observarTodas(),
                transacciones.observarTodas(),
                ajustes.observar(),
            ) { cerrados, lista, movimientos, configuracion ->
                MesesCerradosUiState(
                    moneda = configuracion.moneda,
                    cargando = false,
                    meses = resumir(cerrados, lista, movimientos, configuracion.primerDiaDelMesFinanciero),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(CINCO_SEGUNDOS), MesesCerradosUiState())

        private companion object {
            const val CINCO_SEGUNDOS = 5_000L
        }
    }
