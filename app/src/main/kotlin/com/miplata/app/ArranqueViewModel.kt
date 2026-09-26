package com.miplata.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miplata.core.domain.usecase.HayQueDarLaBienvenidaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Por donde empieza la app: la bienvenida o las pantallas de siempre.
 *
 * `null` mientras no se sabe. La splash screen se queda puesta hasta entonces:
 * enseñar las pantallas vacias un instante y saltar despues a la bienvenida, o
 * al reves, se ve como un parpadeo.
 */
@HiltViewModel
class ArranqueViewModel
    @Inject
    constructor(
        hayQueDarLaBienvenida: HayQueDarLaBienvenidaUseCase,
    ) : ViewModel() {
        val empezarPorLaBienvenida: StateFlow<Boolean?> =
            hayQueDarLaBienvenida().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
