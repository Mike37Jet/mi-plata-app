package com.miplata.feature.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.model.Tema
import com.miplata.core.domain.repository.AjustesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class AjustesUiState(
    val tema: Tema = Tema.SEGUN_EL_SISTEMA,
    /** El dia de la ultima copia, o `null` si no se ha hecho ninguna. */
    val ultimaCopia: LocalDate? = null,
)

sealed interface EventoDeAjustes {
    data class ElegirTema(
        val tema: Tema,
    ) : EventoDeAjustes
}

/**
 * Lo que el usuario puede elegir de la app.
 *
 * El tema se aplica en cuanto se guarda: `MainActivity` observa los ajustes y
 * vuelve a pintar la app entera, sin boton de "aplicar".
 */
@HiltViewModel
class AjustesViewModel
    @Inject
    constructor(
        private val ajustes: AjustesRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AjustesUiState> =
            ajustes
                .observar()
                .map { actuales ->
                    AjustesUiState(
                        tema = actuales.tema,
                        ultimaCopia =
                            actuales.ultimoBackupEnMillis?.let {
                                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
                            },
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ESPERA_AL_SALIR), AjustesUiState())

        fun alEvento(evento: EventoDeAjustes) {
            when (evento) {
                is EventoDeAjustes.ElegirTema ->
                    viewModelScope.launch { ajustes.guardar(ajustes.obtener().copy(tema = evento.tema)) }
            }
        }

        private companion object {
            const val ESPERA_AL_SALIR = 5_000L
        }
    }
